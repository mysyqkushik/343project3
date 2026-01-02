package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.db.DbAdapter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Simple key/value settings stored in DB so the Owner can manage discounts without code changes.
 */
public final class SettingsDao {

    // Keys
    public static final String COUPON_CODE = "coupon_code";
    public static final String COUPON_RATE = "coupon_rate"; // 0.10 means 10%
    public static final String COUPON_MIN_SUBTOTAL = "coupon_min_subtotal";

    public static final String LOYALTY_RATE = "loyalty_rate"; // 0.05 means 5%
    public static final String LOYALTY_MIN_DELIVERED_ORDERS = "loyalty_min_delivered_orders";

    public void ensureSettingsTableAndSeedDefaults() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                CREATE TABLE IF NOT EXISTS settings_info (
                  k VARCHAR(64) PRIMARY KEY,
                  v VARCHAR(255) NOT NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP
                )
                """)) {
                ps.execute();
            }

            seedIfMissing(c, COUPON_CODE, "SAVE10");
            seedIfMissing(c, COUPON_RATE, "0.10");
            seedIfMissing(c, COUPON_MIN_SUBTOTAL, "200.00");

            seedIfMissing(c, LOYALTY_RATE, "0.05");
            seedIfMissing(c, LOYALTY_MIN_DELIVERED_ORDERS, "5");
        }
    }

    private static void seedIfMissing(Connection c, String k, String v) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM settings_info WHERE k=? LIMIT 1")) {
            ps.setString(1, k);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return;
            }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO settings_info (k, v) VALUES (?, ?)")) {
            ps.setString(1, k);
            ps.setString(2, v);
            ps.executeUpdate();
        }
    }

    public String getString(String key) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT v FROM settings_info WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        }
    }

    public String getString(String key, String defaultValue) throws Exception {
        String v = getString(key);
        return (v == null) ? defaultValue : v;
    }

    public BigDecimal getBigDecimal(String key, BigDecimal defaultValue) throws Exception {
        String v = getString(key);
        if (v == null) return defaultValue;
        try {
            return new BigDecimal(v);
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) throws Exception {
        String v = getString(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v);
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    /**
     * Backward-compatible alias used by some controllers.
     * Internally uses {@link #setString(String, String)}.
     */
    public void upsert(String key, String value) throws Exception {
        setString(key, value);
    }

    public void setString(String key, String value) throws Exception {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Key is blank");
        if (value == null) value = "";
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO settings_info (k, v) VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE v = VALUES(v)
                 """)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public void setBigDecimal(String key, BigDecimal value) throws Exception {
        setString(key, value == null ? "0" : value.toPlainString());
    }

    public void setInt(String key, int value) throws Exception {
        setString(key, String.valueOf(value));
    }
}
