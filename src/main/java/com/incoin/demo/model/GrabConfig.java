package com.incoin.demo.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Parameters supplied by the user when starting a grab session. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class GrabConfig {
    private double minAmount;
    private double maxAmount;
    private int    target;
}
