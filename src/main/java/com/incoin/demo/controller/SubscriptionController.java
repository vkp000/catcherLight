package com.incoin.demo.controller;

//import com.incoin.demo.db.service.SubscriptionService;
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

    /**
     * GET /subscription/credits
     * Returns current credits for the logged-in user.
     */
    @GetMapping("/credits")
    public ResponseEntity<Map<String, Object>> getCredits(
            @AuthenticationPrincipal String sessionId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        int credits = subscriptionService.getCredits(session.getUserId());
        return ResponseEntity.ok(Map.of("credits", credits, "userId", session.getUserId()));
    }

    /**
     * POST /subscription/redeem
     * Body: { "couponCode": "PROMO50" }
     * Redeems a coupon and adds 50 credits.
     */
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
            @AuthenticationPrincipal String sessionId,
            @RequestBody Map<String, String> body
    ) {
        UserSession session  = sessionService.getOrThrow(sessionId);
        String      code     = body.get("couponCode");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "couponCode is required"));
        }
        Map<String, Object> result = subscriptionService.redeemCoupon(session.getUserId(), code);
        return ResponseEntity.ok(result);
    }
}