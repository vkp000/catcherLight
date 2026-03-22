package com.incoin.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartGrabRequest {

    @NotNull(message = "minAmount is required")
    private Double minAmount;

    @NotNull(message = "maxAmount is required")
    private Double maxAmount;

    @NotNull(message = "target is required")
    @Min(value = 1, message = "target must be at least 1")
    private Integer target;
}
