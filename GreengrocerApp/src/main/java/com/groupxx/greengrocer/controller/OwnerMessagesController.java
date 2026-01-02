package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.MessageDao;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.MessageRecord;
import com.groupxx.greengrocer.model.Role;
import com.groupxx.greengrocer.model.UserRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import com.groupxx.greengrocer.util.Validators;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;
import javafx.util.Duration;

import java.util.List;

/** Owner ↔ Customer messaging (reply as OWNER). */
public final class OwnerMessagesController {

    @FXML
    private ComboBox<UserRecord> customerCombo;

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

    private static final UserRecord ALL_USERS = new UserRecord(-1, "All Users", Role.CUSTOMER, null, null, null);

    private Timeline pendingSendTimeline;
    private int pendingSecondsLeft;
    private long pendingCustomerId;
    private String pendingText;

    @FXML
    public void initialize() {
        customerCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(UserRecord u) {
                return u == null ? "" : u.username();
            }

            @Override
            public UserRecord fromString(String s) {
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
        UserRecord customer = customerCombo.getSelectionModel().getSelectedItem();
        if (customer == null) {
            Alerts.showWarn("No customer selected", "Please select a customer.", "");
            return;
        }
        if (customer.id() == ALL_USERS.id()) {
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
            List<UserRecord> customers = new java.util.ArrayList<>();
            customers.add(ALL_USERS);
            customers.addAll(userDao.listCustomers());
            customerCombo.setItems(FXCollections.observableArrayList(customers));
            if (!customers.isEmpty() && customerCombo.getSelectionModel().getSelectedItem() == null) {
                customerCombo.getSelectionModel().select(0); // All Users by default
            }
        } catch (Exception ex) {
            Alerts.showError("Load Failed", "Cannot load customer list.", ex.getMessage());
        }
    }

    private void refreshConversation() {
        UserRecord customer = customerCombo.getSelectionModel().getSelectedItem();
        boolean all = (customer != null && customer.id() == ALL_USERS.id());

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

        UserRecord sel = customerCombo.getSelectionModel().getSelectedItem();
        boolean all = (sel != null && sel.id() == ALL_USERS.id());
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

            UserRecord sel = customerCombo.getSelectionModel().getSelectedItem();
            boolean all = (sel != null && sel.id() == ALL_USERS.id());
            sendBtn.setDisable(all || sel == null);
        }
    }

}
