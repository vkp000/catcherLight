package com.incoin.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {
    /** YOUR application JWT — NOT the Incoin token. */
    private String token;
    private String userId;
}
