package com.incoin.demo.service;

import com.incoin.demo.db.entity.UsingService;
import com.incoin.demo.db.repository.SubscriptionRepository;
import com.incoin.demo.db.repository.UsingServiceRepository;
import com.incoin.demo.db.entity.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreditsService {

    private final SubscriptionRepository subscriptionRepo;
    private final UsingServiceRepository usingServiceRepo;

    // ── Get credits ───────────────────────────────────────────────────────────

    public int getCredits(String userId) {
        return subscriptionRepo.findByUserId(userId)
                .map(Subscription::getCredits)
                .orElse(0);
    }

    // ── Save grabbed order ────────────────────────────────────────────────────

    @Transactional
    public void saveGrabbedOrder(String userId, String orderId) {
        if (usingServiceRepo.existsByUserIdAndOrderId(userId, orderId)) return;
        UsingService entry = new UsingService();
        entry.setUserId(userId);
        entry.setOrderId(orderId);
        entry.setCredited("n");
        usingServiceRepo.save(entry);
    }

    // ── Order history ─────────────────────────────────────────────────────────

    public Page<UsingService> getOurHistory(String userId, int page) {
        return usingServiceRepo.findByUserId(
                userId,
                PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "id"))
        );
    }
}