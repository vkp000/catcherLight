package com.incoin.demo.controller;

import com.incoin.demo.db.entity.AuthenticUser;
import com.incoin.demo.db.repository.AuthenticUserRepository;
import com.incoin.demo.db.service.SubscriptionService;
import com.incoin.demo.dto.LoginRequest;
import com.incoin.demo.model.CaptchaResult;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import com.incoin.demo.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IncoinApiService        incoinApi;
    private final SessionService          sessionService;
    private final WorkerService           workerService;
    private final AuthenticUserRepository authenticUserRepo;
    private final SubscriptionService     subscriptionService;

    // ── Available apps ────────────────────────────────────────────────────────

    @GetMapping("/apps")
    public ResponseEntity<List<Map<String, Object>>> getApps() {
        return ResponseEntity.ok(incoinApi.getAvailableApps());
    }

    // ── Captcha ───────────────────────────────────────────────────────────────

    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCaptcha(@RequestParam int appIndex) {
        String baseUrl = incoinApi.getBaseUrlByIndex(appIndex);

        Map<String, String> keys = incoinApi.checkVersion(baseUrl);
        CaptchaResult captcha    = incoinApi.getCaptcha(keys.get("clientKey"), baseUrl);

        Map<String, String> pending = new LinkedHashMap<>(keys);
        pending.put("baseUrl", baseUrl);
        sessionService.storePendingKeys(captcha.getCaptchaToken(), pending);

        return ResponseEntity.ok()
                .header("X-Captcha-Token", captcha.getCaptchaToken())
                .header("Access-Control-Expose-Headers", "X-Captcha-Token")
                .contentType(MediaType.IMAGE_PNG)
                .body(captcha.getImageBytes());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * POST /auth/login
     *
     * On success:
     *  1. Authenticates with Incoin
     *  2. Runs algo1 ONCE to reconcile credited orders
     *  3. Fetches remaining credits from DB
     *  4. Returns { sessionId, userId, credits }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, String> keys = sessionService.getPendingKeys(req.getCaptchaToken());
        if (keys == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Captcha session expired or invalid. Please refresh the captcha.");
        }

        String baseUrl = keys.get("baseUrl");

        // Authenticate with Incoin
        String incoinToken = incoinApi.login(
                keys.get("clientKey"), keys.get("clientSecret"),
                req.getUsername(), req.getPassword(),
                req.getCaptchaCode(), req.getCaptchaToken(),
                baseUrl
        );

        String userId = "user-" + req.getUsername();

        UserSession session = UserSession.builder()
                .userId(userId)
                .incoinToken(incoinToken)
                .clientKey(keys.get("clientKey"))
                .clientSecret(keys.get("clientSecret"))
                .incoinBaseUrl(baseUrl)
                .grabState(new GrabState())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();

        String sessionId = sessionService.createSession(session);

        // Save credentials to DB
        AuthenticUser user = new AuthenticUser();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        authenticUserRepo.save(user);

        // Run algo1 ONCE — reconcile credited orders, deduct from subscription
        // Session must be saved first so algo1 can use it for Incoin API calls
        int deducted = 0;
        try {
            deducted = subscriptionService.runAlgo1AtLogin(userId, session);
        } catch (Exception e) {
            log.warn("algo1 failed at login for userId={}: {}", userId, e.getMessage());
        }

        // Fetch final credits after algo1
        int credits = subscriptionService.getCredits(userId);

        log.info("Login OK userId={} credits={} algo1Deducted={}", userId, credits, deducted);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("userId",    userId);
        resp.put("credits",   credits);
        return ResponseEntity.ok(resp);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String sessionId) {
        workerService.stopGrab(sessionId);
        try {
            UserSession session = sessionService.getOrThrow(sessionId);
            incoinApi.logout(session);
        } catch (Exception ignored) {}
        sessionService.delete(sessionId);
        log.info("Logout sessionId={}", sessionId);
        return ResponseEntity.ok().build();
    }
}