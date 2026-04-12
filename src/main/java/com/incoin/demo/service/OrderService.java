package com.incoin.demo.service;

import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final IncoinApiService incoinApi;

    public Map<String, Object> getDetail(UserSession session, String orderId) {
        return incoinApi.getOrderDetail(session, orderId);
    }

    public boolean markPaid(UserSession session, String orderId) {
        return incoinApi.markPaid(session, orderId);
    }

    public boolean cancel(UserSession session, String orderId, String reason) {
        return incoinApi.cancelOrder(session, orderId, reason);
    }
}