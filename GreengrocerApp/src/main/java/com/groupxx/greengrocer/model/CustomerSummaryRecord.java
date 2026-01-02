package com.groupxx.greengrocer.model;

/**
 * Wraps a customer UserRecord with their total order count.
 */
public record CustomerSummaryRecord(
        UserRecord user,
        int orderCount) {
}
