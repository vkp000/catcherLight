package com.incoin.demo.controller;

import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.SessionService;
import com.incoin.demo.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SessionService      sessionService;

    @GetMapping("/credits")
    public ResponseEntity<Map<String, Object>> getCredits(
            @AuthenticationPrincipal String sessionId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        String userId = session.getUserId();
        int credits = subscriptionService.getCredits(userId);
        return ResponseEntity.ok(Map.of("credits", credits, "userId", userId));
    }

    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @AuthenticationPrincipal String sessionId,
            @RequestBody Map<String, String> body
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        String userId = session.getUserId();
        String code = body.get("couponCode");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "couponCode is required"));
        }
        return ResponseEntity.ok(subscriptionService.redeemCoupon(userId, code));
    }
}