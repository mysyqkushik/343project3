package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.CarrierSummaryRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Validators;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;

import java.util.List;

public final class OwnerCarriersController {

    @FXML
    private TableView<CarrierSummaryRecord> carriersTable;
    @FXML
    private TableColumn<CarrierSummaryRecord, String> usernameCol;
    @FXML
    private TableColumn<CarrierSummaryRecord, Boolean> activeCol;
    @FXML
    private TableColumn<CarrierSummaryRecord, String> avgRatingCol;

    @FXML
    private TableView<CarrierSummaryRecord> deletedTable;
    @FXML
    private TableColumn<CarrierSummaryRecord, String> deletedUsernameCol;
    @FXML
    private TableColumn<CarrierSummaryRecord, String> deletedAvgRatingCol;

    @FXML
    private Label statusLabel;

    private final UserDao userDao = new UserDao();
    private final com.groupxx.greengrocer.dao.OrderDao orderDao = new com.groupxx.greengrocer.dao.OrderDao();

    @FXML
    private void initialize() {
        usernameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().username()));
        activeCol.setCellValueFactory(cd -> new SimpleBooleanProperty(cd.getValue().active()));
        activeCol.setCellFactory(CheckBoxTableCell.forTableColumn(activeCol));
        avgRatingCol.setCellValueFactory(cd -> {
            Double v = cd.getValue().avgRating();
            return new SimpleStringProperty(v == null ? "-" : String.format("%.2f", v));
        });

        deletedUsernameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().username()));
        deletedAvgRatingCol.setCellValueFactory(cd -> {
            Double v = cd.getValue().avgRating();
            return new SimpleStringProperty(v == null ? "-" : String.format("%.2f", v));
        });

        onRefresh();
    }

    @FXML
    private void onRefresh() {
        try {
            List<CarrierSummaryRecord> active = userDao.listCarriersWithAvgRating();
            carriersTable.getItems().setAll(active);

            List<CarrierSummaryRecord> deleted = userDao.listDeletedCarriersWithAvgRating();
            deletedTable.getItems().setAll(deleted);

            statusLabel.setText("Loaded carriers: active=" + active.size() + ", deleted=" + deleted.size());
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load carriers.", ex.getMessage());
        }
    }

    @FXML
    private void onAddCarrier() {
        TextInputDialog userDlg = new TextInputDialog();
        userDlg.setTitle("Add Carrier");
        userDlg.setHeaderText("Enter carrier username:");
        userDlg.setContentText("Username:");

        var uOpt = userDlg.showAndWait();
        if (uOpt.isEmpty())
            return;

        String username = Validators.normalize(uOpt.get());
        if (username.isBlank()) {
            Alerts.showWarn("Invalid Username", "Username cannot be empty.", "");
            return;
        }
        if (!username.matches("[a-zA-Z0-9._-]{3,32}")) {
            Alerts.showWarn("Invalid Username",
                    "Use 3-32 chars: letters, digits, dot, underscore, dash.",
                    "");
            return;
        }

        // Strong password dialog (same rules as RegisterController)
        Dialog<String> pwDlg = new Dialog<>();
        pwDlg.setTitle("Add Carrier");
        pwDlg.setHeaderText("Set a strong password for the new carrier.");

        ButtonType okType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        pwDlg.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        PasswordField pwField = new PasswordField();
        pwField.setPromptText("Password");

        Label rules = new Label("Rules: ≥8 chars, upper + lower case, digit, special char.");
        rules.setStyle("-fx-text-fill: rgba(0,0,0,0.70);");

        pwDlg.getDialogPane().setContent(new VBox(10, pwField, rules));

        Node okBtn = pwDlg.getDialogPane().lookupButton(okType);
        okBtn.setDisable(true);

        pwField.textProperty().addListener((obs, ov, nv) -> okBtn.setDisable(!Validators.isStrongPassword(nv)));

        pwDlg.setResultConverter(bt -> bt == okType ? pwField.getText() : null);

        String password = pwDlg.showAndWait().orElse(null);
        if (password == null)
            return;

        try {
            userDao.createCarrier(username, password.toCharArray());
            statusLabel.setText("Carrier created: " + username);
            onRefresh();
        } catch (Exception ex) {
            Alerts.showError("Create Failed", "Cannot create carrier.", ex.getMessage());
        }
    }

    @FXML
    private void onToggleActive() {
        CarrierSummaryRecord rec = carriersTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            Alerts.showWarn("No selection", "Select a carrier first.", "");
            return;
        }

        // If trying to deactivate, check for active orders first
        if (rec.active()) {
            try {
                int activeOrders = orderDao.countActiveOrdersForCarrierId(rec.id());
                if (activeOrders > 0) {
                    Alerts.showWarn("Cannot Deactivate",
                            "This carrier has " + activeOrders + " active delivery(ies) in progress.",
                            "Wait until all deliveries are completed before deactivating.");
                    return;
                }
            } catch (Exception ex) {
                Alerts.showError("Check Failed", "Cannot check carrier orders.", ex.getMessage());
                return;
            }
        }

        try {
            userDao.setUserActive(rec.id(), !rec.active());
            statusLabel.setText("Carrier " + rec.username() + " is now " + (!rec.active() ? "ACTIVE" : "INACTIVE"));
            onRefresh();
        } catch (Exception ex) {
            Alerts.showError("Update Failed", "Cannot change carrier active state.", ex.getMessage());
        }
    }

    @FXML
    private void onDeleteCarrier() {
        CarrierSummaryRecord rec = carriersTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            Alerts.showWarn("No selection", "Select a carrier first.", "");
            return;
        }

        boolean ok = Alerts.confirm("Delete Carrier",
                "Soft-delete carrier '" + rec.username() + "'?",
                "Orders will remain, but this carrier will be removed from active lists until you undo deletion.");
        if (!ok)
            return;

        try {
            userDao.deleteCarrier(rec.id());
            statusLabel.setText("Deleted carrier: " + rec.username());
            onRefresh();
        } catch (Exception ex) {
            Alerts.showError("Delete Failed", "Cannot delete carrier.", ex.getMessage());
        }
    }

    @FXML
    private void onUndoDeletion() {
        CarrierSummaryRecord rec = deletedTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            Alerts.showWarn("No selection", "Select a deleted carrier first.", "");
            return;
        }

        try {
            userDao.undoDeleteCarrier(rec.id());
            statusLabel.setText("Restored carrier: " + rec.username());
            onRefresh();
        } catch (Exception ex) {
            Alerts.showError("Undo Failed", "Cannot restore carrier.", ex.getMessage());
        }
    }

    /**
     * Used by OwnerHomeController's global Refresh button (fx:include child
     * controller).
     */
    public void reload() {
        onRefresh();
    }
}
