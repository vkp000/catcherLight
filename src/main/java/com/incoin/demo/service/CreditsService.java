package com.incoin.demo.service;

import com.incoin.demo.db.entity.Coupon;
import com.incoin.demo.db.entity.Subscription;
import com.incoin.demo.db.entity.UsingService;
import com.incoin.demo.db.repository.CouponRepository;
import com.incoin.demo.db.repository.SubscriptionRepository;
import com.incoin.demo.db.repository.UsingServiceRepository;
import com.incoin.demo.model.UserSession;
import com.incoin.demo.service.IncoinApiService;
import com.incoin.demo.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditsService {

    private final SubscriptionRepository subscriptionRepo;
    private final CouponRepository couponRepo;
    private final UsingServiceRepository usingServiceRepo;
    private final IncoinApiService incoinApi;
    private final SessionService sessionService;

    private static final int CREDITS_PER_COUPON = 50;

    // ── Get credits ───────────────────────────────────────────────────────────

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
            .map(Subscription::getCredits)
            .orElse(0);
    }

    // ── Algo1: reconcile credited orders ─────────────────────────────────────

    /**
     * algo1:
     * (a) fetch all orderIds from using_service where credited=n and userId=current
     * (b) fetch order details from Incoin, filter where status=credited (status==2 means paid/credited)
     * (c) mark those as credited=y in DB
     * (d) deduct count from subscription credits (min 0)
     */
    @Transactional
    public int runAlgo1(String userId) {
        // (a) group A — uncredited orders
        List<UsingService> groupA = usingServiceRepo
            .findByUserIdAndCredited(userId, "n");

        if (groupA.isEmpty()) return 0;

        // (b) fetch details from Incoin and filter credited ones
        UserSession session = sessionService.getOrThrow(userId);
        List<String> groupBIds = new ArrayList<>();

        for (UsingService entry : groupA) {
            try {
                Map<String, Object> detail = incoinApi.getOrderDetail(session, entry.getOrderId());
                // status=2 means credited/paid in Incoin
                Object status = detail.get("status");
                int statusInt = status instanceof Number ? ((Number) status).intValue() : -1;
                if (statusInt == 2) {
                    groupBIds.add(entry.getOrderId());
                }
            } catch (Exception e) {
                log.warn("algo1: Could not fetch order {} for user {}: {}", entry.getOrderId(), userId, e.getMessage());
            }
        }

        if (groupBIds.isEmpty()) return 0;

        // (c) mark as credited=y
        for (UsingService entry : groupA) {
            if (groupBIds.contains(entry.getOrderId())) {
                entry.setCredited("y");
                usingServiceRepo.save(entry);
            }
        }

        // (d) deduct from credits
        int x = groupBIds.size();
        Subscription sub = subscriptionRepo.findByUserId(userId).orElse(null);
        if (sub != null) {
            sub.setCredits(Math.max(0, sub.getCredits() - x));
            subscriptionRepo.save(sub);
        }

        log.info("algo1 for userId={}: {} orders credited, deducted {} credits", userId, x, x);
        return x;
    }

    // ── Coupon redemption ─────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> redeemCoupon(String userId, String couponCode) {
        Optional<Coupon> couponOpt = couponRepo.findByCouponCode(couponCode.trim().toUpperCase());

        // Coupon not found or already claimed
        if (couponOpt.isEmpty() || couponOpt.get().getUserId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This coupon is already claimed or does not exist.");
        }

        Coupon coupon = couponOpt.get();
        coupon.setUserId(userId);
        couponRepo.save(coupon);

        // Find or create subscription
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
        result.put("oldCredits", oldCredits);
        result.put("addedCredits", CREDITS_PER_COUPON);
        result.put("totalCredits", sub.getCredits());
        return result;
    }

    // ── Save grabbed order to using_service ──────────────────────────────────

    @Transactional
    public void saveGrabbedOrder(String userId, String orderId) {
        if (usingServiceRepo.existsByUserIdAndOrderId(userId, orderId)) return;
        UsingService entry = new UsingService();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setCredited("n");
        usingServiceRepo.save(entry);
    }

    // ── Deduct one credit per grab ────────────────────────────────────────────

    @Transactional
    public void deductCredit(String userId) {
        Subscription sub = subscriptionRepo.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No subscription found"));
        sub.setCredits(Math.max(0, sub.getCredits() - 1));
        subscriptionRepo.save(sub);
    }

    // ── Order history from our DB ─────────────────────────────────────────────

    public Page<UsingService> getOurHistory(String userId, int page) {
        return usingServiceRepo.findByUserId(
            userId,
            PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        );
    }
}
