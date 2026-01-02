package com.groupxx.greengrocer.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record CartLine(
        long productId,
        String name,
        ProductCategory category,
        BigDecimal unitPricePerKg,
        BigDecimal kg
) {
    public BigDecimal lineTotal() {
        BigDecimal unit = com.groupxx.greengrocer.util.BigDecimalUtil.nz(unitPricePerKg);
        BigDecimal amount = com.groupxx.greengrocer.util.BigDecimalUtil.nz(kg);
        return unit.multiply(amount).setScale(2, RoundingMode.HALF_UP);
    }
}
