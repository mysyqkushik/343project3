package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.TextLimiters;
import com.groupxx.greengrocer.util.Validators;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controller for the Registration Screen.
 * <p>
 * Handles new user account creation, including input validation and database
 * insertion.
 * </p>
 */
public final class RegisterController {
    private static final Logger LOG = Logger.getLogger(RegisterController.class.getName());

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField phoneField;

    @FXML
    private Button createButton;
    @FXML
    private Button backButton;
    @FXML
    private Label statusLabel;

    private final UserDao userDao = new UserDao();

    /**
     * Initializes the controller.
     * Sets up text limiters for input fields.
     */
    @FXML
    private void initialize() {
        TextLimiters.limitLength(usernameField, 32);
        TextLimiters.limitLength(passwordField, 64);
        TextLimiters.limitLength(confirmPasswordField, 64);
        TextLimiters.limitLength(addressField, 255);
        TextLimiters.limitLength(phoneField, 32);
        statusLabel.setText("");
    }

    @FXML
    private void onBack() {
        SceneRouter.showLogin();
    }

    /**
     * Attempts to create a new customer account.
     * <p>
     * Validates all input fields (username, password strength, address, phone).
     * If valid, creates the user in the database and redirects to the login screen.
     * </p>
     */
    @FXML
    private void onCreateAccount() {
        String username = Validators.normalize(usernameField.getText());
        String pass = passwordField.getText() == null ? "" : passwordField.getText();
        String pass2 = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();
        String address = Validators.normalize(addressField.getText());
        String phone = Validators.normalize(phoneField.getText());

        if (!Validators.isValidUsername(username)) {
            statusLabel.setText("Username must be 3..32 and only A-Z, a-z, 0-9, _");
            return;
        }
        if (!Validators.isStrongPassword(pass)) {
            statusLabel.setText("Password: 8..64, upper/lower/digit/special, no spaces.");
            return;
        }
        if (!pass.equals(pass2)) {
            statusLabel.setText("Passwords do not match.");
            return;
        }
        if (address.isBlank()) {
            statusLabel.setText("Address cannot be empty.");
            return;
        }
        if (!Validators.isReasonablePhone(phone)) {
            statusLabel.setText("Phone format: +90XXXXXXXXXX or 0XXXXXXXXXX");
            return;
        }

        setBusy(true);
        statusLabel.setText("Creating account...");

        new Thread(() -> {
            try {
                if (userDao.usernameExists(username)) {
                    Platform.runLater(() -> {
                        setBusy(false);
                        statusLabel.setText("Username already exists.");
                    });
                    return;
                }

                userDao.createCustomer(username, pass.toCharArray(), address, phone.isBlank() ? null : phone);

                Platform.runLater(() -> {
                    setBusy(false);
                    Alerts.showInfo("Account Created", "Registration successful.", "You can now log in.");
                    SceneRouter.showLogin();
                });
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Failed to create account for username=" + username, ex);
                Platform.runLater(() -> {
                    setBusy(false);
                    statusLabel.setText("");
                    Alerts.showError("Registration Failed", "Database error.", ex.getMessage());
                });
            }
        }, "register-thread").start();
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        backButton.setDisable(busy);
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        confirmPasswordField.setDisable(busy);
        addressField.setDisable(busy);
        phoneField.setDisable(busy);
    }
}
