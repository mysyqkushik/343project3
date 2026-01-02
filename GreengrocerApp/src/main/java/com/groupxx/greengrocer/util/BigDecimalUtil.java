package com.groupxx.greengrocer.util;

import java.math.BigDecimal;

public final class BigDecimalUtil {
    private BigDecimalUtil() {}

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
