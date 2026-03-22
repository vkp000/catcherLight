package com.incoin.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Holds the captcha image bytes and the captchaToken header from Incoin. */
@Data
@AllArgsConstructor
public class CaptchaResult {
    private String captchaToken;
    private byte[] imageBytes;
}
