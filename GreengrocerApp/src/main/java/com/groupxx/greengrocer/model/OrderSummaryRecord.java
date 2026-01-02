package com.groupxx.greengrocer.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderSummaryRecord(
        long orderId,
        int customerId,
        String customerUsername,
        String customerAddress,
        Integer carrierId,
        String carrierUsername,
        LocalDateTime orderTime,
        LocalDateTime requestedDeliveryTime,
        LocalDateTime deliveredTime,
        boolean canceled,
        boolean delivered,
        BigDecimal totalInclVat
) {}
