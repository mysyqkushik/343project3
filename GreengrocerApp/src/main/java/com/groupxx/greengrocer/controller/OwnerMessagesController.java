package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.MessageDao;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.CustomerSummaryRecord;
import com.groupxx.greengrocer.model.MessageRecord;
import com.groupxx.greengrocer.model.Role;
import com.groupxx.greengrocer.model.UserRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Owner ↔ Customer messaging (reply as OWNER). */
public final class OwnerMessagesController {

    @FXML
    private ComboBox<String> filterTypeCombo;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<CustomerSummaryRecord> customerCombo;

    @FXML
    private TableView<MessageRecord> messageTable;
    @FXML
    private TableColumn<MessageRecord, String> timeCol;
    @FXML
    private TableColumn<MessageRecord, String> fromCol;
    @FXML
    private TableColumn<MessageRecord, String> textCol;

    @FXML
    private TextArea messageInput;
    @FXML
    private Button sendBtn;
    @FXML
    private Button cancelSendBtn;
    @FXML
    private Label sendTimerLabel;

    private final UserDao userDao = new UserDao();
    private final MessageDao messageDao = new MessageDao();

    // "All Users" placeholder
    private static final CustomerSummaryRecord ALL_USERS = new CustomerSummaryRecord(
            new UserRecord(-1, "All Users", Role.CUSTOMER, null, null, null), 0);

    private Timeline pendingSendTimeline;
    private int pendingSecondsLeft;
    private long pendingCustomerId;
    private String pendingText;

    private List<CustomerSummaryRecord> fullCustomerList = new ArrayList<>();

