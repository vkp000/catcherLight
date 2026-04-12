package com.incoin.demo.service;

import com.incoin.demo.db.entity.Coupon;
import com.incoin.demo.db.entity.Subscription;
import com.incoin.demo.db.entity.UsingService;
import com.incoin.demo.db.repository.CouponRepository;
import com.incoin.demo.db.repository.SubscriptionRepository;
import com.incoin.demo.db.repository.UsingServiceRepository;
import com.incoin.demo.model.UserSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepo;
    private final UsingServiceRepository usingServiceRepo;
    private final CouponRepository       couponRepo;
    private final IncoinApiService       incoinApi;

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getCredits)
                .orElse(0);
    }

    @Transactional
    public void initUserIfAbsent(String userId, int defaultCredits) {
        if (subscriptionRepo.findByUserId(userId).isEmpty()) {
            Subscription sub = new Subscription();
            sub.setUserId(userId);
            sub.setCredits(defaultCredits);
            subscriptionRepo.save(sub);
            log.info("New subscription created userId={} credits={}", userId, defaultCredits);
        }
    }

    @Transactional
    public int runAlgo1AtLogin(String userId, UserSession session) {

        List<UsingService> groupA = usingServiceRepo.findByUserIdAndCredited(userId, "n");
        if (groupA.isEmpty()) {
            log.debug("algo1[{}]: no uncredited orders — skipping", userId);
            return 0;
        }

        Set<String>               remaining = new HashSet<>();
        Map<String, UsingService> entryMap  = new HashMap<>();
        for (UsingService e : groupA) {
            remaining.add(e.getOrderId());
            entryMap.put(e.getOrderId(), e);
        }

        Set<String> creditedOnIncoin = new HashSet<>();
        int page = 1;

        while (!remaining.isEmpty() && page <= 20) {
            try {
                Map<String, Object> resp = incoinApi.getOrderHistory(session, page, 50);

                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>)
                        resp.getOrDefault("data", Collections.emptyMap());

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> orders = (List<Map<String, Object>>)
                        data.getOrDefault("list", Collections.emptyList());

                if (orders == null || orders.isEmpty()) break;

                for (Map<String, Object> order : orders) {
                    String oid = (String) order.get("orderId");
                    if (oid == null || !remaining.contains(oid)) continue;
                    remaining.remove(oid);
                    int statusInt = order.get("status") instanceof Number
                            ? ((Number) order.get("status")).intValue() : -1;
                    if (statusInt == 2) creditedOnIncoin.add(oid);
                }

                Object totalObj = data.get("total");
                int total = totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
                if (page * 50 >= total) break;
                page++;

            } catch (Exception ex) {
                log.warn("algo1[{}]: error on page {}: {}", userId, page, ex.getMessage());
                break;
            }
        }

        if (creditedOnIncoin.isEmpty()) return 0;

        for (String oid : creditedOnIncoin) {
            UsingService entry = entryMap.get(oid);
            entry.setCredited("y");
            usingServiceRepo.save(entry);
        }

        int x = creditedOnIncoin.size();
        subscriptionRepo.findByUserId(userId).ifPresent(sub -> {
            sub.setCredits(Math.max(0, sub.getCredits() - x));
            subscriptionRepo.save(sub);
        });

        log.info("algo1[{}]: {} credited, {} deducted, {} pages scanned", userId, x, x, page);
        return x;
    }

    @Transactional
    public void saveGrabbedOrder(String userId, String orderId) {
        if (usingServiceRepo.existsByUserIdAndOrderId(userId, orderId)) return;
        UsingService e = new UsingService();
        e.setUserId(userId);
        e.setOrderId(orderId);
        e.setCredited("n");
        usingServiceRepo.save(e);
    }

    @Transactional
    public Map<String, Object> redeemCoupon(String userId, String couponCode) {
        Coupon coupon = couponRepo.findByCouponCode(couponCode.trim().toUpperCase())
                .orElse(null);

        if (coupon == null || coupon.getUserId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coupon is already claimed or does not exist.");
        }

        int creditsToAdd = coupon.getValue() != null ? coupon.getValue() : 0;

        coupon.setUserId(userId);
        couponRepo.save(coupon);

        Subscription sub = subscriptionRepo.findByUserId(userId).orElse(null);
        int oldCredits = 0;

        if (sub == null) {
            sub = new Subscription();
            sub.setUserId(userId);
            sub.setCredits(creditsToAdd);
        } else {
            oldCredits = sub.getCredits();
            sub.setCredits(oldCredits + creditsToAdd);
        }
        subscriptionRepo.save(sub);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldCredits",   oldCredits);
        result.put("addedCredits", creditsToAdd);
        result.put("totalCredits", sub.getCredits());
        return result;
    }
}