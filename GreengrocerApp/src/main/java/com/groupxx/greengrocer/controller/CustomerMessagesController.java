package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.dao.MessageDao;
import com.groupxx.greengrocer.model.MessageRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.util.List;

/** Customer ↔ Owner messaging with 10-second "cancel sending" buffer. */
public final class CustomerMessagesController {

    @FXML private TableView<MessageRecord> messageTable;
    @FXML private TableColumn<MessageRecord, String> timeCol;
    @FXML private TableColumn<MessageRecord, String> fromCol;
    @FXML private TableColumn<MessageRecord, String> textCol;

    @FXML private TextArea messageInput;
    @FXML private Button sendBtn;
    @FXML private Button cancelSendBtn;
    @FXML private Label sendTimerLabel;
    @FXML private Button refreshBtn;

    private final MessageDao dao = new MessageDao();

    private Timeline pendingSendTimeline;
    private int pendingSecondsLeft;
    private String pendingText;

    @FXML
    public void initialize() {
        timeCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                Formatters.fmtDateTime(cd.getValue().createdAt())
        ));
        fromCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().senderRole().name()
        ));
        textCol.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(
                cd.getValue().messageText()
        ));

        setupCancelUi();
        refresh();
    }

    @FXML
    public void onRefresh() {
        refresh();
    }

    @FXML
    public void onSend() {
        String text = messageInput.getText();
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) {
            Alerts.warn("Empty message", "Message cannot be empty.");
            return;
        }

        // Normalize whitespace and cap length.
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 500) text = text.substring(0, 500);

        startPendingSend(text);
    }

    @FXML
    private void onCancelSend() {
        cancelPendingSend();
    }

    // ---------------- internal helpers ----------------

    private void refresh() {
        try {
            List<MessageRecord> msgs = dao.listConversationForCustomerUsername(SessionContext.getUsername());
            messageTable.setItems(FXCollections.observableArrayList(msgs));
            if (!msgs.isEmpty()) {
                messageTable.scrollTo(msgs.size() - 1);
            }
        } catch (Exception ex) {
            Alerts.error("Load Failed", "Cannot load messages.\n" + ex.getMessage());
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

    private void startPendingSend(String text) {
        if (pendingSendTimeline != null) {
            Alerts.warn("Sending in progress", "Please wait or cancel the current send first.");
            return;
        }

        pendingText = text;
        pendingSecondsLeft = AppConfig.CANCEL_SEND_SECONDS;

        // Lock UI while buffered.
        sendBtn.setDisable(true);
        refreshBtn.setDisable(true);

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
        // Run for the original number of seconds.
        pendingSendTimeline.setCycleCount(AppConfig.CANCEL_SEND_SECONDS);
        pendingSendTimeline.play();
    }

    private void cancelPendingSend() {
        if (pendingSendTimeline != null) {
            pendingSendTimeline.stop();
            pendingSendTimeline = null;
        }
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

        sendBtn.setDisable(false);
        refreshBtn.setDisable(false);

        Alerts.info("Canceled", "Message sending canceled.");
    }

    private void finishPendingSend() {
        if (pendingSendTimeline != null) {
            pendingSendTimeline.stop();
            pendingSendTimeline = null;
        }

        try {
            // Commit to DB only after the buffer expires.
            dao.sendFromCustomerUsername(SessionContext.getUsername(), pendingText);
            messageInput.clear();
            refresh();
        } catch (Exception ex) {
            Alerts.error("Send Failed", "Cannot send message.\n" + ex.getMessage());
        } finally {
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

            sendBtn.setDisable(false);
            refreshBtn.setDisable(false);
        }
    }
}
