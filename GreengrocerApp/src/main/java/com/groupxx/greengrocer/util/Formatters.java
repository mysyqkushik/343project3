package com.groupxx.greengrocer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Formatters {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Formatters() {}

    public static String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME.format(value);
    }

    // Backward-compatible aliases used in some controllers
    public static String fmtDateTime(LocalDateTime value) {
        return formatDateTime(value);
    }

    public static String ts(LocalDateTime value) {
        return formatDateTime(value);
    }

    public static String formatMoney(BigDecimal value) {
        return formatDecimal(value);
    }

    public static String formatQuantity(BigDecimal value) {
        return formatDecimal(value);
    }

    private static String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
