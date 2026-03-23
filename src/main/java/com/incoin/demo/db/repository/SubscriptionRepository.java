package com.incoin.demo.db.repository;

import com.incoin.demo.db.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findByUserId(String userId);
}
