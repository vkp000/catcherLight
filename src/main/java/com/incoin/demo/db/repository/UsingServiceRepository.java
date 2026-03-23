package com.incoin.demo.db.repository;

import com.incoin.demo.db.entity.UsingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsingServiceRepository extends JpaRepository<UsingService, Long> {
    List<UsingService> findByUserIdAndCredited(String userId, String credited);
    Page<UsingService> findByUserId(String userId, Pageable pageable);
    boolean existsByUserIdAndOrderId(String userId, String orderId);
}
