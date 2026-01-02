package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.util.Alerts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileWindow {
    private static final Logger LOG = Logger.getLogger(ProfileWindow.class.getName());
    private ProfileWindow() {}

    public static void showAndWait(Runnable onClose) {
        try {
            FXMLLoader loader = new FXMLLoader(ProfileWindow.class.getResource("/fxml/profile.fxml"));
            Scene scene = new Scene(loader.load(), 520, 360);

            Stage st = new Stage();
            st.setTitle(AppConfig.APP_TITLE + " - Profile");
            st.initModality(Modality.APPLICATION_MODAL);
            st.setScene(scene);
            st.centerOnScreen();
            st.setOnHidden(e -> { if (onClose != null) onClose.run(); });
            st.showAndWait();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to open profile window", ex);
            Alerts.showError("Profile Error", "Cannot open profile window.", ex.getMessage());
        }
    }
}
