package com.incoin.demo.db.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupons")
@Data
@NoArgsConstructor
public class Coupon {

    @Id
    @Column(name = "coupon_code", nullable = false, unique = true)
    private String couponCode;

    @Column(name = "user_id")
    private String userId; // null = unclaimed

    @Column(name = "value")
    private Integer value;
}
