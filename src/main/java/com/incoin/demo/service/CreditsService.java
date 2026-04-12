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
    private final CouponRepository       couponRepo;
    private final UsingServiceRepository usingServiceRepo;
    private final IncoinApiService       incoinApi;
    private final SessionService         sessionService;

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getCredits)
                .orElse(0);
    }

    @Transactional
    public int runAlgo1(String userId) {
        List<UsingService> groupA = usingServiceRepo.findByUserIdAndCredited(userId, "n");
        if (groupA.isEmpty()) return 0;

        UserSession session;
        try {
            session = sessionService.getOrThrow(userId);
        } catch (Exception e) {
            log.warn("algo1: no active session for userId={}, skipping", userId);
            return 0;
        }

        List<String> groupBIds = new ArrayList<>();
        for (UsingService entry : groupA) {
            try {
                Map<String, Object> detail = incoinApi.getOrderDetail(session, entry.getOrderId());
                Object status = detail.get("status");
                int statusInt = status instanceof Number ? ((Number) status).intValue() : -1;
                if (statusInt == 2) groupBIds.add(entry.getOrderId());
            } catch (Exception e) {
                log.warn("algo1: Could not fetch order {} for user {}: {}",
                        entry.getOrderId(), userId, e.getMessage());
            }
        }

        if (groupBIds.isEmpty()) return 0;

        for (UsingService entry : groupA) {
            if (groupBIds.contains(entry.getOrderId())) {
                entry.setCredited("y");
                usingServiceRepo.save(entry);
            }
        }

        int x = groupBIds.size();
        Subscription sub = subscriptionRepo.findByUserId(userId).orElse(null);
        if (sub != null) {
            sub.setCredits(Math.max(0, sub.getCredits() - x));
            subscriptionRepo.save(sub);
        }

        log.info("algo1 userId={}: {} credited, {} deducted", userId, x, x);
        return x;
    }

    @Transactional
    public Map<String, Object> redeemCoupon(String userId, String couponCode) {
        Optional<Coupon> couponOpt = couponRepo.findByCouponCode(couponCode.trim().toUpperCase());

        if (couponOpt.isEmpty() || couponOpt.get().getUserId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This coupon is already claimed or does not exist.");
        }

        Coupon coupon = couponOpt.get();
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
            runAlgo1(userId);
            sub = subscriptionRepo.findByUserId(userId).orElse(sub);
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

    @Transactional
    public void saveGrabbedOrder(String userId, String orderId) {
        if (usingServiceRepo.existsByUserIdAndOrderId(userId, orderId)) return;
        UsingService entry = new UsingService();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setCredited("n");
        usingServiceRepo.save(entry);
    }

    public Page<UsingService> getOurHistory(String userId, int page) {
        return usingServiceRepo.findByUserId(
                userId,
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        );
    }
}