package com.incoin.demo.controller;

import com.incoin.demo.dto.CancelRequest;
import com.incoin.demo.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Order-level actions on grabbed orders.
 * All endpoints require a valid JWT.
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * GET /orders/{orderId}
     * Full order detail: bank info, amount, UTR, status, etc.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> detail(
        @AuthenticationPrincipal String userId,
        @PathVariable String orderId
    ) {
        return ResponseEntity.ok(orderService.getDetail(userId, orderId));
    }

    /**
     * POST /orders/{orderId}/paid
     * Triggers Incoin's machine review (marks the order as paid).
     */
    @PostMapping("/{orderId}/paid")
    public ResponseEntity<Void> markPaid(
        @AuthenticationPrincipal String userId,
        @PathVariable String orderId
    ) {
        boolean ok = orderService.markPaid(userId, orderId);
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Mark paid failed — check order status in Incoin.");
        }
        return ResponseEntity.ok().build();
    }

    /**
     * POST /orders/{orderId}/cancel
     * Cancels an order with a reason string.
     *
     * Body: { "reason": "Payment Fail" }
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
        @AuthenticationPrincipal String userId,
        @PathVariable String orderId,
        @Valid @RequestBody CancelRequest req
    ) {
        boolean ok = orderService.cancel(userId, orderId, req.getReason());
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cancel failed — order may already be closed.");
        }
        return ResponseEntity.ok().build();
    }
}
