package com.incoin.demo.controller;

import com.incoin.demo.db.entity.UsingService;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.CreditsService;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/credits")
@RequiredArgsConstructor
public class CreditsController {

    private final CreditsService   creditsService;
    private final SessionService   sessionService;
    private final IncoinApiService incoinApi;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyCredits(
            @AuthenticationPrincipal String sessionId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        String userId = session.getUserId();

        int deducted = creditsService.runAlgo1(userId);
        int credits  = creditsService.getCredits(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId",            userId);
        result.put("credits",           credits);
        result.put("deductedThisCheck", deducted);
        return ResponseEntity.ok(result);
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
            return ResponseEntity.badRequest().body(Map.of("message", "Coupon code is required"));
        }
        return ResponseEntity.ok(creditsService.redeemCoupon(userId, code));
    }

    @GetMapping("/history/ours")
    public ResponseEntity<Map<String, Object>> ourHistory(
            @AuthenticationPrincipal String sessionId,
            @RequestParam(defaultValue = "0") int page
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        String userId = session.getUserId();

        Page<UsingService> result = creditsService.getOurHistory(userId, page);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("orders",      result.getContent());
        resp.put("totalPages",  result.getTotalPages());
        resp.put("currentPage", page);
        resp.put("hasMore",     !result.isLast());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/history/incoin")
    public ResponseEntity<Object> incoinHistory(
            @AuthenticationPrincipal String sessionId,
            @RequestParam(defaultValue = "1") int page
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        return ResponseEntity.ok(incoinApi.getOrderHistory(session, page, 20));
    }
}