package com.incoin.demo.service;

import com.incoin.demo.db.entity.Coupon;
import com.incoin.demo.db.entity.Subscription;
import com.incoin.demo.db.entity.UsingService;
import com.incoin.demo.db.repository.CouponRepository;
import com.incoin.demo.db.repository.SubscriptionRepository;
import com.incoin.demo.db.repository.UsingServiceRepository;
import com.incoin.demo.model.UserSession;
//import com.incoin.demo.service.IncoinApiService;
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

    private static final int CREDITS_PER_COUPON = 50;

    // ── Get current credits ───────────────────────────────────────────────────

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getCredits)
                .orElse(0);
    }

    // ── Algo1 — called ONCE at login time ────────────────────────────────────
    //
    // OPTIMAL BATCH approach — avoids N individual Incoin API calls:
    //
    //  (a) ONE DB query → get all uncredited orderIds for this user
    //  (b) Fast path → if none, return immediately (0 Incoin calls)
    //  (c) Page through Incoin order HISTORY (50/page) and match against a Set
    //      → stop paging as soon as all uncredited IDs are resolved
    //      → max 20 pages safety cap
    //  (d) ONE batch DB update → mark resolved orders as credited=y
    //  (e) ONE subscription update → deduct count from credits
    //
    // Total Incoin calls = ceil(uncredited / 50) in the best case,
    // bounded by 20 pages. Far better than 1 call per order.

    @Transactional
    public int runAlgo1AtLogin(String userId, UserSession session) {

        // (a) ONE query — get all uncredited entries
        List<UsingService> groupA = usingServiceRepo.findByUserIdAndCredited(userId, "n");

        // (b) Fast path
        if (groupA.isEmpty()) {
            log.debug("algo1[{}]: no uncredited orders — skipping Incoin calls", userId);
            return 0;
        }

        // Build lookup structures: Set for O(1) membership, Map for update
        Set<String>             remaining = new HashSet<>();
        Map<String, UsingService> entryMap = new HashMap<>();
        for (UsingService e : groupA) {
            remaining.add(e.getOrderId());
            entryMap.put(e.getOrderId(), e);
        }
        log.debug("algo1[{}]: {} uncredited orders to reconcile", userId, remaining.size());

        // (c) Page through Incoin order history
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

                if (orders == null || orders.isEmpty()) break; // no more pages

                for (Map<String, Object> order : orders) {
                    String oid = (String) order.get("orderId");
                    if (oid == null || !remaining.contains(oid)) continue;

                    // Remove from tracking regardless of status
                    remaining.remove(oid);

                    // status == 2 → paid/credited on Incoin side
                    int statusInt = order.get("status") instanceof Number
                            ? ((Number) order.get("status")).intValue() : -1;
                    if (statusInt == 2) {
                        creditedOnIncoin.add(oid);
                    }
                }

                // Check if more pages exist
                Object totalObj = data.get("total");
                int total = totalObj instanceof Number ? ((Number) totalObj).intValue() : 0;
                if (page * 50 >= total) break;

                page++;

            } catch (Exception ex) {
                log.warn("algo1[{}]: error on page {}: {}", userId, page, ex.getMessage());
                break;
            }
        }

        if (creditedOnIncoin.isEmpty()) {
            log.debug("algo1[{}]: 0 orders newly credited after {} page(s)", userId, page);
            return 0;
        }

        // (d) Batch mark credited=y
        for (String oid : creditedOnIncoin) {
            UsingService entry = entryMap.get(oid);
            entry.setCredited("y");
            usingServiceRepo.save(entry);
        }

        // (e) ONE subscription deduction
        int x = creditedOnIncoin.size();
        subscriptionRepo.findByUserId(userId).ifPresent(sub -> {
            sub.setCredits(Math.max(0, sub.getCredits() - x));
            subscriptionRepo.save(sub);
        });

        log.info("algo1[{}]: {} credited, {} deducted, {} pages scanned", userId, x, x, page);
        return x;
    }

    // ── Save grabbed order to using_service ──────────────────────────────────

    @Transactional
    public void saveGrabbedOrder(String userId, String orderId) {
        if (usingServiceRepo.existsByUserIdAndOrderId(userId, orderId)) return;
        UsingService e = new UsingService();
        e.setUserId(userId);
        e.setOrderId(orderId);
        e.setCredited("n");
        usingServiceRepo.save(e);
    }

    // ── Coupon redemption ─────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> redeemCoupon(String userId, String couponCode) {
        Coupon coupon = couponRepo.findByCouponCode(couponCode.trim().toUpperCase())
                .orElse(null);

        if (coupon == null || coupon.getUserId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Coupon is already claimed or does not exist.");
        }

        // Claim coupon
        coupon.setUserId(userId);
        couponRepo.save(coupon);

        // Add credits — create subscription row if first time
        Subscription sub = subscriptionRepo.findByUserId(userId).orElse(null);
        int oldCredits = 0;

        if (sub == null) {
            sub = new Subscription();
            sub.setUserId(userId);
            sub.setCredits(CREDITS_PER_COUPON);
        } else {
            oldCredits = sub.getCredits();
            sub.setCredits(oldCredits + CREDITS_PER_COUPON);
        }
        subscriptionRepo.save(sub);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("oldCredits",   oldCredits);
        result.put("addedCredits", CREDITS_PER_COUPON);
        result.put("totalCredits", sub.getCredits());
        return result;
    }
}