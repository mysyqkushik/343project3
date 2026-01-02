package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.dao.UserDao;
import com.groupxx.greengrocer.model.UserRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.TextLimiters;
import com.groupxx.greengrocer.util.Validators;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.util.Optional;

public final class ProfileController {

    @FXML
    private Label userLabel;
    @FXML
    private TextField usernameField;
    @FXML
    private TextArea addressArea;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;
    @FXML
    private Label statusLabel;
    @FXML
    private Button saveBtn;

    private final UserDao userDao = new UserDao();
    private UserRecord current;

    @FXML
    private void initialize() {
        TextLimiters.limitLength(usernameField, 32);
        TextLimiters.limitLength(addressArea, 255);
        TextLimiters.limitLength(phoneField, 32);
        TextLimiters.limitLength(emailField, 96);

        load();
    }

    private void load() {
        try {
            String username = SessionContext.requireUsername();
            Optional<UserRecord> u = userDao.findByUsername(username);
            if (u.isEmpty()) {
                Alerts.showError("Profile", "Cannot load profile", "User not found.");
                close();
                return;
            }
            current = u.get();
            userLabel.setText("User: " + current.username() + " (" + current.role() + ")");
            usernameField.setText(current.username());
            addressArea.setText(current.address() == null ? "" : current.address());
            phoneField.setText(current.phone() == null ? "" : current.phone());
            emailField.setText(current.email() == null ? "" : current.email());
            statusLabel.setText("");
        } catch (Exception ex) {
            Alerts.showError("Profile", "Cannot load profile.", ex.getMessage());
            close();
        }
    }

    @FXML
    private void onSave() {
        if (current == null)
            return;
        try {
            String newUsername = Validators.normalize(usernameField.getText());
            String address = Validators.normalize(addressArea.getText());
            String phone = Validators.normalize(phoneField.getText());
            String email = Validators.normalize(emailField.getText());

            // Validate username
            if (!Validators.isValidUsername(newUsername)) {
                Alerts.showWarn("Invalid username", "Username format is incorrect.",
                        "3-32 characters, only A-Z, a-z, 0-9, _");
                return;
            }

            // Validate phone format (Turkish: +90XXXXXXXXXX or 0XXXXXXXXXX)
            if (!Validators.isReasonablePhone(phone)) {
                Alerts.showWarn("Invalid phone", "Phone format is incorrect.",
                        "Use Turkish format: +90XXXXXXXXXX or 0XXXXXXXXXX");
                return;
            }

            if (email != null && !email.isBlank() && !Validators.isValidEmail(email)) {
                Alerts.showWarn("Invalid email", "Email format looks incorrect.", "Example: name@example.com");
                return;
            }

            // Update username if changed
            if (!newUsername.equals(current.username())) {
                userDao.updateUsername(current.id(), newUsername);
                // Update session with new username
                SessionContext.setUsername(newUsername);
            }

            // Update profile fields
            userDao.updateProfile(current.id(),
                    address == null || address.isBlank() ? null : address,
                    phone == null || phone.isBlank() ? null : phone,
                    email == null || email.isBlank() ? null : email);

            // Reload and push to session
            current = userDao.findByUsername(newUsername).orElse(current);
            SessionContext.setUser(current);
            userLabel.setText("User: " + current.username() + " (" + current.role() + ")");

            statusLabel.setText("Saved.");
        } catch (IllegalArgumentException ex) {
            Alerts.showWarn("Save Failed", "Cannot update profile.", ex.getMessage());
        } catch (Exception ex) {
            Alerts.showError("Save Failed", "Cannot update profile.", ex.getMessage());
        }
    }

    @FXML
    private void onChangePassword() {
        if (current == null)
            return;

        // Create dialog for password change
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Enter your current password and new password");

        // Set up buttons
        ButtonType changeButtonType = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText("Current password");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");

        grid.add(new Label("Current Password:"), 0, 0);
        grid.add(currentPasswordField, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // Show dialog and handle result
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == changeButtonType) {
            String currentPass = currentPasswordField.getText();
            String newPass = newPasswordField.getText();
            String confirmPass = confirmPasswordField.getText();

            // Validate inputs
            if (currentPass == null || currentPass.isEmpty()) {
                Alerts.showWarn("Password Change", "Current password is required.", "");
                return;
            }

            if (!Validators.isStrongPassword(newPass)) {
                Alerts.showWarn("Password Change", "New password does not meet requirements.",
                        "8-64 characters, uppercase, lowercase, digit, special character, no spaces.");
                return;
            }

            if (!newPass.equals(confirmPass)) {
                Alerts.showWarn("Password Change", "New passwords do not match.", "");
                return;
            }

            try {
                // Verify current password
                if (!userDao.verifyPassword(current.id(), currentPass.toCharArray())) {
                    Alerts.showError("Password Change", "Incorrect current password.",
                            "Please enter your current password correctly.");
                    return;
                }

                // Update password
                userDao.updatePassword(current.id(), newPass.toCharArray());
                Alerts.showInfo("Password Changed", "Success", "Your password has been updated.");
                statusLabel.setText("Password changed.");
            } catch (Exception ex) {
                Alerts.showError("Password Change Failed", "Cannot update password.", ex.getMessage());
            }
        }
    }

    @FXML
    private void onCancel() {
        close();
    }

    private void close() {
        Stage st = (Stage) saveBtn.getScene().getWindow();
        st.close();
    }
}
