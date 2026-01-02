package com.groupxx.greengrocer;

import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.util.Alerts;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class MainApp extends Application {
    private static final Logger LOG = Logger.getLogger(MainApp.class.getName());

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            LOG.log(Level.SEVERE, "Uncaught error in thread " + t.getName(), e);
            Platform.runLater(() -> Alerts.showError(
                    "Unexpected Error",
                    "The application hit an unexpected error but stayed alive.",
                    e.getClass().getSimpleName() + ": " + e.getMessage()));
        });

        try {
            DbAdapter.getInstance().testConnection();
            new UserDao().ensureUserTableAndSeed();
            new com.groupxx.greengrocer.dao.ProductDao().ensureProductTableAndSeed();
            new com.groupxx.greengrocer.dao.OrderDao().ensureOrderTables();
            new com.groupxx.greengrocer.dao.SettingsDao().ensureSettingsTableAndSeedDefaults();
            new com.groupxx.greengrocer.dao.CouponDao().ensureTableAndSeedDefaults();
            new com.groupxx.greengrocer.dao.MessageDao().ensureMessageTable();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Database initialization failed", ex);
            Alerts.showError(
                    "Database Connection Failed",
                    "Cannot connect to the database or initialize schema.",
                    ex.getMessage() + "\n\nPlease verify MySQL is running and the credentials are correct.");
            Platform.exit();
            return;
        }

        SceneRouter.init(stage);
        SceneRouter.showLogin();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
