package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.UserRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.TextLimiters;
import com.groupxx.greengrocer.util.Validators;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LoginController {
    private static final Logger LOG = Logger.getLogger(LoginController.class.getName());

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;
    @FXML
    private Label statusLabel;

    /** Counter for failed login attempts to trigger temporary lockout. */
    private int failedAttempts = 0;

    /** Timestamp until which login is disabled if lockout is active. */
    private Instant lockedUntil = Instant.EPOCH;

    private final UserDao userDao = new UserDao();

    /**
     * Initializes the controller.
     * Sets up text limiters for input fields and clears the status label.
     */
    @FXML
    private void initialize() {
        TextLimiters.limitLength(usernameField, 32);
        TextLimiters.limitLength(passwordField, 64);
        statusLabel.setText("");
    }

    /**
     * Handles the login action triggered by the "Sign In" button or Enter key in
     * password field.
     * <p>
     * Validates input, checks for lockout, authenticates against the database,
     * and redirects to the appropriate home screen based on user role.
     * </p>
     */
    @FXML
    private void onLogin() {
        if (Instant.now().isBefore(lockedUntil)) {
            Alerts.showWarn("Temporarily Locked",
                    "Too many failed attempts.",
                    "Wait a few seconds and try again.");
            return;
        }

        String username = Validators.normalize(usernameField.getText());
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (!Validators.isValidUsername(username)) {
            statusLabel.setText("Invalid username (A-Z, a-z, 0-9, _, length 3..32).");
            return;
        }
        if (password.isBlank()) {
            statusLabel.setText("Password cannot be empty.");
            return;
        }

        setBusy(true);
        statusLabel.setText("Signing in...");

        new Thread(() -> {
            try {
                Optional<UserRecord> user = userDao.authenticate(username, password.toCharArray());
                Platform.runLater(() -> {
                    setBusy(false);
                    if (user.isPresent()) {
                        failedAttempts = 0;
                        statusLabel.setText("");
                        SceneRouter.showRoleHome(user.get().role(), user.get().username());
                    } else {
                        failedAttempts++;
                        statusLabel.setText("Invalid username or password.");
                        if (failedAttempts >= 5) {
                            lockedUntil = Instant.now().plusSeconds(15);
                            Alerts.showWarn("Locked",
                                    "Too many failed attempts.",
                                    "Login disabled for 15 seconds.");
                            failedAttempts = 0;
                        }
                    }
                });
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Login failed for username=" + username, ex);
                Platform.runLater(() -> {
                    setBusy(false);
                    statusLabel.setText("");
                    Alerts.showError("Login Failed", "Database error.", ex.getMessage());
                });
            }
        }, "login-thread").start();
    }

    /**
     * Opens the registration screen when "Create Account" is clicked.
     */
    @FXML
    private void onOpenRegister() {
        SceneRouter.showRegister();
    }

    /**
     * Disables or enables UI controls during async operations.
     *
     * @param busy true to disable controls (show busy state), false to enable.
     */
    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
    }
}