    @FXML
    public void initialize() {
        // Setup filter types
        filterTypeCombo.setItems(FXCollections.observableArrayList("Name", "ID", "Orders"));
        filterTypeCombo.getSelectionModel().select(0);
        filterTypeCombo.setOnAction(e -> applyFilter());
        searchField.textProperty().addListener((o, ov, nv) -> applyFilter());

        customerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(CustomerSummaryRecord r) {
                if (r == null)
                    return "";
                if (r.user().id() == -1)
                    return "All Users";
                return r.user().username() + " (ID:" + r.user().id() + ", Ords:" + r.orderCount() + ")";
            }

            @Override
            public CustomerSummaryRecord fromString(String s) {
                return null;
            }
        });

        timeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                Formatters.fmtDateTime(cd.getValue().createdAt())));
        fromCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().senderRole().name()));
        textCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().messageText()));

        setupCancelUi();

        customerCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldV, newV) -> refreshConversation());
        reloadCustomers();
    }

    /** Called by OwnerHomeController "Refresh" button. */
    public void reload() {
        reloadCustomers();
        refreshConversation();
    }

    @FXML
    public void onSend() {
        CustomerSummaryRecord summary = customerCombo.getSelectionModel().getSelectedItem();
        UserRecord customer = (summary == null) ? null : summary.user();

        if (customer == null) {
            Alerts.showWarn("No customer selected", "Please select a customer.", "");
            return;
        }
        if (customer.id() == ALL_USERS.user().id()) {
            Alerts.showWarn("All Users view", "Select a specific customer to reply.", "");
            return;
        }

        String text = messageInput.getText();
        if (text == null)
            text = "";
        text = text.trim();
        if (text.isEmpty()) {
            Alerts.showWarn("Empty message", "Message cannot be empty.", "");
            return;
        }

        // normalize spaces/newlines and limit length
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 500)
            text = text.substring(0, 500);

        startPendingSend(customer.id(), text);
    }

    private void reloadCustomers() {
        try {
            fullCustomerList.clear();
            fullCustomerList.add(ALL_USERS);
            fullCustomerList.addAll(userDao.listCustomersWithOrderCounts());
            applyFilter(); // This will populate customerCombo
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load customer list.", ex.getMessage());
        }
    }

    private void applyFilter() {
        String filterType = filterTypeCombo.getValue();
        String query = searchField.getText();

        if (query == null)
            query = "";
        query = query.trim().toLowerCase();

        List<CustomerSummaryRecord> filtered = new ArrayList<>();

        if (query.isEmpty()) {
            filtered.addAll(fullCustomerList);
        } else {
            // Always include "All Users" if it matches specific criteria or just keep it at
            // top?
            // Usually search implies strict filtering, so we might exclude "All Users" if
            // it doesn't match query.
            // But let's keep "All Users" only if query is empty or explicitly searching for
            // "All".
            // Implementation decision: Search strictly filters the list.

            for (CustomerSummaryRecord rec : fullCustomerList) {
                if (rec == ALL_USERS)
                    continue; // Skip ALL_USERS during search iteration

                boolean match = false;
                UserRecord u = rec.user();

                if ("Name".equals(filterType)) {
                    if (u.username().toLowerCase().contains(query))
                        match = true;
                } else if ("ID".equals(filterType)) {
                    if (String.valueOf(u.id()).contains(query))
                        match = true;
                } else if ("Orders".equals(filterType)) {
                    // Filter by order count >= query or exact match?
                    // Let's do exact match or "greater than" if query starts with >
                    // Simple approach: string contains (flexibility)
                    if (String.valueOf(rec.orderCount()).contains(query))
                        match = true;
                }

                if (match) {
                    filtered.add(rec);
                }
            }
        }

        // Preserve selection if possible
        CustomerSummaryRecord selected = customerCombo.getSelectionModel().getSelectedItem();

        customerCombo.setItems(FXCollections.observableArrayList(filtered));

        if (selected != null && filtered.contains(selected)) {
            customerCombo.getSelectionModel().select(selected);
        } else if (!filtered.isEmpty()) {
            customerCombo.getSelectionModel().select(0);
        }
    }

    private void refreshConversation() {
        CustomerSummaryRecord summary = customerCombo.getSelectionModel().getSelectedItem();
        UserRecord customer = (summary == null) ? null : summary.user();
        boolean all = (customer != null && customer.id() == ALL_USERS.user().id());

        // disable reply UI in "All Users" mode
        messageInput.setDisable(all || customer == null);
        sendBtn.setDisable(all || customer == null);

        if (all) {
            messageInput.setPromptText("Select a user to reply…");
        } else {
            messageInput.setPromptText("Type a message…");
        }

        try {
            List<MessageRecord> msgs = all ? messageDao.listAllMessagesForOwner()
                    : (customer == null ? java.util.List.of()
                            : messageDao.listConversationForCustomerId(customer.id()));

            messageTable.setItems(FXCollections.observableArrayList(msgs));

            // Show customer username inline when viewing all users
            fromCol.setCellValueFactory(cd -> {
                MessageRecord m = cd.getValue();
                String s = m.senderRole().name();
                if (all)
                    s = s + " (" + m.customerUsername() + ")";
                return new javafx.beans.property.SimpleStringProperty(s);
            });

        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load messages.", ex.getMessage());
        }
    }

    private void setupCancelUi() {
        if (sendTimerLabel != null) {
            sendTimerLabel.setText("");
            sendTimerLabel.setVisible(false);
            sendTimerLabel.setManaged(false);
            sendTimerLabel.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
        }
        if (cancelSendBtn != null) {
            cancelSendBtn.setVisible(false);
            cancelSendBtn.setManaged(false);
        }
    }

    @FXML
    public void onCancelSend() {
        cancelPendingSend();
    }

    private void startPendingSend(long customerId, String text) {
        if (pendingSendTimeline != null) {
            Alerts.showWarn("Sending in progress", "Please wait or cancel the current sending first.", "");
            return;
        }

        pendingCustomerId = customerId;
        pendingText = text;
        pendingSecondsLeft = com.groupxx.greengrocer.config.AppConfig.CANCEL_SEND_SECONDS;

        sendBtn.setDisable(true);
        customerCombo.setDisable(true);

        if (sendTimerLabel != null) {
            sendTimerLabel.setVisible(true);
            sendTimerLabel.setManaged(true);
            sendTimerLabel.setText("Sending in " + pendingSecondsLeft + "s");
        }
        if (cancelSendBtn != null) {
            cancelSendBtn.setVisible(true);
            cancelSendBtn.setManaged(true);
        }

        pendingSendTimeline = new Timeline(new KeyFrame(Duration.seconds(1), ev -> {
            pendingSecondsLeft--;
            if (sendTimerLabel != null) {
                sendTimerLabel.setText("Sending in " + Math.max(pendingSecondsLeft, 0) + "s");
            }
            if (pendingSecondsLeft <= 0) {
                finishPendingSend();
            }
        }));
        pendingSendTimeline.setCycleCount(pendingSecondsLeft);
        pendingSendTimeline.play();
    }

    private void cancelPendingSend() {
        if (pendingSendTimeline != null) {
            pendingSendTimeline.stop();
            pendingSendTimeline = null;
        }

        pendingCustomerId = 0;
        pendingText = null;
        pendingSecondsLeft = 0;

        if (sendTimerLabel != null) {
            sendTimerLabel.setVisible(false);
            sendTimerLabel.setManaged(false);
            sendTimerLabel.setText("");
        }
        if (cancelSendBtn != null) {
            cancelSendBtn.setVisible(false);
            cancelSendBtn.setManaged(false);
        }

        customerCombo.setDisable(false);

        CustomerSummaryRecord summary = customerCombo.getSelectionModel().getSelectedItem();
        UserRecord sel = (summary != null) ? summary.user() : null;
        boolean all = (sel != null && sel.id() == ALL_USERS.user().id());
        sendBtn.setDisable(all || sel == null);

        Alerts.showInfo("Canceled", "Message sending canceled.", "");
    }

    private void finishPendingSend() {
        if (pendingSendTimeline != null) {
            pendingSendTimeline.stop();
            pendingSendTimeline = null;
        }

        try {
            messageDao.sendFromOwner(pendingCustomerId, pendingText);
            messageInput.clear();
            refreshConversation();
        } catch (Exception ex) {
            Alerts.showError("Send Failed", "Cannot send message.", ex.getMessage());
        } finally {
            pendingCustomerId = 0;
            pendingText = null;
            pendingSecondsLeft = 0;

            if (sendTimerLabel != null) {
                sendTimerLabel.setVisible(false);
                sendTimerLabel.setManaged(false);
                sendTimerLabel.setText("");
            }
            if (cancelSendBtn != null) {
                cancelSendBtn.setVisible(false);
                cancelSendBtn.setManaged(false);
            }

            customerCombo.setDisable(false);

            CustomerSummaryRecord summary = customerCombo.getSelectionModel().getSelectedItem();
            UserRecord sel = (summary != null) ? summary.user() : null;
            boolean all = (sel != null && sel.id() == ALL_USERS.user().id());
            sendBtn.setDisable(all || sel == null);
        }
    }
}
