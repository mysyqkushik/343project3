package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.model.Coupon;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for managing coupons in the database.
 */
public final class CouponDao {

    /**
     * Ensures the coupons table exists and seeds a default coupon if empty.
     */
    public void ensureTableAndSeedDefaults() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS coupons (
                      code VARCHAR(32) PRIMARY KEY,
                      rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
                      min_subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00
                    )
                    """)) {
                ps.execute();
            }

            // Seed default coupon if table is empty
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM coupons")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        upsert(new Coupon("SAVE10", new BigDecimal("0.10"), new BigDecimal("200.00")));
                    }
                }
            }
        }
    }

    /**
     * Returns all coupons.
     */
    public List<Coupon> getAll() throws Exception {
        List<Coupon> result = new ArrayList<>();
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c
                        .prepareStatement("SELECT code, rate, min_subtotal FROM coupons ORDER BY code")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * Finds a coupon by its code (case-insensitive).
     */
    public Coupon findByCode(String code) throws Exception {
        if (code == null || code.isBlank())
            return null;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT code, rate, min_subtotal FROM coupons WHERE UPPER(code) = UPPER(?)")) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Inserts or updates a coupon.
     */
    public void upsert(Coupon coupon) throws Exception {
        if (coupon == null)
            throw new IllegalArgumentException("Coupon is null");
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO coupons (code, rate, min_subtotal) VALUES (?, ?, ?)
                        ON DUPLICATE KEY UPDATE rate = VALUES(rate), min_subtotal = VALUES(min_subtotal)
                        """)) {
            ps.setString(1, coupon.code().toUpperCase());
            ps.setBigDecimal(2, coupon.rate());
            ps.setBigDecimal(3, coupon.minSubtotal());
            ps.executeUpdate();
        }
    }

    /**
     * Deletes a coupon by code.
     */
    public void delete(String code) throws Exception {
        if (code == null || code.isBlank())
            return;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement("DELETE FROM coupons WHERE UPPER(code) = UPPER(?)")) {
            ps.setString(1, code.trim());
            ps.executeUpdate();
        }
    }

    private Coupon mapRow(ResultSet rs) throws Exception {
        String code = rs.getString("code");
        BigDecimal rate = rs.getBigDecimal("rate");
        BigDecimal minSubtotal = rs.getBigDecimal("min_subtotal");
        return new Coupon(code, rate, minSubtotal);
    }
}
