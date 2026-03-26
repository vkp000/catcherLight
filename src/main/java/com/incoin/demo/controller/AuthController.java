package com.incoin.demo.controller;

import com.incoin.demo.db.entity.AuthenticUser;
import com.incoin.demo.db.repository.AuthenticUserRepository;
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

    private final IncoinApiService         incoinApi;
    private final SessionService           sessionService;
    private final WorkerService            workerService;
    private final AuthenticUserRepository  authenticUserRepo;

    // ── Available apps ────────────────────────────────────────────────────────

    /**
     * GET /auth/apps
     * Returns the list of selectable apps. Frontend shows these in a dropdown.
     * No auth required.
     */
    @GetMapping("/apps")
    public ResponseEntity<List<Map<String, Object>>> getApps() {
        List<Map<String, Object>> apps = incoinApi.getAvailableApps();
        return ResponseEntity.ok(apps);
    }

    // ── Captcha ───────────────────────────────────────────────────────────────

    /**
     * GET /auth/captcha?appIndex=0
     *
     * appIndex selects the base URL from the apps list.
     * Returns PNG bytes + X-Captcha-Token header.
     * No auth required.
     */
    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCaptcha(@RequestParam int appIndex) {
        String baseUrl = incoinApi.getBaseUrlByIndex(appIndex);

        Map<String, String> keys = incoinApi.checkVersion(baseUrl);
        CaptchaResult captcha    = incoinApi.getCaptcha(keys.get("clientKey"), baseUrl);

        // Store clientKey, clientSecret AND baseUrl together in Redis
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
     * Body: { username, password, captchaCode, captchaToken }
     *
     * On success:
     *   - Creates Redis session (30 min TTL)
     *   - Saves username+password to authentic_users table
     *   - Returns { sessionId, userId } — NO JWT
     *
     * No auth required.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest req) {
        Map<String, String> keys = sessionService.getPendingKeys(req.getCaptchaToken());
        if (keys == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Captcha session expired or invalid. Please refresh the captcha.");
        }

        String baseUrl = keys.get("baseUrl");

        // Authenticate against Incoin
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

        // Store session in Redis — returns the sessionId (random UUID)
        String sessionId = sessionService.createSession(session);

        // Save username + password to DB (plain text as requested)
        AuthenticUser user = new AuthenticUser();
        user.setUsername(req.getUsername());
        user.setPassword(req.getPassword());
        authenticUserRepo.save(user); // upsert — PK is username

        log.info("Login OK userId={} baseUrl={}", userId, baseUrl);

        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "userId",    userId
        ));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * POST /auth/logout
     * Header: X-Session-Id: <sessionId>
     */
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