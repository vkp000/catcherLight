package com.incoin.demo.db.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "authentic_users")
@Data
@NoArgsConstructor
public class AuthenticUser {

    @Id
    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "password", nullable = false)
    private String password; // plain text as requested
}