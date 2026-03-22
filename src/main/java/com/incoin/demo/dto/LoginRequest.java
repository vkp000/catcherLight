package com.incoin.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Captcha code is required")
    private String captchaCode;

    @NotBlank(message = "Captcha token is required")
    private String captchaToken;
}
