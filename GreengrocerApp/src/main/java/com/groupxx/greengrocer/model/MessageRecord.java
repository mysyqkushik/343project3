package com.groupxx.greengrocer.model;

import java.time.LocalDateTime;

public record MessageRecord(
        long id,
        long customerId,
        String customerUsername,
        Role senderRole,
        String messageText,
        LocalDateTime createdAt
) {}
