package com.incoin.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    private String sessionId; // random UUID — no JWT
    private String userId;
}