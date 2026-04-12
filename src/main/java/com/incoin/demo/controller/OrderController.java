package com.incoin.demo.controller;

import com.incoin.demo.dto.CancelRequest;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.OrderService;
import com.incoin.demo.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService   orderService;
    private final SessionService sessionService;

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> detail(
            @AuthenticationPrincipal String sessionId,
            @PathVariable String orderId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        return ResponseEntity.ok(orderService.getDetail(session, orderId));
    }

    @PostMapping("/{orderId}/paid")
    public ResponseEntity<Void> markPaid(
            @AuthenticationPrincipal String sessionId,
            @PathVariable String orderId
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        boolean ok = orderService.markPaid(session, orderId);
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Mark paid failed — check order status in Incoin.");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal String sessionId,
            @PathVariable String orderId,
            @Valid @RequestBody CancelRequest req
    ) {
        UserSession session = sessionService.getOrThrow(sessionId);
        boolean ok = orderService.cancel(session, orderId, req.getReason());
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Cancel failed — order may already be closed.");
        return ResponseEntity.ok().build();
    }
}