package com.groupxx.greengrocer.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class CartWindow {
    private static final Logger LOG = Logger.getLogger(CartWindow.class.getName());

    private CartWindow() {
    }

    public static void showAndWait(Runnable onCloseRefresh) {
        try {
            FXMLLoader loader = new FXMLLoader(CartWindow.class.getResource("/fxml/cart.fxml"));
            Scene scene = new Scene(loader.load(), 760, 520);

            CartController c = loader.getController();
            c.setOnSuccessRefresh(onCloseRefresh);

            Stage st = new Stage();
            st.setTitle("Cart");
            st.initModality(Modality.APPLICATION_MODAL);
            st.setScene(scene);
            st.centerOnScreen();
            st.showAndWait();

            // Always refresh the main view when cart window closes
            // This updates the cart count button whether user cleared, removed items, or
            // just closed
            if (onCloseRefresh != null) {
                onCloseRefresh.run();
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open cart window", ex);
            com.groupxx.greengrocer.util.Alerts.showError("Cart Error", "Cannot open cart window.", ex.getMessage());
        }
    }
}
