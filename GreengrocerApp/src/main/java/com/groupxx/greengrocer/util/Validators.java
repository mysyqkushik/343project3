package com.groupxx.greengrocer.util;

public final class Validators {
    private Validators() {
    }

    public static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    public static boolean isValidUsername(String username) {
        return username != null && username.matches("^[A-Za-z0-9_]{3,32}$");
    }

    public static boolean isStrongPassword(String password) {
        if (password == null)
            return false;
        if (password.length() < 8 || password.length() > 64)
            return false;

        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        boolean hasSpecial = password.matches(".*[^A-Za-z0-9].*");
        boolean hasSpace = password.matches(".*\\s.*");

        return hasUpper && hasLower && hasDigit && hasSpecial && !hasSpace;
    }

    /**
     * Validates phone number in Turkish format:
     * - +90 followed by 10 digits (e.g., +905551234567)
     * - 0 followed by 10 digits (e.g., 05551234567)
     * Empty/blank phones are allowed (optional field).
     */
    public static boolean isReasonablePhone(String phone) {
        if (phone == null || phone.isBlank())
            return true;
        String p = phone.trim();
        // Turkish format: +90XXXXXXXXXX or 0XXXXXXXXXX
        return p.matches("^\\+90\\d{10}$") || p.matches("^0\\d{10}$");
    }

    /**
     * Lightweight BigDecimal parser used by Owner forms.
     * <p>
     * - Blank input is treated as 0
     * - Invalid numbers throw an IllegalArgumentException
     */
    public static java.math.BigDecimal parseBigDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.math.BigDecimal.ZERO;
        }
        try {
            return new java.math.BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number: " + raw);
        }
    }

    public static boolean isValidEmail(String email) {
        if (email == null)
            return true;
        String e = normalize(email);
        if (e.isBlank())
            return true;
        if (e.length() > 96)
            return false;
        // simple sanity check (not RFC-perfect, but good for UI validation)
        return e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    }

}
