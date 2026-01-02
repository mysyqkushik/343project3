package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.dao.CouponDao;
import com.groupxx.greengrocer.dao.SettingsDao;
import com.groupxx.greengrocer.model.Coupon;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Validators;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Owner UI to manage coupons and loyalty rules.
 *
 * Coupons are now stored in the `coupons` table (multiple coupons supported).
 * Loyalty settings remain in the Settings table.
 */
public final class OwnerDiscountsController {

    public static final String KEY_LOYALTY_RATE = "loyalty_rate";
    public static final String KEY_LOYALTY_MIN_DELIVERED = "loyalty_min_delivered_orders";

    // Coupon table
    @FXML
    private TableView<Coupon> couponTable;
    @FXML
    private TableColumn<Coupon, String> colCode;
    @FXML
    private TableColumn<Coupon, String> colRate;
    @FXML
    private TableColumn<Coupon, String> colMinSubtotal;

    // Coupon input fields
    @FXML
    private TextField newCouponCodeField;
    @FXML
    private TextField newCouponRateField;
    @FXML
    private TextField newCouponMinSubtotalField;

    // Loyalty fields
    @FXML
    private TextField loyaltyRateField;
    @FXML
    private TextField loyaltyMinDeliveredField;

    private final CouponDao couponDao = new CouponDao();
    private final SettingsDao settingsDao = new SettingsDao();

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().code()));
        colRate.setCellValueFactory(v -> {
            BigDecimal rate = v.getValue().rate();
            String display = rate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() + "%";
            return new SimpleStringProperty(display);
        });
        colMinSubtotal.setCellValueFactory(v -> {
            BigDecimal min = v.getValue().minSubtotal();
            return new SimpleStringProperty(min.setScale(2, RoundingMode.HALF_UP).toPlainString());
        });

        reload();
    }

    /** Called by Refresh button. */
    public void reload() {
        loadCoupons();
        loadLoyaltySettings();
    }

    @FXML
    public void onReload() {
        reload();
    }

    private void loadCoupons() {
        try {
            couponTable.setItems(FXCollections.observableArrayList(couponDao.getAll()));
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load coupons.", ex.getMessage());
        }
    }

    private void loadLoyaltySettings() {
        try {
            BigDecimal loyaltyRate = settingsDao.getBigDecimal(KEY_LOYALTY_RATE, AppConfig.LOYALTY_DISCOUNT_RATE);
            int loyaltyMinDelivered = settingsDao.getInt(KEY_LOYALTY_MIN_DELIVERED,
                    AppConfig.LOYALTY_MIN_DELIVERED_ORDERS);

            loyaltyRateField.setText(loyaltyRate == null ? "0" : loyaltyRate.stripTrailingZeros().toPlainString());
            loyaltyMinDeliveredField.setText(String.valueOf(loyaltyMinDelivered));
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load loyalty settings.", ex.getMessage());
        }
    }

    @FXML
    public void onAddCoupon() {
        try {
            String code = Validators.normalize(newCouponCodeField.getText()).toUpperCase();
            if (code.isBlank()) {
                Alerts.warn("Invalid input", "Coupon code cannot be blank.");
                return;
            }
            BigDecimal rate = parseRate(newCouponRateField.getText(), "Coupon rate");
            BigDecimal minSubtotal = parseMoney(newCouponMinSubtotalField.getText(), "Min subtotal");

            if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Coupon rate must be between 0 and 1 (e.g., 0.10 for 10%).");
            }
            if (minSubtotal.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Min subtotal cannot be negative.");
            }

            Coupon coupon = new Coupon(code, rate, minSubtotal);
            couponDao.upsert(coupon);

            newCouponCodeField.clear();
            newCouponRateField.clear();
            newCouponMinSubtotalField.clear();

            loadCoupons();
            Alerts.info("Saved", "Coupon '" + code + "' added/updated.");
        } catch (IllegalArgumentException ex) {
            Alerts.warn("Invalid input", ex.getMessage());
        } catch (Exception ex) {
            Alerts.showError("Save Failed", "Cannot save coupon.", ex.getMessage());
        }
    }

    @FXML
    public void onDeleteCoupon() {
        Coupon selected = couponTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alerts.warn("No Selection", "Select a coupon to delete.");
            return;
        }
        try {
            couponDao.delete(selected.code());
            loadCoupons();
            Alerts.info("Deleted", "Coupon '" + selected.code() + "' deleted.");
        } catch (Exception ex) {
            Alerts.showError("Delete Failed", "Cannot delete coupon.", ex.getMessage());
        }
    }

    @FXML
    public void onSaveLoyalty() {
        try {
            BigDecimal loyaltyRate = parseRate(loyaltyRateField.getText(), "Loyalty rate");
            int loyaltyMinDelivered = parseIntNonNegative(loyaltyMinDeliveredField.getText(),
                    "Minimum delivered orders");

            if (loyaltyRate.compareTo(BigDecimal.ZERO) < 0 || loyaltyRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Loyalty rate must be between 0 and 1 (e.g., 0.05 for 5%).");
            }

            settingsDao.upsert(KEY_LOYALTY_RATE, loyaltyRate.toPlainString());
            settingsDao.upsert(KEY_LOYALTY_MIN_DELIVERED, String.valueOf(loyaltyMinDelivered));

            Alerts.info("Saved", "Loyalty settings updated.");
            loadLoyaltySettings();
        } catch (IllegalArgumentException ex) {
            Alerts.warn("Invalid input", ex.getMessage());
        } catch (Exception ex) {
            Alerts.showError("Save Failed", "Cannot save loyalty settings.", ex.getMessage());
        }
    }

    private static BigDecimal parseRate(String raw, String fieldName) {
        String s = Validators.normalize(raw);
        if (s.isBlank())
            return BigDecimal.ZERO;
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
        if (s.isBlank())
            return BigDecimal.ZERO;
        try {
            BigDecimal v = new BigDecimal(s);
            return v.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    private static int parseIntNonNegative(String raw, String fieldName) {
        String s = Validators.normalize(raw);
        if (s.isBlank())
            return 0;
        try {
            int v = Integer.parseInt(s);
            if (v < 0)
                throw new NumberFormatException();
            return v;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a non-negative integer.");
        }
    }
}
