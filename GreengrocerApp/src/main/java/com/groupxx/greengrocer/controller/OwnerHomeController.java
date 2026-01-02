package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.dao.OrderDao;
import com.groupxx.greengrocer.model.OrderSummaryRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Owner Dashboard (Home) Screen.
 * <p>
 * Displays a global list of orders and acts as the parent container for other
 * owner sub-modules
 * (Products, Carriers, Messages, Discounts, Reports), which are included via
 * fx:include.
 * </p>
 */
public final class OwnerHomeController {
    private static final Logger LOG = Logger.getLogger(OwnerHomeController.class.getName());

    @FXML
    private TableView<OrderSummaryRecord> ordersTable;
    @FXML
    private TableColumn<OrderSummaryRecord, String> oId;
    @FXML
    private TableColumn<OrderSummaryRecord, String> oCustomer;
    @FXML
    private TableColumn<OrderSummaryRecord, String> oCarrier;
    @FXML
    private TableColumn<OrderSummaryRecord, String> oStatus;
    @FXML
    private TableColumn<OrderSummaryRecord, String> oTotal;

    // fx:include controller injections (fx:id + "Controller")
    @FXML
    private OwnerProductsController productsIncludeController;
    @FXML
    private OwnerCarriersController carriersIncludeController;
    @FXML
    private OwnerMessagesController messagesIncludeController;
    @FXML
    private OwnerDiscountsController discountsIncludeController;
    @FXML
    private OwnerReportsController reportsIncludeController;

    private final OrderDao orderDao = new OrderDao();

    /**
     * Initializes the controller.
     * Sets up the orders table columns and triggers the initial load of data.
     */
    @FXML
    private void initialize() {
        oId.setCellValueFactory(cd -> new SimpleStringProperty(String.valueOf(cd.getValue().orderId())));
        oCustomer.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().customerUsername()));
        oCarrier.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue().carrierUsername() == null ? "-" : cd.getValue().carrierUsername()));
        oStatus.setCellValueFactory(cd -> new SimpleStringProperty(statusText(cd.getValue())));
        oTotal.setCellValueFactory(
                cd -> new SimpleStringProperty(Formatters.formatMoney(cd.getValue().totalInclVat())));

        loadOrders();
        // Child controllers will self-initialize, but we also call reload to keep
        // "Refresh" consistent.
        reloadChildren();
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

    private void loadOrders() {
        try {
            ordersTable.getItems().setAll(orderDao.listAllOrders());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to load all orders", e);
            Alerts.unexpected("Failed to load all orders.", e);
        }
    }

    /**
     * Reloads data in all child sub-controllers (Products, Carriers, etc.).
     * Ensures that "Refresh" on the dashboard updates everything.
     */
    private void reloadChildren() {
        try {
            if (productsIncludeController != null)
                productsIncludeController.reload();
        } catch (Exception ignore) {
        }
        try {
            if (carriersIncludeController != null)
                carriersIncludeController.reload();
        } catch (Exception ignore) {
        }
        try {
            if (messagesIncludeController != null)
                messagesIncludeController.reload();
        } catch (Exception ignore) {
        }
        try {
            if (discountsIncludeController != null)
                discountsIncludeController.reload();
        } catch (Exception ignore) {
        }
        try {
            if (reportsIncludeController != null)
                reportsIncludeController.reload();
        } catch (Exception ignore) {
        }
    }

    @FXML
    private void onRefresh() {
        loadOrders();
        reloadChildren();
    }

    @FXML
    private void onOpenProfile() {
        ProfileWindow.showAndWait(this::reloadChildren);
    }

    @FXML
    private void onLogout() {
        com.groupxx.greengrocer.app.CartModel.get().clear(); // Clear cart when logging out
        SessionContext.clear();
        SceneRouter.setScene("/fxml/login.fxml");
    }
}
