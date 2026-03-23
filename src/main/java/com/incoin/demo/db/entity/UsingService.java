package com.incoin.demo.db.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "using_service",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "order_id"})
)
@Data
@NoArgsConstructor
public class UsingService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "credited", nullable = false)
    private String credited = "n"; // "n" or "y"
}
