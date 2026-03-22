package com.incoin.demo.service;

import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Thin facade over IncoinApiService for order-specific operations.
 * Keeps controller code minimal.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final SessionService   sessionService;
    private final IncoinApiService incoinApi;

    public Map<String, Object> getDetail(String userId, String orderId) {
        UserSession session = sessionService.getOrThrow(userId);
        return incoinApi.getOrderDetail(session, orderId);
    }

    public boolean markPaid(String userId, String orderId) {
        UserSession session = sessionService.getOrThrow(userId);
        return incoinApi.markPaid(session, orderId);
    }

    public boolean cancel(String userId, String orderId, String reason) {
        UserSession session = sessionService.getOrThrow(userId);
        return incoinApi.cancelOrder(session, orderId, reason);
    }
}
