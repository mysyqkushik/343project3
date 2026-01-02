package com.groupxx.greengrocer.util;

import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;

public final class TextLimiters {
    private TextLimiters() {}

    public static void limitLength(TextInputControl control, int maxLen) {
        control.setTextFormatter(new TextFormatter<String>(change -> {
            String next = change.getControlNewText();
            if (next == null) return change;
            return next.length() <= maxLen ? change : null;
        }));
    }
}
