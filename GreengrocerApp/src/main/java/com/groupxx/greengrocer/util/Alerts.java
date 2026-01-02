package com.groupxx.greengrocer.util;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import java.io.PrintWriter;
import java.io.StringWriter;


public final class Alerts {
    private Alerts() {}

    public static void info(String title, String message) {
        show(Alert.AlertType.INFORMATION, title, message, null);
    }

    public static void warn(String title, String message) {
        show(Alert.AlertType.WARNING, title, message, null);
    }

    /** Convenience overload with a header + content (some controllers use 3-arg calls). */
    public static void warn(String title, String header, String content) {
        showWithHeader(Alert.AlertType.WARNING, title, header, content, null);
    }

    /** Convenience overload with a header + content (some controllers use 3-arg calls). */
    public static void info(String title, String header, String content) {
        showWithHeader(Alert.AlertType.INFORMATION, title, header, content, null);
    }

    /** Convenience overload with a header + content (some controllers use 3-arg calls). */
    public static void error(String title, String header, String content) {
        showWithHeader(Alert.AlertType.ERROR, title, header, content, null);
    }

    public static void error(String title, String message) {
        show(Alert.AlertType.ERROR, title, message, null);
    }

    public static boolean confirm(String title, String message) {
        if (!Platform.isFxApplicationThread()) {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
            Platform.runLater(() -> {
                result.set(showConfirm(title, message));
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result.get();
        }

        return showConfirm(title, message);
    }

    /**
     * Confirmation dialog with header + content (backward compatible with 3-arg calls).
     */
    public static boolean confirm(String title, String header, String content) {
        if (!Platform.isFxApplicationThread()) {
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final java.util.concurrent.atomic.AtomicBoolean result = new java.util.concurrent.atomic.AtomicBoolean(false);
            Platform.runLater(() -> {
                result.set(showConfirm(title, header, content));
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return result.get();
        }

        return showConfirm(title, header, content);
    }

    private static boolean showConfirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yes, no);

        return alert.showAndWait().orElse(no) == yes;
    }

    private static boolean showConfirm(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(yes, no);

        return alert.showAndWait().orElse(no) == yes;
    }

    /** Used for unexpected exceptions. */
    public static void unexpected(String message, Throwable ex) {
        show(Alert.AlertType.ERROR, "Unexpected Error", message, ex);
    }

    private static void show(Alert.AlertType type, String title, String message, Throwable ex) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(type, title, message, ex));
            return;
        }

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        if (ex != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);
            String exceptionText = sw.toString();

            Label label = new Label("Details:");

            TextArea textArea = new TextArea(exceptionText);
            textArea.setEditable(false);
            textArea.setWrapText(false);

            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
            alert.getDialogPane().setExpanded(false);
        }

        alert.showAndWait();
    }
    // ===== Backward-compatible methods (older code may call these) =====

    public static void showWarn(String title, String header, String content) {
        showWithHeader(Alert.AlertType.WARNING, title, header, content, null);
    }

    public static void showInfo(String title, String header, String content) {
        showWithHeader(Alert.AlertType.INFORMATION, title, header, content, null);
    }

    public static void showError(String title, String header, String content) {
        showWithHeader(Alert.AlertType.ERROR, title, header, content, null);
    }

    private static void showWithHeader(Alert.AlertType type, String title, String header, String content, Throwable ex) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> showWithHeader(type, title, header, content, ex));
            return;
        }
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        if (ex != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ex.printStackTrace(pw);

            Label label = new Label("Details:");
            TextArea textArea = new TextArea(sw.toString());
            textArea.setEditable(false);
            textArea.setWrapText(false);
            textArea.setMaxWidth(Double.MAX_VALUE);
            textArea.setMaxHeight(Double.MAX_VALUE);
            GridPane.setVgrow(textArea, Priority.ALWAYS);
            GridPane.setHgrow(textArea, Priority.ALWAYS);

            GridPane expContent = new GridPane();
            expContent.setMaxWidth(Double.MAX_VALUE);
            expContent.add(label, 0, 0);
            expContent.add(textArea, 0, 1);

            alert.getDialogPane().setExpandableContent(expContent);
            alert.getDialogPane().setExpanded(false);
        }

        alert.showAndWait();
    }

    // ===== Convenience overloads used across the codebase =====

    /**
     * Convenience overload: show an error dialog using a Throwable.
     */
    public static void showError(String title, Throwable ex) {
        if (ex == null) {
            error(title, "Unknown error");
            return;
        }
        unexpected(title, ex);
    }

    /**
     * Convenience overload: show a warning dialog using a Throwable.
     */
    public static void showWarn(String title, Throwable ex) {
        if (ex == null) {
            warn(title, "Unknown warning");
            return;
        }
        show(Alert.AlertType.WARNING, title, ex.getMessage(), ex);
    }

    /**
     * Convenience overload: show an info dialog using a message and optional details.
     */
    public static void showInfo(String title, String content) {
        showWithHeader(Alert.AlertType.INFORMATION, title, null, content, null);
    }

}
