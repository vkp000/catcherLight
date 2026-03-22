package com.incoin.demo.controller;

import com.incoin.demo.dto.LoginRequest;
import com.incoin.demo.dto.LoginResponse;
import com.incoin.demo.model.CaptchaResult;
import com.incoin.demo.model.GrabState;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.security.JwtUtil;
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
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IncoinApiService incoinApi;
    private final SessionService   sessionService;
    private final WorkerService    workerService;
    private final JwtUtil          jwtUtil;

    // ── Step 1 ────────────────────────────────────────────────────────────────

    /**
     * GET /auth/captcha
     *
     * Calls Incoin to obtain a fresh captcha image.
     * Returns PNG bytes + X-Captcha-Token header.
     * The frontend must pass X-Captcha-Token back in the login request.
     *
     * No auth required.
     */
    @GetMapping(value = "/captcha", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getCaptcha() {
        Map<String, String> keys = incoinApi.checkVersion();
        CaptchaResult captcha    = incoinApi.getCaptcha(keys.get("clientKey"));

        // Store clientKey/clientSecret in Redis keyed by captchaToken (TTL: 5 min)
        sessionService.storePendingKeys(captcha.getCaptchaToken(), keys);

        return ResponseEntity.ok()
                .header("X-Captcha-Token", captcha.getCaptchaToken())
                // Expose the custom header to browser CORS
                .header("Access-Control-Expose-Headers", "X-Captcha-Token")
                .contentType(MediaType.IMAGE_PNG)
                .body(captcha.getImageBytes());
    }

    // ── Step 2 ────────────────────────────────────────────────────────────────

    /**
     * POST /auth/login
     *
     * Body: { username, password, captchaCode, captchaToken }
     *
     * 1. Retrieves clientKey/clientSecret from Redis using captchaToken.
     * 2. Calls Incoin login → receives incoinToken.
     * 3. Builds an isolated UserSession and persists it in Redis.
     * 4. Returns YOUR application JWT (never the Incoin token).
     *
     * No auth required.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        // Retrieve and consume the one-time pending keys
        Map<String, String> keys = sessionService.getPendingKeys(req.getCaptchaToken());
        if (keys == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Captcha session expired or invalid. Please refresh the captcha.");
        }

        // Authenticate against Incoin — throws 401 on failure
        String incoinToken = incoinApi.login(
                keys.get("clientKey"), keys.get("clientSecret"),
                req.getUsername(), req.getPassword(),
                req.getCaptchaCode(), req.getCaptchaToken()
        );

        // One session per username (concurrent login replaces the old session)
        String userId = "user-" + req.getUsername();

        UserSession session = UserSession.builder()
                .userId(userId)
                .incoinToken(incoinToken)                  // secret — stays in Redis only
                .clientKey(keys.get("clientKey"))
                .clientSecret(keys.get("clientSecret"))    // secret — stays in Redis only
                .grabState(new GrabState())
                .createdAt(Instant.now())
                .lastActiveAt(Instant.now())
                .build();

        sessionService.save(session);

        // Your JWT carries only userId — no sensitive data
        String jwt = jwtUtil.generate(userId);
        log.info("Login successful userId={}", userId);

        return ResponseEntity.ok(new LoginResponse(jwt, userId));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * POST /auth/logout
     *
     * Stops any running grab loop, notifies Incoin, deletes the session.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String userId) {
        workerService.stopGrab(userId);
        try {
            UserSession session = sessionService.getOrThrow(userId);
            incoinApi.logout(session);
        } catch (Exception ignored) {
            // Session may already be expired — that's fine
        }
        sessionService.delete(userId);
        log.info("Logout userId={}", userId);
        return ResponseEntity.ok().build();
    }
}