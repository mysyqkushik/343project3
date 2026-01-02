package com.groupxx.greengrocer.model;

public record CarrierSummaryRecord(
        long id,
        String username,
        boolean active,
        Double avgRating
) {}
