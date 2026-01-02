package com.groupxx.greengrocer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumericValidators {
    private NumericValidators() {}

    public static BigDecimal requirePositiveScale(BigDecimal value, int scale, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0.");
        }
        if (value.scale() > scale) {
            throw new IllegalArgumentException(fieldName + " must have at most " + scale + " decimal places.");
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal parseBigDecimal(String raw, int scale, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            BigDecimal value = new BigDecimal(raw.trim());
            return requirePositiveScale(value, scale, fieldName);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    public static int requireRange(Integer value, int min, int max, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ".");
        }
        return value;
    }
}
