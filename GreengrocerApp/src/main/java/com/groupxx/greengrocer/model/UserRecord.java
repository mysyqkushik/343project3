package com.groupxx.greengrocer.model;

/**
 * Represents a registered user in the system.
 *
 * @param id       Unique database identifier for the user.
 * @param username Unique login username.
 * @param role     The role assigned to the user (CUSTOMER, CARRIER, OWNER).
 * @param address  Physical address for delivery (relevant for Customers).
 * @param phone    Contact phone number.
 * @param email    Contact email address.
 */
public record UserRecord(
                long id,
                String username,
                Role role,
                String address,
                String phone,
                String email) {
}
