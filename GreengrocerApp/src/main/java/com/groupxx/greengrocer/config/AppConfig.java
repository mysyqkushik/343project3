package com.groupxx.greengrocer.config;

import java.math.BigDecimal;

public final class AppConfig {
    private AppConfig() {
    }

    public static final String GROUP_LABEL = "GroupXX";
    public static final String APP_TITLE = GROUP_LABEL + " GreenGrocer";

    public static final double WINDOW_W = 960;
    public static final double WINDOW_H = 540;
    public static final long CANCEL_WINDOW_MINUTES = 30; // Customer can cancel within 30 minutes after order time

    // DB (override via env vars or JVM properties)
    public static final String DB_URL = envOrProperty(
            "GREENGROCER_DB_URL",
            "greengrocer.db.url",
            "jdbc:mysql://localhost:3306/greengrocer?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    public static final String DB_USER = envOrProperty("GREENGROCER_DB_USER", "greengrocer.db.user", "myuser");
    public static final String DB_PASSWORD = envOrProperty("GREENGROCER_DB_PASSWORD", "greengrocer.db.password",
            "1234");

    // Password hashing
    public static final int PBKDF2_ITERATIONS = 120_000;
    public static final int SALT_BYTES = 16;

    // Finance rules
    public static final BigDecimal VAT_RATE = new BigDecimal("0.08"); // 8% VAT
    public static final BigDecimal MIN_CART_SUBTOTAL = new BigDecimal("100.00"); // minimum subtotal

    // Coupon rules (explicit conditions)
    public static final String COUPON_CODE = "SAVE10";
    public static final BigDecimal COUPON_MIN_SUBTOTAL = new BigDecimal("200.00");
    public static final BigDecimal COUPON_DISCOUNT_RATE = new BigDecimal("0.10"); // 10%

    // Loyalty rules (defaults; can be overridden via Settings table)
    public static final int LOYALTY_MIN_DELIVERED_ORDERS = 5;
    public static final BigDecimal LOYALTY_DISCOUNT_RATE = new BigDecimal("0.05"); // 5%

    // Product purchase rules
    public static final BigDecimal DEFAULT_MAX_OPTION_KG = new BigDecimal("2.00"); // max of the default dropdown
                                                                                   // options
    public static final int CANCEL_SEND_SECONDS = 10; // "Cancel sending" window for messages

    public enum UnitType {
        WEIGHT, PIECE
    }

    /**
     * Minimum-order and unit rule used by the customer UI (dropdown generation +
     * manual input validation).
     */
    public static final class MinimumOrderRule {
        public final UnitType unitType;
        public final BigDecimal minOrderKg; // for WEIGHT
        public final int minPieces; // for PIECE
        public final BigDecimal pieceWeightKg; // for PIECE
        public final String note; // shown on product card

        private MinimumOrderRule(UnitType unitType, BigDecimal minOrderKg, int minPieces, BigDecimal pieceWeightKg,
                String note) {
            this.unitType = unitType;
            this.minOrderKg = minOrderKg;
            this.minPieces = minPieces;
            this.pieceWeightKg = pieceWeightKg;
            this.note = note == null ? "" : note;
        }

        public static MinimumOrderRule weight(BigDecimal minOrderKg, String note) {
            return new MinimumOrderRule(UnitType.WEIGHT, minOrderKg, 0, null, note);
        }

        public static MinimumOrderRule piece(int minPieces, BigDecimal pieceWeightKg, String note) {
            return new MinimumOrderRule(UnitType.PIECE, null, minPieces, pieceWeightKg, note);
        }
    }

    private static final MinimumOrderRule DEFAULT_MIN_ORDER = MinimumOrderRule.weight(new BigDecimal("0.25"),
            "Minimum order: 250 g");

    private static final java.util.Map<String, MinimumOrderRule> MIN_ORDER_RULES = java.util.Map.ofEntries(
            // Fruits
            java.util.Map.entry("Apple",
                    MinimumOrderRule.piece(1, new BigDecimal("0.25"), "Minimum order: 250 g (1 pcs)")),
            java.util.Map.entry("Banana",
                    MinimumOrderRule.weight(new BigDecimal("1.00"), "Minimum order: 1 kg (1 bunch)")),
            java.util.Map.entry("Cherry",
                    MinimumOrderRule.weight(new BigDecimal("0.25"), "Minimum order: 250 g (1 tray)")),
            java.util.Map.entry("Grapes",
                    MinimumOrderRule.weight(new BigDecimal("0.50"), "Minimum order: 0.5 kg (1 bunch)")),
            java.util.Map.entry("Kiwi",
                    MinimumOrderRule.piece(2, new BigDecimal("0.15"), "Minimum order: 300 g (2 pcs)")),
            java.util.Map.entry("Lemon",
                    MinimumOrderRule.piece(1, new BigDecimal("0.20"), "Minimum order: 200 g (1 pcs)")),
            java.util.Map.entry("Mango",
                    MinimumOrderRule.piece(1, new BigDecimal("0.30"), "Minimum order: 300 g (1 pcs)")),
            java.util.Map.entry("Orange",
                    MinimumOrderRule.weight(new BigDecimal("1.00"), "Minimum order: 1 kg (1 net / bag)")),
            java.util.Map.entry("Peach",
                    MinimumOrderRule.piece(5, new BigDecimal("0.10"), "Minimum order: 0.5 kg (5 pcs)")),
            java.util.Map.entry("Pear",
                    MinimumOrderRule.piece(3, new BigDecimal("0.17"), "Minimum order: 0.5 kg (3 pcs)")),
            java.util.Map.entry("Pineapple",
                    MinimumOrderRule.piece(1, new BigDecimal("1.00"), "Minimum order: 1 kg (1 pineapple)")),
            java.util.Map.entry("Strawberry",
                    MinimumOrderRule.weight(new BigDecimal("0.25"), "Minimum order: 250 g (1 tray)")),

            // Vegetables
            java.util.Map.entry("Broccoli",
                    MinimumOrderRule.piece(1, new BigDecimal("0.50"), "Minimum order: 500 g (1 head)")),
            java.util.Map.entry("Brocolli",
                    MinimumOrderRule.piece(1, new BigDecimal("0.50"), "Minimum order: 500 g (1 head)")),
            java.util.Map.entry("Carrot",
                    MinimumOrderRule.piece(5, new BigDecimal("0.10"), "Minimum order: 500 g (5 pcs)")),
            java.util.Map.entry("Cucumber",
                    MinimumOrderRule.piece(1, new BigDecimal("0.25"), "Minimum order: 250 g (1 pc)")),
            java.util.Map.entry("Eggplant",
                    MinimumOrderRule.piece(1, new BigDecimal("0.40"), "Minimum order: 400 g (1 pc)")),
            java.util.Map.entry("Garlic",
                    MinimumOrderRule.piece(1, new BigDecimal("0.10"), "Minimum order: 100 g (1 bulb)")),
            java.util.Map.entry("Lettuce",
                    MinimumOrderRule.piece(1, new BigDecimal("0.30"), "Minimum order: 300 g (1 head)")),
            java.util.Map.entry("Onion",
                    MinimumOrderRule.piece(5, new BigDecimal("0.10"), "Minimum order: 500 g (5 pcs)")),
            java.util.Map.entry("Pepper",
                    MinimumOrderRule.piece(2, new BigDecimal("0.15"), "Minimum order: 300 g (2 pcs)")),
            java.util.Map.entry("Spinach",
                    MinimumOrderRule.weight(new BigDecimal("0.25"), "Minimum order: 250 g (1 bag)")),
            java.util.Map.entry("Tomato",
                    MinimumOrderRule.piece(5, new BigDecimal("0.10"), "Minimum order: 500 g (5 pcs)")),
            java.util.Map.entry("Zucchini",
                    MinimumOrderRule.piece(1, new BigDecimal("0.30"), "Minimum order: 300 g (1 pc)")),
            // In your seed list there is also Potato; it's not in the provided list, so we
            // use a reasonable default
            java.util.Map.entry("Potato", MinimumOrderRule.weight(new BigDecimal("1.00"), "Minimum order: 1 kg")));

    public static MinimumOrderRule minimumOrderFor(String productName) {
        if (productName == null)
            return DEFAULT_MIN_ORDER;
        return MIN_ORDER_RULES.getOrDefault(productName.trim(), DEFAULT_MIN_ORDER);
    }

    private static String envOrProperty(String envKey, String propKey, String defaultValue) {
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return System.getProperty(propKey, defaultValue);
    }
}
