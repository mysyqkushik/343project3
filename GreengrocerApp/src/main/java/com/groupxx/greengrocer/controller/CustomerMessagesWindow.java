package com.groupxx.greengrocer.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class CustomerMessagesWindow {
    private static final Logger LOG = Logger.getLogger(CustomerMessagesWindow.class.getName());
    private CustomerMessagesWindow() {}

    public static void showAndWait() {
        try {
            FXMLLoader loader = new FXMLLoader(CustomerMessagesWindow.class.getResource("/fxml/customer_messages.fxml"));
            Scene scene = new Scene(loader.load(), 720, 520);

            Stage st = new Stage();
            st.setTitle("Messages (Customer ↔ Owner)");
            st.initModality(Modality.APPLICATION_MODAL);
            st.setScene(scene);
            st.centerOnScreen();
            st.showAndWait();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open messages window", ex);
            com.groupxx.greengrocer.util.Alerts.showError("Messages Error", "Cannot open messages window.", ex.getMessage());
        }
    }
}
