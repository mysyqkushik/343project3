package com.groupxx.greengrocer.model;

import java.time.LocalDateTime;

/**
 * Record representing a carrier review/comment from a customer.
 */
public record CarrierReviewRecord(
        long orderId,
        String customerUsername,
        String carrierUsername,
        int rating,
        String comment,
        LocalDateTime reviewTime) {
}
