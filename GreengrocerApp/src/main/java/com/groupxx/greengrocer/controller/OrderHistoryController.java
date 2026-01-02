package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.model.OrderSummaryRecord;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;

import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderHistoryController {
    private static final Logger LOG = Logger.getLogger(OrderHistoryController.class.getName());

    @FXML
    private Label ruleLabel;

    @FXML
    private TableView<OrderSummaryRecord> ordersTable;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colId;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colOrderTime;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colReqDelivery;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colStatus;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colTotal;
    @FXML
    private TableColumn<OrderSummaryRecord, String> colCarrier;
    @FXML
    private TableColumn<OrderSummaryRecord, Void> colAction;

    private final OrderDao orderDao = new OrderDao();

    @FXML
    private void initialize() {
        ruleLabel.setText("Cancel rule: You can cancel within " + AppConfig.CANCEL_WINDOW_MINUTES
                + " minutes after order time, only if not delivered and not accepted by a carrier.");

        colId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().orderId())));
        colOrderTime.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatDateTime(cd.getValue().orderTime())));
        colReqDelivery.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatDateTime(cd.getValue().requestedDeliveryTime())));
        colStatus.setCellValueFactory(cd -> new SimpleStringProperty(statusText(cd.getValue())));
        colTotal.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatMoney(cd.getValue().totalInclVat())));
        colCarrier.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().carrierUsername() == null ? "-" : cd.getValue().carrierUsername()));

        colAction.setCellFactory(tc -> new TableCell<>() {
            private final Button cancelBtn = new Button("Cancel");
            private final Button invoiceBtn = new Button("Invoice");
            private final Button rateBtn = new Button("Rate");
            private final HBox box = new HBox(6, cancelBtn, invoiceBtn, rateBtn);

            {
                box.setAlignment(Pos.CENTER_LEFT);

                cancelBtn.setOnAction(e -> {
                    OrderSummaryRecord row = getTableView().getItems().get(getIndex());
                    onCancelRow(row);
                });

                invoiceBtn.setOnAction(e -> {
                    OrderSummaryRecord row = getTableView().getItems().get(getIndex());
                    onSaveInvoice(row);
                });

                rateBtn.setOnAction(e -> {
                    OrderSummaryRecord row = getTableView().getItems().get(getIndex());
                    onRateCarrier(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                OrderSummaryRecord row = getTableView().getItems().get(getIndex());
                cancelBtn.setDisable(!isCancelable(row));
                invoiceBtn.setDisable(false);
                rateBtn.setDisable(!(row.delivered() && !row.canceled() && row.carrierId() != null));
                setGraphic(box);
            }
        });

        load();
    }

    private void load() {
        try {
            String customerUsername = SessionContext.requireUsername();
            List<OrderSummaryRecord> list = orderDao.listOrdersForCustomerUsername(customerUsername);
            ordersTable.getItems().setAll(list);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load order history", e);
            Alerts.unexpected("Failed to load order history.", e);
        }
    }

    private void onCancelRow(OrderSummaryRecord row) {
        if (!isCancelable(row)) {
            Alerts.warn("Not Allowed", "This order cannot be canceled by rule.");
            return;
        }
        boolean ok = Alerts.confirm("Cancel Order",
                "Cancel order #" + row.orderId() + "?\n\nThis will restore product stocks.");
        if (!ok)
            return;

        try {
            String customerUsername = SessionContext.requireUsername();
            boolean canceled = orderDao.cancelOrderByUsername(customerUsername, row.orderId(),
                    AppConfig.CANCEL_WINDOW_MINUTES);
            if (canceled)
                Alerts.info("Canceled", "Order canceled successfully.");
            else
                Alerts.warn("Failed",
                        "Cancel failed (maybe accepted by carrier / time window expired / already delivered).");
            load();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cancel operation failed for order=" + row.orderId(), e);
            Alerts.unexpected("Cancel operation failed.", e);
        }
    }

    private boolean isCancelable(OrderSummaryRecord o) {
        if (o.canceled() || o.delivered())
            return false;
        if (o.carrierId() != null)
            return false;
        if (o.orderTime() == null)
            return false;
        Duration d = Duration.between(o.orderTime(), LocalDateTime.now());
        return !d.isNegative() && d.toMinutes() <= AppConfig.CANCEL_WINDOW_MINUTES;
    }

    private void onSaveInvoice(OrderSummaryRecord row) {
        try {
            String customerUsername = SessionContext.requireUsername();
            byte[] pdf = orderDao.loadInvoicePdfForCustomerUsername(customerUsername, row.orderId());
            if (pdf == null || pdf.length == 0) {
                Alerts.warn("Not Available", "Invoice PDF is not available for this order.");
                return;
            }

            FileChooser fc = new FileChooser();
            fc.setTitle("Save Invoice PDF");
            fc.setInitialFileName("invoice-order-" + row.orderId() + ".pdf");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
            File f = fc.showSaveDialog(ordersTable.getScene().getWindow());
            if (f == null)
                return;
            Files.write(f.toPath(), pdf);
            Alerts.info("Saved", "Invoice saved: " + f.getAbsolutePath());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to save invoice", e);
            Alerts.unexpected("Failed to save invoice.", e);
        }
    }

    private void onRateCarrier(OrderSummaryRecord row) {
        try {
            String customerUsername = SessionContext.requireUsername();

            Integer existing = orderDao.getCarrierRatingForCustomerUsername(customerUsername, row.orderId());
            if (existing != null) {
                Alerts.info("Already Rated", "You already rated this delivery: " + existing + "/5");
                return;
            }

            ChoiceDialog<Integer> ratingDlg = new ChoiceDialog<>(5, 1, 2, 3, 4, 5);
            ratingDlg.setTitle("Rate Carrier");
            ratingDlg.setHeaderText("Rate the carrier for order #" + row.orderId());
            ratingDlg.setContentText("Rating (1-5):");
            var opt = ratingDlg.showAndWait();
            if (opt.isEmpty())
                return;

            int rating = opt.get();

            // Custom dialog with TextArea for comment (larger, with character limit)
            Dialog<String> commentDlg = new Dialog<>();
            commentDlg.setTitle("Rate Carrier");
            commentDlg.setHeaderText("Optional comment (max 255 characters)");

            // Create TextArea with character limit
            TextArea commentArea = new TextArea();
            commentArea.setPromptText("Enter your comment here...");
            commentArea.setWrapText(true);
            commentArea.setPrefRowCount(5);
            commentArea.setPrefColumnCount(40);

            // Enforce 255 character limit
            com.groupxx.greengrocer.util.TextLimiters.limitLength(commentArea, 255);

            // Character counter label
            Label charCounter = new Label("0 / 255");
            charCounter.setStyle("-fx-text-fill: rgba(0,0,0,0.6);");
            commentArea.textProperty().addListener((obs, oldVal, newVal) -> {
                int len = newVal == null ? 0 : newVal.length();
                charCounter.setText(len + " / 255");
                if (len >= 255) {
                    charCounter.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
                } else {
                    charCounter.setStyle("-fx-text-fill: rgba(0,0,0,0.6);");
                }
            });

            javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(8, commentArea, charCounter);
            content.setPadding(new javafx.geometry.Insets(10));
            commentDlg.getDialogPane().setContent(content);
            commentDlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            commentDlg.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return commentArea.getText();
                }
                return null;
            });

            var commentOpt = commentDlg.showAndWait();
            String comment = commentOpt.orElse("");

            boolean ok = orderDao.rateCarrierByCustomerUsername(customerUsername, row.orderId(), rating, comment);
            if (ok)
                Alerts.info("Thank you", "Rating submitted.");
            else
                Alerts.warn("Not Allowed",
                        "Cannot rate this order (maybe not delivered / already rated / no carrier).");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Rating failed", e);
            Alerts.unexpected("Rating failed.", e);
        }
    }

    private String statusText(OrderSummaryRecord o) {
        if (o.canceled())
            return "CANCELED";
        if (o.delivered())
            return "DELIVERED";
        if (o.carrierId() != null)
            return "IN DELIVERY";
        return "PENDING";
    }

    @FXML
    private void onRefresh() {
        load();
    }

    @FXML
    private void onClose() {
        Stage st = (Stage) ordersTable.getScene().getWindow();
        st.close();
    }
}
