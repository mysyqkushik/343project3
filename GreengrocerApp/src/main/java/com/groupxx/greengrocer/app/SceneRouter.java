package com.groupxx.greengrocer.app;

import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.model.Role;
import com.groupxx.greengrocer.util.Alerts;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

public final class SceneRouter {
    private static Stage stage;

    private SceneRouter() {
    }

    public static void init(Stage primaryStage) {
        stage = primaryStage;
        stage.setMinWidth(AppConfig.WINDOW_W);
        stage.setMinHeight(AppConfig.WINDOW_H);
        stage.setWidth(AppConfig.WINDOW_W);
        stage.setHeight(AppConfig.WINDOW_H);
        stage.setResizable(true);
    }

    public static void showLogin() {
        SessionContext.clear();
        setScene("/fxml/login.fxml", "Login - " + AppConfig.APP_TITLE);
    }

    public static void showRegister() {
        setScene("/fxml/register.fxml", "Register - " + AppConfig.APP_TITLE);
    }

    public static void showRoleHome(Role role, String username) {
        SessionContext.set(username, role);
        switch (role) {
            case CUSTOMER -> setScene("/fxml/customer_home.fxml", AppConfig.APP_TITLE);
            case CARRIER -> setScene("/fxml/carrier_home.fxml", "Carrier - " + AppConfig.APP_TITLE);
            case OWNER -> setScene("/fxml/owner_home.fxml", "Owner - " + AppConfig.APP_TITLE);
        }
    }

    public static void setScene(String fxmlPath) {
        setScene(fxmlPath, null);
    }

    private static void setScene(String fxmlPath, String title) {
        if (stage == null) {
            Alerts.error("Navigation Error", "Main window is not ready.");
            return;
        }
        try {
            URL fxmlUrl = SceneRouter.class.getResource(fxmlPath);
            if (fxmlUrl == null) {
                Alerts.error("Navigation Error", "Missing screen resource: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Scene scene = new Scene(loader.load(), AppConfig.WINDOW_W, AppConfig.WINDOW_H);
            URL cssUrl = SceneRouter.class.getResource("/css/app.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            } else {
                Alerts.warn("Stylesheet Missing", "Could not load /css/app.css.");
            }
            stage.setTitle(title);
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            Alerts.unexpected("Failed to load screen: " + fxmlPath, e);
        }
    }
}
