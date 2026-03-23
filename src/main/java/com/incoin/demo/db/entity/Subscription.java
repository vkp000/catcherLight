package com.incoin.demo.db.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "subscription")
@Data
@NoArgsConstructor
public class Subscription {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "credits", nullable = false)
    private int credits = 0;
}
