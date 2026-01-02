package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.dao.SettingsDao;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Validators;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Owner UI to update coupon/loyalty rules stored in the Settings table.
 *
 * Stored values are used by Cart checkout:
 * - coupon_code, coupon_rate, coupon_min_subtotal
 * - loyalty_rate, loyalty_min_delivered_orders
 */
public final class OwnerDiscountsController {

    public static final String KEY_COUPON_CODE = "coupon_code";
    public static final String KEY_COUPON_RATE = "coupon_rate";
    public static final String KEY_COUPON_MIN_SUBTOTAL = "coupon_min_subtotal";
    public static final String KEY_LOYALTY_RATE = "loyalty_rate";
    public static final String KEY_LOYALTY_MIN_DELIVERED = "loyalty_min_delivered_orders";

    @FXML private TextField couponCodeField;
    @FXML private TextField couponRateField;
    @FXML private TextField couponMinSubtotalField;
    @FXML private TextField loyaltyRateField;
    @FXML private TextField loyaltyMinDeliveredField;

    private final SettingsDao settingsDao = new SettingsDao();

    @FXML
    public void initialize() {
        reload();
    }

    /** Called by OwnerHomeController "Refresh" button. */
    public void reload() {
        try {
            String code = settingsDao.getString(KEY_COUPON_CODE, AppConfig.COUPON_CODE);
            BigDecimal couponRate = settingsDao.getBigDecimal(KEY_COUPON_RATE, AppConfig.COUPON_DISCOUNT_RATE);
            BigDecimal couponMin = settingsDao.getBigDecimal(KEY_COUPON_MIN_SUBTOTAL, AppConfig.COUPON_MIN_SUBTOTAL);
            BigDecimal loyaltyRate = settingsDao.getBigDecimal(KEY_LOYALTY_RATE, AppConfig.LOYALTY_DISCOUNT_RATE);
            int loyaltyMinDelivered = settingsDao.getInt(KEY_LOYALTY_MIN_DELIVERED, AppConfig.LOYALTY_MIN_DELIVERED_ORDERS);

            couponCodeField.setText(code == null ? "" : code);
            couponRateField.setText(couponRate == null ? "0" : couponRate.stripTrailingZeros().toPlainString());
            couponMinSubtotalField.setText(couponMin == null ? "0" : couponMin.setScale(2, RoundingMode.HALF_UP).toPlainString());
            loyaltyRateField.setText(loyaltyRate == null ? "0" : loyaltyRate.stripTrailingZeros().toPlainString());
            loyaltyMinDeliveredField.setText(String.valueOf(loyaltyMinDelivered));
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load discount settings.", ex.getMessage());
        }
    }

    @FXML
    public void onReload() {
        reload();
    }

    @FXML
    public void onSave() {
        try {
            String code = Validators.normalize(couponCodeField.getText()).toUpperCase();
            BigDecimal couponRate = parseRate(couponRateField.getText(), "Coupon rate");
            BigDecimal couponMin = parseMoney(couponMinSubtotalField.getText(), "Coupon minimum subtotal");
            BigDecimal loyaltyRate = parseRate(loyaltyRateField.getText(), "Loyalty rate");
            int loyaltyMinDelivered = parseIntNonNegative(loyaltyMinDeliveredField.getText(), "Minimum delivered orders");

            // Validation rules (explicit + robust)
            if (couponRate.compareTo(BigDecimal.ZERO) < 0 || couponRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Coupon rate must be between 0 and 1 (e.g., 0.10 for 10%).");
            }
            if (loyaltyRate.compareTo(BigDecimal.ZERO) < 0 || loyaltyRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Loyalty rate must be between 0 and 1 (e.g., 0.05 for 5%).");
            }
            if (couponMin.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Coupon minimum subtotal cannot be negative.");
            }

            settingsDao.upsert(KEY_COUPON_CODE, code);
            settingsDao.upsert(KEY_COUPON_RATE, couponRate.toPlainString());
            settingsDao.upsert(KEY_COUPON_MIN_SUBTOTAL, couponMin.setScale(2, RoundingMode.HALF_UP).toPlainString());
            settingsDao.upsert(KEY_LOYALTY_RATE, loyaltyRate.toPlainString());
            settingsDao.upsert(KEY_LOYALTY_MIN_DELIVERED, String.valueOf(loyaltyMinDelivered));

            Alerts.info("Saved", "Discount settings updated.");
            reload();
        } catch (IllegalArgumentException ex) {
            Alerts.warn("Invalid input", ex.getMessage());
        } catch (Exception ex) {
            Alerts.showError("Save Failed", "Cannot save discount settings.", ex.getMessage());
        }
    }

    private static BigDecimal parseRate(String raw, String fieldName) {
        String s = Validators.normalize(raw);
        if (s.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal v = new BigDecimal(s);
            // Convenience: allow entering 10 meaning 10%
            if (v.compareTo(BigDecimal.ONE) > 0) {
                v = v.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            }
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    private static BigDecimal parseMoney(String raw, String fieldName) {
        String s = Validators.normalize(raw);
        if (s.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal v = new BigDecimal(s);
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    private static int parseIntNonNegative(String raw, String fieldName) {
        String s = Validators.normalize(raw);
        if (s.isBlank()) return 0;
        try {
            int v = Integer.parseInt(s);
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative integer.");
        }
    }
}
