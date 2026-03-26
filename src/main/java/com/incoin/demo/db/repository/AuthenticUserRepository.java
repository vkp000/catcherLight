package com.incoin.demo.db.repository;

import com.incoin.demo.db.entity.AuthenticUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthenticUserRepository extends JpaRepository<AuthenticUser, String> {
}