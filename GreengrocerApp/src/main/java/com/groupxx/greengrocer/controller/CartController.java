package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.CartModel;
import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.dao.CouponDao;
import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.dao.SettingsDao;
import com.groupxx.greengrocer.model.CartLine;
import com.groupxx.greengrocer.model.Coupon;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import com.groupxx.greengrocer.util.NumericValidators;
import com.groupxx.greengrocer.util.TextLimiters;
import com.groupxx.greengrocer.util.Validators;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CartController {
    private static final Logger LOG = Logger.getLogger(CartController.class.getName());

    @FXML
    private TableView<CartLine> table;
    @FXML
    private TableColumn<CartLine, String> colName;
    @FXML
    private TableColumn<CartLine, String> colKg;
    @FXML
    private TableColumn<CartLine, String> colUnit;
    @FXML
    private TableColumn<CartLine, String> colTotal;

    @FXML
    private TextField couponField;
    @FXML
    private DatePicker deliveryDate;
    @FXML
    private Spinner<Integer> hourSpinner;
    @FXML
    private Spinner<Integer> minSpinner;

    @FXML
    private Label subtotalLabel;
    @FXML
    private Label discountLabel;
    @FXML
    private Label vatLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label rulesLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Button checkoutButton;

    private final OrderDao orderDao = new OrderDao();
    private final SettingsDao settingsDao = new SettingsDao();
    private final CouponDao couponDao = new CouponDao();

    // Loyalty settings (loaded once)
    private BigDecimal loyaltyRate;
    private int loyaltyMinDeliveredOrders;
    private int deliveredOrdersSoFar;
    private Runnable onSuccessRefresh = () -> {
    };

    public void setOnSuccessRefresh(Runnable r) {
        this.onSuccessRefresh = (r == null) ? () -> {
        } : r;
    }

    @FXML
    private void initialize() {
        TextLimiters.limitLength(couponField, 32);

        colName.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name()));
        colKg.setCellValueFactory(v -> new SimpleStringProperty(
                Formatters.formatQuantity(com.groupxx.greengrocer.util.BigDecimalUtil.nz(v.getValue().kg()))));
        colUnit.setCellValueFactory(v -> new SimpleStringProperty(
                Formatters.formatMoney(com.groupxx.greengrocer.util.BigDecimalUtil.nz(v.getValue().unitPricePerKg()))));
        colTotal.setCellValueFactory(v -> new SimpleStringProperty(
                Formatters.formatMoney(com.groupxx.greengrocer.util.BigDecimalUtil.nz(v.getValue().lineTotal()))));

        hourSpinner
                .setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, LocalTime.now().getHour()));
        minSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, (LocalTime.now().getMinute() / 5) * 5));

        deliveryDate.setValue(LocalDate.now());

        loadDiscountSettingsAndStats();

        rulesLabel.setText(buildRulesText());
        refreshTable();
        recalcTotals();
        couponField.textProperty().addListener((o, a, b) -> recalcTotalsSafe());
        deliveryDate.valueProperty().addListener((o, a, b) -> recalcTotalsSafe());
        hourSpinner.valueProperty().addListener((o, a, b) -> recalcTotalsSafe());
        minSpinner.valueProperty().addListener((o, a, b) -> recalcTotalsSafe());
    }

    @FXML
    private void onRemoveSelected() {
        CartLine sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alerts.warn("No Selection", "Choose a cart line to remove.");
            return;
        }
        CartModel.get().remove(sel.productId());
        refreshTable();
        recalcTotals();
    }

    @FXML
    private void onClear() {
        CartModel.get().clear();
        refreshTable();
        recalcTotals();
    }

    @FXML
    private void onClose() {
        ((Stage) table.getScene().getWindow()).close();
    }

    @FXML
    private void onCheckout() {
        statusLabel.setText("");

        List<CartLine> lines = CartModel.get().snapshot();
        if (lines.isEmpty()) {
            statusLabel.setText("Cart is empty.");
            return;
        }

        BigDecimal subtotal = CartModel.get().subtotal();
        if (subtotal.compareTo(AppConfig.MIN_CART_SUBTOTAL) < 0) {
            Alerts.showWarn("Minimum Not Met", "Cart subtotal is too low.",
                    "Required: " + AppConfig.MIN_CART_SUBTOTAL + " | Current: " + subtotal);
            return;
        }

        LocalDate d = deliveryDate.getValue();
        if (d == null) {
            statusLabel.setText("Select delivery date.");
            return;
        }

        int hh;
        int mm;
        try {
            hh = NumericValidators.requireRange(hourSpinner.getValue(), 0, 23, "Hour");
            mm = NumericValidators.requireRange(minSpinner.getValue(), 0, 59, "Minute");
        } catch (IllegalArgumentException ex) {
            Alerts.showWarn("Invalid delivery time", ex.getMessage(), "");
            return;
        }
        LocalDateTime deliveryTs = LocalDateTime.of(d, LocalTime.of(hh, mm));

        LocalDateTime now = LocalDateTime.now();
        if (deliveryTs.isBefore(now)) {
            Alerts.showWarn("Invalid delivery time", "Delivery cannot be in the past.", "");
            return;
        }
        if (deliveryTs.isAfter(now.plusHours(48))) {
            Alerts.showWarn("Invalid delivery time", "Delivery must be within 48 hours.", "");
            return;
        }

        String coupon = Validators.normalize(couponField.getText());
        if (!coupon.isEmpty()) {
            // Validate coupon exists
            try {
                Coupon found = couponDao.findByCode(coupon);
                if (found == null) {
                    Alerts.showWarn("Coupon not valid", "Unknown coupon code.", "");
                    return;
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to validate coupon", ex);
                Alerts.showWarn("Coupon validation error", "Could not validate coupon.", ex.getMessage());
                return;
            }
        }

        setBusy(true);
        statusLabel.setText("Placing order...");

        new Thread(() -> {
            try {
                String username = SessionContext.username();
                if (username == null || username.isBlank()) {
                    throw new IllegalStateException("No active user session.");
                }

                long orderId = orderDao.placeOrder(
                        username,
                        lines,
                        coupon.isEmpty() ? null : coupon,
                        deliveryTs);

                javafx.application.Platform.runLater(() -> {
                    setBusy(false);
                    CartModel.get().clear();
                    refreshTable();
                    recalcTotals();
                    Alerts.showInfo("Order Placed", "Success", "Order ID: " + orderId);
                    onSuccessRefresh.run();
                    onClose();
                });

            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Checkout failed for user=" + SessionContext.username(), ex);
                javafx.application.Platform.runLater(() -> {
                    setBusy(false);
                    Alerts.showError("Checkout Failed", "Could not place order.", ex.getMessage());
                    statusLabel.setText("");
                });
            }
        }, "checkout-thread").start();
    }

    private void refreshTable() {
        table.setItems(FXCollections.observableArrayList(CartModel.get().snapshot()));
    }

    private void loadDiscountSettingsAndStats() {
        try {
            // Load loyalty settings (coupons are now loaded dynamically)
            loyaltyRate = settingsDao.getBigDecimal(SettingsDao.LOYALTY_RATE, AppConfig.LOYALTY_DISCOUNT_RATE);
            loyaltyMinDeliveredOrders = settingsDao.getInt(SettingsDao.LOYALTY_MIN_DELIVERED_ORDERS,
                    AppConfig.LOYALTY_MIN_DELIVERED_ORDERS);

            String u = SessionContext.username();
            deliveredOrdersSoFar = (u == null || u.isBlank()) ? 0 : orderDao.countDeliveredOrdersForCustomerUsername(u);
        } catch (Exception ex) {
            // Safe fallbacks
            loyaltyRate = AppConfig.LOYALTY_DISCOUNT_RATE;
            loyaltyMinDeliveredOrders = AppConfig.LOYALTY_MIN_DELIVERED_ORDERS;
            deliveredOrdersSoFar = 0;
        }
    }

    private String buildRulesText() {
        return "Rules: Minimum subtotal = " + AppConfig.MIN_CART_SUBTOTAL
                + " | Enter any valid coupon code for a discount"
                + " | Loyalty: -" + loyaltyRate.multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString()
                + "% if delivered orders >= " + loyaltyMinDeliveredOrders
                + " (you have " + deliveredOrdersSoFar + ")";
    }

    private void recalcTotals() {
        BigDecimal subtotal = CartModel.get().subtotal();

        BigDecimal couponDiscount = BigDecimal.ZERO;
        String couponInput = Validators.normalize(couponField.getText());
        if (!couponInput.isEmpty()) {
            try {
                Coupon found = couponDao.findByCode(couponInput);
                if (found != null && subtotal.compareTo(found.minSubtotal()) >= 0) {
                    couponDiscount = scale2(subtotal.multiply(found.rate()));
                }
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Failed to lookup coupon for recalc", ex);
            }
        }

        BigDecimal loyaltyDiscount = BigDecimal.ZERO;
        if (deliveredOrdersSoFar >= loyaltyMinDeliveredOrders) {
            BigDecimal base = subtotal.subtract(couponDiscount);
            if (base.compareTo(BigDecimal.ZERO) > 0) {
                loyaltyDiscount = scale2(base.multiply(loyaltyRate));
            }
        }

        BigDecimal discount = scale2(couponDiscount.add(loyaltyDiscount));
        BigDecimal afterDiscount = subtotal.subtract(discount);
        BigDecimal vat = scale2(afterDiscount.multiply(AppConfig.VAT_RATE));
        BigDecimal total = scale2(afterDiscount.add(vat));

        subtotalLabel.setText("Subtotal: " + Formatters.formatMoney(subtotal));
        discountLabel.setText("Discount: " + Formatters.formatMoney(discount)
                + (discount.compareTo(BigDecimal.ZERO) > 0 ? " (coupon " + Formatters.formatMoney(couponDiscount)
                        + " + loyalty " + Formatters.formatMoney(loyaltyDiscount) + ")" : ""));
        vatLabel.setText("VAT: " + Formatters.formatMoney(vat));
        totalLabel.setText("Total: " + Formatters.formatMoney(total));

    }

    private void recalcTotalsSafe() {
        try {
            recalcTotals();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "Failed to recalculate totals", ex);
        }
    }

    private void setBusy(boolean busy) {
        checkoutButton.setDisable(busy);
        table.setDisable(busy);
        couponField.setDisable(busy);
        deliveryDate.setDisable(busy);
        hourSpinner.setDisable(busy);
        minSpinner.setDisable(busy);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
