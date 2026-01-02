package com.groupxx.greengrocer.model;

import java.math.BigDecimal;

public record ProductRecord(
        long id,
        String name,
        ProductCategory category,
        BigDecimal basePricePerKg,
        BigDecimal stockKg,
        BigDecimal thresholdKg,
        byte[] imageBytes,
        boolean active
) {
    public BigDecimal effectivePricePerKg() {
        // Rule: if stock <= threshold => price doubled
        BigDecimal base = com.groupxx.greengrocer.util.BigDecimalUtil.nz(basePricePerKg);
        BigDecimal stock = com.groupxx.greengrocer.util.BigDecimalUtil.nz(stockKg);
        BigDecimal threshold = com.groupxx.greengrocer.util.BigDecimalUtil.nz(thresholdKg);
        if (stock.compareTo(threshold) <= 0) {
            return base.multiply(BigDecimal.valueOf(2));
        }
        return base;
    }
}
