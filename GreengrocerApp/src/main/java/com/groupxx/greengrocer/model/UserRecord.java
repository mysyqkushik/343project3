package com.groupxx.greengrocer.model;

public record UserRecord(
        long id,
        String username,
        Role role,
        String address,
        String phone,
        String email
) {}
