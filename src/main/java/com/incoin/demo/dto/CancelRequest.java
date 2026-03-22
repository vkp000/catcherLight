package com.incoin.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelRequest {

    @NotBlank(message = "reason is required")
    private String reason;
}
