package com.incoin.demo.controller;

import com.incoin.demo.db.entity.UsingService;
//import com.incoin.demo.db.service.CreditsService;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.CreditsService;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditsController {

    private final CreditsService creditsService;
    private final SessionService sessionService;
    private final IncoinApiService incoinApi;

    /**
     * GET /credits/me
     * Run algo1 then return current credits.
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyCredits(
        @AuthenticationPrincipal String userId
    ) {
        // Run algo1 first to reconcile
        int deducted = creditsService.runAlgo1(userId);
        int credits = creditsService.getCredits(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("credits", credits);
        result.put("deductedThisCheck", deducted);
        result.put("hasSubscription", credits > 0 || creditsService.getCredits(userId) >= 0);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /credits/redeem
     * Redeem a coupon code.
     * Body: { "couponCode": "ABC123" }
     */
    @PostMapping("/redeem")
    public ResponseEntity<Map<String, Object>> redeem(
        @AuthenticationPrincipal String userId,
        @RequestBody Map<String, String> body
    ) {
        String code = body.get("couponCode");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Coupon code is required"));
        }
        Map<String, Object> result = creditsService.redeemCoupon(userId, code);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /credits/history/ours?page=0
     * Orders grabbed through our service (from using_service table).
     */
    @GetMapping("/history/ours")
    public ResponseEntity<Map<String, Object>> ourHistory(
        @AuthenticationPrincipal String userId,
        @RequestParam(defaultValue = "0") int page
    ) {
        Page<UsingService> result = creditsService.getOurHistory(userId, page);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orders", result.getContent());
        resp.put("totalPages", result.getTotalPages());
        resp.put("currentPage", page);
        resp.put("hasMore", !result.isLast());
        return ResponseEntity.ok(resp);
    }

    /**
     * GET /credits/history/incoin?page=1
     * All order history from Incoin API (last 20, load more).
     */
    @GetMapping("/history/incoin")
    public ResponseEntity<Object> incoinHistory(
        @AuthenticationPrincipal String userId,
        @RequestParam(defaultValue = "1") int page
    ) {
        UserSession session = sessionService.getOrThrow(userId);
        Object history = incoinApi.getOrderHistory(session, page, 20);
        return ResponseEntity.ok(history);
    }
}
