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

    // ── Get current credits ───────────────────────────────────────────────────

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getCredits)
                .orElse(0);
    }

    // ── Auto-register new user with free credits ──────────────────────────────

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

    // ── Algo1 — ONLY at login ─────────────────────────────────────────────────
    //
    // 1. using_service se saare rows lo jahan userId match kare aur credited = 'n'
    // 2. Har order ka status Incoin se fetch karo
    // 3. Jo orders status == 2 hain (confirmed) → credited = 'y' mark karo
    // 4. Count ke hisaab se subscription credits deduct karo

    @Transactional
    public int runAlgo1AtLogin(String userId, UserSession session) {

        // Step 1 — fetch all uncredited orders for this user
        List<UsingService> uncredited = usingServiceRepo.findByUserIdAndCredited(userId, "n");

        if (uncredited.isEmpty()) {
            log.debug("algo1[{}]: no uncredited orders, nothing to do", userId);
            return 0;
        }

        log.debug("algo1[{}]: {} uncredited orders found, checking with Incoin", userId, uncredited.size());

        // Step 2 & 3 — check each order status from Incoin
        List<UsingService> toCredit = new ArrayList<>();

        for (UsingService entry : uncredited) {
            try {
                Map<String, Object> detail = incoinApi.getOrderDetail(session, entry.getOrderId());
                Object statusObj = detail.get("status");
                int status = statusObj instanceof Number ? ((Number) statusObj).intValue() : -1;

                if (status == 2) {
                    // Incoin confirmed this order — mark it
                    entry.setCredited("y");
                    toCredit.add(entry);
                    log.debug("algo1[{}]: order {} confirmed credited", userId, entry.getOrderId());
                }
            } catch (Exception e) {
                log.warn("algo1[{}]: could not fetch order {}: {}", userId, entry.getOrderId(), e.getMessage());
            }
        }

        if (toCredit.isEmpty()) {
            log.debug("algo1[{}]: no orders newly confirmed by Incoin", userId);
            return 0;
        }

        // Step 3 — batch save credited = 'y'
        usingServiceRepo.saveAll(toCredit);

        // Step 4 — deduct credits
        int deductCount = toCredit.size();
        subscriptionRepo.findByUserId(userId).ifPresent(sub -> {
            int before = sub.getCredits();
            sub.setCredits(Math.max(0, before - deductCount));
            subscriptionRepo.save(sub);
            log.info("algo1[{}]: {} orders credited, credits {} -> {}",
                    userId, deductCount, before, sub.getCredits());
        });

        return deductCount;
    }

    // ── Save grabbed order ────────────────────────────────────────────────────

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