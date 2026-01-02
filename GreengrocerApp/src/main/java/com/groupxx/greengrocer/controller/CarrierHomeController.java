package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.model.OrderSummaryRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CarrierHomeController {
    private static final Logger LOG = Logger.getLogger(CarrierHomeController.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private TableView<OrderSummaryRecord> availableTable;
    @FXML
    private TableView<OrderSummaryRecord> currentTable;
    @FXML
    private TableView<OrderSummaryRecord> completedTable;

    @FXML
    private TableColumn<OrderSummaryRecord, String> aId, aCustomer, aAddress, aReq, aTotal;
    @FXML
    private TableColumn<OrderSummaryRecord, String> cId, cCustomer, cAddress, cReq, cTotal;
    @FXML
    private TableColumn<OrderSummaryRecord, String> dId, dCustomer, dDelivered, dTotal;

    private final OrderDao orderDao = new OrderDao();

    @FXML
    private void initialize() {
        availableTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        bindCommon(aId, aCustomer, aAddress, aReq, aTotal);
        bindCommon(cId, cCustomer, cAddress, cReq, cTotal);

        dId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().orderId())));
        dCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().customerUsername()));
        dDelivered.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatDateTime(cd.getValue().deliveredTime())));
        dTotal.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatMoney(cd.getValue().totalInclVat())));

        loadAll();
    }

    private void bindCommon(TableColumn<OrderSummaryRecord, String> id,
            TableColumn<OrderSummaryRecord, String> customer,
            TableColumn<OrderSummaryRecord, String> address,
            TableColumn<OrderSummaryRecord, String> req,
            TableColumn<OrderSummaryRecord, String> total) {
        id.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().orderId())));
        customer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().customerUsername()));
        address.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().customerAddress() == null ? "-" : cd.getValue().customerAddress()));
        req.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatDateTime(cd.getValue().requestedDeliveryTime())));
        total.setCellValueFactory(cd -> new SimpleStringProperty(Formatters.formatMoney(cd.getValue().totalInclVat())));
    }

    private void loadAll() {
        try {
            String carrierUsername = SessionContext.requireUsername();

            List<OrderSummaryRecord> available = orderDao.listAvailableOrders();
            List<OrderSummaryRecord> current = orderDao.listCurrentOrdersForCarrierUsername(carrierUsername);
            List<OrderSummaryRecord> completed = orderDao.listCompletedOrdersForCarrierUsername(carrierUsername);

            availableTable.getItems().setAll(available);
            currentTable.getItems().setAll(current);
            completedTable.getItems().setAll(completed);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load carrier orders", e);
            Alerts.unexpected("Failed to load carrier orders.", e);
        }
    }

    @FXML
    private void onClaimSelected() {
        try {
            String carrierUsername = SessionContext.requireUsername();
            var selected = availableTable.getSelectionModel().getSelectedItems();
            if (selected == null || selected.isEmpty()) {
                Alerts.warn("No Selection", "Select one or more available orders.");
                return;
            }

            int ok = 0, fail = 0;
            for (OrderSummaryRecord r : selected) {
                boolean claimed = orderDao.claimOrderByCarrierUsername(carrierUsername, r.orderId());
                if (claimed)
                    ok++;
                else
                    fail++;
            }

            Alerts.info("Claim Result", "Claimed: " + ok + "\nFailed (already taken): " + fail);
            loadAll();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Claim operation failed", e);
            Alerts.unexpected("Claim operation failed.", e);
        }
    }

    @FXML
    private void onMarkDelivered() {
        OrderSummaryRecord sel = currentTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alerts.warn("No Selection", "Select one current/selected order.");
            return;
        }

        LocalDateTime deliveredAt = promptDeliveredDateTime(sel);
        if (deliveredAt == null)
            return; // cancelled or invalid

        try {
            String carrierUsername = SessionContext.requireUsername();
            boolean ok = orderDao.markDeliveredByCarrierUsername(carrierUsername, sel.orderId(), deliveredAt);
            if (ok)
                Alerts.info("Done", "Order marked as delivered.");
            else
                Alerts.warn("Failed", "Could not mark delivered (maybe not assigned to you / already delivered).");
            loadAll();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Mark delivered failed for order=" + sel.orderId(), e);
            Alerts.unexpected("Mark delivered failed.", e);
        }
    }

    /**
     * Requirement: carrier must enter delivery date/time AFTER delivery/payment.
     * We enforce:
     * - date/time is required
     * - deliveredAt >= orderTime (if present)
     * - deliveredAt is not in the future (allowing small clock skew)
     */
    private LocalDateTime promptDeliveredDateTime(OrderSummaryRecord order) {
        Dialog<LocalDateTime> dialog = new Dialog<>();
        dialog.setTitle("Delivery Date/Time");
        dialog.setHeaderText(
                "Enter delivery date and time (after delivery/payment).\n" +
                        "Order #" + order.orderId() + " • Customer: " + order.customerUsername());

        ButtonType okType = new ButtonType("Mark Delivered", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField timeField = new TextField(LocalTime.now().withSecond(0).withNano(0).format(TIME_FMT));
        timeField.setPromptText("HH:mm");

        Label error = new Label();
        error.setWrapText(true);
        error.setStyle("-fx-text-fill: #b00020;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15, 15, 5, 15));

        grid.add(new Label("Date:"), 0, 0);
        grid.add(datePicker, 1, 0);
        grid.add(new Label("Time (HH:mm):"), 0, 1);
        grid.add(timeField, 1, 1);
        grid.add(error, 0, 2, 2, 1);

        dialog.getDialogPane().setContent(grid);

        Node okButton = dialog.getDialogPane().lookupButton(okType);

        Runnable validate = () -> {
            String msg = validateDeliveredInput(order, datePicker.getValue(), timeField.getText());
            error.setText(msg == null ? "" : msg);
            okButton.setDisable(msg != null);
        };

        // initial validation + live validation
        validate.run();
        datePicker.valueProperty().addListener((obs, o, n) -> validate.run());
        timeField.textProperty().addListener((obs, o, n) -> validate.run());

        dialog.setResultConverter(bt -> {
            if (bt != okType)
                return null;

            // Safe: OK button is disabled unless valid.
            LocalDate d = datePicker.getValue();
            LocalTime t = LocalTime.parse(timeField.getText().trim(), TIME_FMT);
            return LocalDateTime.of(d, t);
        });

        return dialog.showAndWait().orElse(null);
    }

    private String validateDeliveredInput(OrderSummaryRecord order, LocalDate date, String timeText) {
        if (date == null)
            return "Please select a date.";

        if (timeText == null || timeText.isBlank())
            return "Please enter time in HH:mm format (example: 18:30).";

        LocalTime time;
        try {
            time = LocalTime.parse(timeText.trim(), TIME_FMT);
        } catch (DateTimeParseException ex) {
            return "Invalid time. Use 24-hour format HH:mm (example: 09:05).";
        }

        LocalDateTime deliveredAt = LocalDateTime.of(date, time);

        // Not before order time
        if (order.orderTime() != null && deliveredAt.isBefore(order.orderTime())) {
            return "Delivery time cannot be before the order time (" + Formatters.formatDateTime(order.orderTime())
                    + ").";
        }

        // Not in the future (allow small skew)
        LocalDateTime now = LocalDateTime.now().plusMinutes(5);
        if (deliveredAt.isAfter(now)) {
            return "Delivery time cannot be in the future.";
        }

        return null;
    }

    @FXML
    private void onRefresh() {
        loadAll();
    }

    @FXML
    private void onOpenProfile() {
        ProfileWindow.showAndWait(this::onRefresh);
    }

    @FXML
    private void onLogout() {
        com.groupxx.greengrocer.app.CartModel.get().clear(); // Clear cart when logging out
        SessionContext.clear();
        SceneRouter.setScene("/fxml/login.fxml");
    }
}
