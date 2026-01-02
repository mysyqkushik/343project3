package com.groupxx.greengrocer;

/**
 * Main Entry Point for the Greengrocer Application.
 * <p>
 * This class serves as the launcher ensuring that the JavaFX runtime is
 * correctly
 * initialized, especially when running from a shaded jar or environments where
 * module path issues might arise if extending Application directly from result
 * of main method.
 * </p>
 */
public class Launcher {
    /**
     * The main method that launches the application.
     *
     * @param args Command line arguments passed to the application.
     */
    public static void main(String[] args) {
        MainApp.main(args);
    }
}