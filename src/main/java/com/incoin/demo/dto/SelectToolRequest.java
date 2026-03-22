package com.incoin.demo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SelectToolRequest {

    @NotBlank(message = "upiAddr is required")
    private String upiAddr;

    @NotBlank(message = "toolType is required")
    private String toolType;

    @NotBlank(message = "toolName is required")
    private String toolName;
}
