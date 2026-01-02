package com.groupxx.greengrocer.model;

import java.math.BigDecimal;

/**
 * Represents a discount coupon that the owner can create.
 */
public record Coupon(String code, BigDecimal rate, BigDecimal minSubtotal) {
    public Coupon {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Coupon code cannot be blank");
        }
        if (rate == null) {
            rate = BigDecimal.ZERO;
        }
        if (minSubtotal == null) {
            minSubtotal = BigDecimal.ZERO;
        }
    }
}
