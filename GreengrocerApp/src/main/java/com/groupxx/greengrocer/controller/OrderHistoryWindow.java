package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.config.AppConfig;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import com.groupxx.greengrocer.util.Alerts;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class OrderHistoryWindow {
    private static final Logger LOG = Logger.getLogger(OrderHistoryWindow.class.getName());
    private OrderHistoryWindow() {}

    public static void open(Stage ownerStage) {
        try {
            FXMLLoader loader = new FXMLLoader(OrderHistoryWindow.class.getResource("/fxml/order_history.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initOwner(ownerStage);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle(AppConfig.APP_TITLE + " - Order History");
            stage.setScene(new Scene(root, 980, 520));
            stage.show();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to open order history window", e);
            Alerts.unexpected("Failed to open Order History window.", e);
        }
    }
}
