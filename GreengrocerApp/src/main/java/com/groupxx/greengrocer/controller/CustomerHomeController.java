package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.app.CartModel;
import com.groupxx.greengrocer.app.SceneRouter;
import com.groupxx.greengrocer.app.SessionContext;
import com.groupxx.greengrocer.dao.ProductDao;
import com.groupxx.greengrocer.model.ProductCategory;
import com.groupxx.greengrocer.model.ProductRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import com.groupxx.greengrocer.util.NumericValidators;
import com.groupxx.greengrocer.util.TextLimiters;
import com.groupxx.greengrocer.util.Validators;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CustomerHomeController {
    private static final Logger LOG = Logger.getLogger(CustomerHomeController.class.getName());

    @FXML
    private Label usernameLabel;
    @FXML
    private Button cartButton;
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<String> sortCombo;

    @FXML
    private javafx.scene.layout.FlowPane vegetablesFlow;
    @FXML
    private javafx.scene.layout.FlowPane fruitsFlow;
    @FXML
    private Label statusLabel;

    private final ProductDao productDao = new ProductDao();
    private List<ProductRecord> allProducts = new ArrayList<>();

    @FXML
    private void initialize() {
        usernameLabel.setText(SessionContext.username() == null ? "" : SessionContext.username());

        TextLimiters.limitLength(searchField, 64);

        sortCombo.getItems().setAll(
                "Name (A-Z)",
                "Price (Low to High)",
                "Price (High to Low)",
                "Stock (High to Low)");
        sortCombo.getSelectionModel().select(0);

        searchField.textProperty().addListener((obs, oldV, newV) -> render());
        sortCombo.valueProperty().addListener((obs, oldV, newV) -> render());

        reloadProducts();
    }

    @FXML
    private void onOpenOrders(javafx.event.ActionEvent e) {
        javafx.stage.Stage owner = (javafx.stage.Stage) ((javafx.scene.Node) e.getSource()).getScene().getWindow();
        OrderHistoryWindow.open(owner);
    }

    @FXML
    private void onLogout() {
        CartModel.get().clear(); // Clear cart when logging out to prevent sharing between users
        SceneRouter.showLogin();
    }

    @FXML
    private void onClearSearch() {
        searchField.setText("");
        sortCombo.getSelectionModel().select(0);
        statusLabel.setText("");
    }

    @FXML
    private void onOpenCart() {
        CartWindow.showAndWait(this::reloadProducts);
    }

    @FXML
    private void onOpenMessages() {
        CustomerMessagesWindow.showAndWait();
    }

    @FXML
    private void onOpenProfile(javafx.event.ActionEvent e) {
        ProfileWindow.showAndWait(() -> {
        });
    }

    public void reloadProducts() {
        statusLabel.setText("Loading products...");
        new Thread(() -> {
            try {
                List<ProductRecord> list = productDao.fetchAllActiveSorted();
                Platform.runLater(() -> {
                    allProducts = list;
                    statusLabel.setText("");
                    render();
                });
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Failed to load products", ex);
                Platform.runLater(() -> {
                    statusLabel.setText("");
                    Alerts.showError("Load Failed", "Cannot load products from DB.", ex.getMessage());
                });
            }
        }, "products-load").start();
    }

    private void render() {
        String keyword = Validators.normalize(searchField.getText()).toLowerCase();
        String sort = sortCombo.getValue();
        if (sort == null)
            sort = "Name (A-Z)";

        vegetablesFlow.getChildren().clear();
        fruitsFlow.getChildren().clear();

        List<ProductRecord> filtered = new ArrayList<>();
        for (ProductRecord p : allProducts) {
            if (!keyword.isEmpty() && !p.name().toLowerCase().contains(keyword))
                continue;
            filtered.add(p);
        }

        // Sorting
        final String s = sort;
        filtered.sort((p1, p2) -> {
            switch (s) {
                case "Price (Low to High)":
                    return p1.effectivePricePerKg().compareTo(p2.effectivePricePerKg());
                case "Price (High to Low)":
                    return p2.effectivePricePerKg().compareTo(p1.effectivePricePerKg());
                case "Stock (High to Low)":
                    BigDecimal stock1 = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p1.stockKg());
                    BigDecimal stock2 = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p2.stockKg());
                    return stock2.compareTo(stock1);
                default: // Name (A-Z)
                    return p1.name().compareToIgnoreCase(p2.name());
            }
        });

        for (ProductRecord p : filtered) {
            HBox row = createProductRow(p);
            // Adjust row for FlowPane (optional card style)
            row.setStyle(
                    "-fx-border-color: #ddd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-background-color: white; -fx-padding: 10;");
            row.setPrefWidth(300);

            if (p.category() == ProductCategory.VEGETABLE)
                vegetablesFlow.getChildren().add(row);
            else if (p.category() == ProductCategory.FRUIT)
                fruitsFlow.getChildren().add(row);
        }

        int cartCount = CartModel.get().snapshot().size();
        cartButton.setText(cartCount == 0 ? "Cart" : "Cart (" + cartCount + ")");
    }

    private static final class QtyOption {
        final java.math.BigDecimal kg;
        final String label;
        final int pieces; // 0 for weight-based items

        QtyOption(java.math.BigDecimal kg, String label, int pieces) {
            this.kg = kg;
            this.label = label;
            this.pieces = pieces;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private HBox createProductRow(ProductRecord p) {
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
        try {
            byte[] imageBytes = p.imageBytes();
            if (imageBytes != null && imageBytes.length > 0) {
                javafx.scene.image.Image img = new javafx.scene.image.Image(
                        new java.io.ByteArrayInputStream(imageBytes));
                iv.setImage(img);
            }
        } catch (Exception ignore) {
        }

        iv.setFitWidth(64);
        iv.setFitHeight(64);
        iv.setPreserveRatio(true);

        com.groupxx.greengrocer.config.AppConfig.MinimumOrderRule rule = com.groupxx.greengrocer.config.AppConfig
                .minimumOrderFor(p.name());

        Label name = new Label(p.name());
        name.setStyle("-fx-font-weight: bold;");

        java.math.BigDecimal price = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.effectivePricePerKg());
        Label priceLbl = new Label("Price/kg: " + Formatters.formatMoney(price));

        Label minOrderLbl = new Label(rule.note);
        minOrderLbl.setStyle("-fx-text-fill: rgba(0,0,0,0.65); -fx-font-size: 11px;");

        java.math.BigDecimal stock = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.stockKg());
        java.math.BigDecimal reserved = com.groupxx.greengrocer.util.BigDecimalUtil
                .nz(CartModel.get().reservedKg(p.id()));
        java.math.BigDecimal availableRaw = stock.subtract(reserved);
        java.math.BigDecimal availableSafe = com.groupxx.greengrocer.util.BigDecimalUtil.nz(availableRaw);
        if (availableSafe.compareTo(java.math.BigDecimal.ZERO) < 0)
            availableSafe = java.math.BigDecimal.ZERO;
        final java.math.BigDecimal availableFinal = availableSafe;

        Label stockLbl;
        if (availableFinal.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            stockLbl = new Label("Out of stock");
            stockLbl.setStyle("-fx-text-fill: #b00020;");
        } else if (rule.unitType == com.groupxx.greengrocer.config.AppConfig.UnitType.PIECE
                && rule.pieceWeightKg != null) {
            int availPieces = availableFinal.divide(rule.pieceWeightKg, 0, java.math.RoundingMode.FLOOR).intValue();
            stockLbl = new Label(
                    "Available: " + Formatters.formatQuantity(availableFinal) + " kg (~" + availPieces + " pcs)");
            java.math.BigDecimal threshold = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.thresholdKg());
            if (availableFinal.compareTo(threshold) <= 0)
                stockLbl.setStyle("-fx-text-fill: #d07a00;");
        } else {
            stockLbl = new Label("Available: " + Formatters.formatQuantity(availableFinal) + " kg");
            java.math.BigDecimal threshold = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.thresholdKg());
            if (availableFinal.compareTo(threshold) <= 0)
                stockLbl.setStyle("-fx-text-fill: #d07a00;");
        }

        VBox info = new VBox(2, name, priceLbl, minOrderLbl, stockLbl);
        info.setMinWidth(240);

        ComboBox<QtyOption> qtyBox = new ComboBox<>();
        qtyBox.setPrefWidth(160);

        List<QtyOption> defaultOptions = buildDefaultOptions(rule, availableFinal);
        qtyBox.getItems().setAll(defaultOptions);

        // if there are no options -> cannot add
        boolean canBuy = !defaultOptions.isEmpty() && availableFinal.compareTo(java.math.BigDecimal.ZERO) > 0;

        if (canBuy) {
            qtyBox.setPromptText("Select...");
            // Do NOT select first item by default
        }

        Button manualBtn = new Button("Manual…");
        manualBtn.setDisable(!canBuy);

        Button addBtn = new Button("Add");
        addBtn.setDisable(!canBuy);

        // Label to display the selected quantity (especially for manual input)
        Label selectedQtyLabel = new Label("");
        selectedQtyLabel.setStyle("-fx-text-fill: #006400; -fx-font-weight: bold;");
        selectedQtyLabel.setMinWidth(120);

        // Precompute max default for validation
        final java.math.BigDecimal maxDefaultKg = defaultOptions.isEmpty() ? java.math.BigDecimal.ZERO
                : defaultOptions.get(defaultOptions.size() - 1).kg;
        final int maxDefaultPieces = defaultOptions.isEmpty() ? 0
                : defaultOptions.get(defaultOptions.size() - 1).pieces;

        final QtyOption[] selectedManual = new QtyOption[1]; // used if user enters manual value

        // Update label when combo selection changes
        qtyBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedQtyLabel.setText("Selected: " + newVal.label);
                selectedManual[0] = null; // clear manual if dropdown is used
            }
        });

        manualBtn.setOnAction(e -> {
            try {
                java.math.BigDecimal nowReserved = com.groupxx.greengrocer.util.BigDecimalUtil
                        .nz(CartModel.get().reservedKg(p.id()));
                java.math.BigDecimal nowAvailable = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.stockKg())
                        .subtract(nowReserved);
                if (nowAvailable.compareTo(java.math.BigDecimal.ZERO) < 0)
                    nowAvailable = java.math.BigDecimal.ZERO;

                QtyOption opt = promptManualOption(rule, nowAvailable, maxDefaultKg, maxDefaultPieces);
                if (opt == null)
                    return;
                selectedManual[0] = opt;
                qtyBox.getSelectionModel().clearSelection();
                qtyBox.setPromptText(opt.label);
                // Show the selected manual value in the label
                selectedQtyLabel.setText("Selected: " + opt.label);
            } catch (Exception ex) {
                Alerts.showError("Manual Input Failed", "Cannot set manual amount.", ex.getMessage());
            }
        });

        addBtn.setStyle(
                "-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        addBtn.setMinWidth(60);
        manualBtn.setStyle("-fx-border-color: #bbb; -fx-background-color: #f9f9f9; -fx-cursor: hand;");
        manualBtn.setMinWidth(80); // Increased for "Manual..."

        addBtn.setOnAction(e -> {
            addBtn.setDisable(true);
            try {
                java.math.BigDecimal nowReserved = com.groupxx.greengrocer.util.BigDecimalUtil
                        .nz(CartModel.get().reservedKg(p.id()));
                java.math.BigDecimal nowAvailable = com.groupxx.greengrocer.util.BigDecimalUtil.nz(p.stockKg())
                        .subtract(nowReserved);
                if (nowAvailable.compareTo(java.math.BigDecimal.ZERO) < 0)
                    nowAvailable = java.math.BigDecimal.ZERO;

                QtyOption chosen = qtyBox.getValue();
                if (chosen == null)
                    chosen = selectedManual[0];
                if (chosen == null) {
                    Alerts.warn("No amount selected", "Choose an amount first.");
                    addBtn.setDisable(!canBuy);
                    return;
                }

                java.math.BigDecimal kg = chosen.kg;
                if (kg == null || kg.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                    Alerts.warn("Invalid Amount", "Amount must be positive.");
                    addBtn.setDisable(!canBuy);
                    return;
                }

                // validate against current availability
                if (nowAvailable.compareTo(kg) < 0) {
                    Alerts.showWarn("Not enough stock",
                            "Cannot add selected amount.",
                            "Available: " + Formatters.formatQuantity(nowAvailable) + " kg");
                    addBtn.setDisable(!canBuy);
                    return;
                }

                // enforce min order + multiple rules
                String err = validateQuantityAgainstRule(rule, chosen, maxDefaultKg, maxDefaultPieces);
                if (err != null) {
                    Alerts.showWarn("Unsupported amount", err, "");
                    addBtn.setDisable(!canBuy);
                    return;
                }

                CartModel.get().addOrIncrease(p.id(), p.name(), p.category(), p.effectivePricePerKg(), kg);
                statusLabel.setText("Added: " + p.name() + " (" + chosen.label + ")");
                render();
            } catch (Exception ex) {
                LOG.log(java.util.logging.Level.SEVERE, "Failed to add product to cart id=" + p.id(), ex);
                Alerts.showError("Add Failed", "Unexpected error while adding to cart.", ex.getMessage());
            }
        });

        HBox row = new HBox(12, iv, info, qtyBox, selectedQtyLabel, manualBtn, addBtn);
        row.setPadding(new javafx.geometry.Insets(6));
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS); // Ensure info box takes available space

        return row;
    }

    private List<QtyOption> buildDefaultOptions(com.groupxx.greengrocer.config.AppConfig.MinimumOrderRule rule,
            java.math.BigDecimal availableKg) {
        List<QtyOption> out = new java.util.ArrayList<>();
        if (availableKg == null)
            return out;
        if (availableKg.compareTo(java.math.BigDecimal.ZERO) <= 0)
            return out;

        java.math.BigDecimal maxKg = availableKg.min(com.groupxx.greengrocer.config.AppConfig.DEFAULT_MAX_OPTION_KG);

        if (rule.unitType == com.groupxx.greengrocer.config.AppConfig.UnitType.PIECE && rule.pieceWeightKg != null) {
            int availablePieces = maxPiecesForKg(availableKg, rule.pieceWeightKg);
            int maxDefaultPieces = maxPiecesForKg(maxKg, rule.pieceWeightKg);

            int start = Math.max(1, rule.minPieces);
            if (availablePieces < start)
                return out;

            for (int pcs = start; pcs <= maxDefaultPieces; pcs++) {
                java.math.BigDecimal kg = rule.pieceWeightKg.multiply(java.math.BigDecimal.valueOf(pcs));
                if (kg.compareTo(availableKg) > 0)
                    break;
                out.add(new QtyOption(kg.setScale(2, java.math.RoundingMode.HALF_UP),
                        pcs + " pcs (" + Formatters.formatQuantity(kg) + " kg)", pcs));
            }
        } else {
            java.math.BigDecimal step = rule.minOrderKg == null ? new java.math.BigDecimal("0.25") : rule.minOrderKg;
            if (step.compareTo(java.math.BigDecimal.ZERO) <= 0)
                step = new java.math.BigDecimal("0.25");

            if (availableKg.compareTo(step) < 0)
                return out;

            java.math.BigDecimal v = step;
            int guard = 0;
            while (v.compareTo(maxKg) <= 0 && guard < 200) {
                out.add(new QtyOption(v.setScale(2, java.math.RoundingMode.HALF_UP),
                        Formatters.formatQuantity(v) + " kg", 0));
                v = v.add(step);
                guard++;
            }
        }
        return out;
    }

    private int maxPiecesForKg(java.math.BigDecimal kg, java.math.BigDecimal pieceWeightKg) {
        if (kg == null || pieceWeightKg == null)
            return 0;
        if (pieceWeightKg.compareTo(java.math.BigDecimal.ZERO) <= 0)
            return 0;
        return kg.divide(pieceWeightKg, 0, java.math.RoundingMode.FLOOR).intValue();
    }

    private QtyOption promptManualOption(com.groupxx.greengrocer.config.AppConfig.MinimumOrderRule rule,
            java.math.BigDecimal availableKg,
            java.math.BigDecimal maxDefaultKg,
            int maxDefaultPieces) {
        if (rule.unitType == com.groupxx.greengrocer.config.AppConfig.UnitType.PIECE && rule.pieceWeightKg != null) {
            int availablePieces = maxPiecesForKg(availableKg, rule.pieceWeightKg);
            int minPieces = Math.max(1, rule.minPieces);

            if (availablePieces <= maxDefaultPieces) {
                Alerts.warn("Manual option unavailable",
                        "No stock left above the default maximum.",
                        "Available pieces: " + availablePieces);
                return null;
            }

            TextInputDialog dlg = new TextInputDialog(String.valueOf(Math.max(maxDefaultPieces + 1, minPieces)));
            dlg.setTitle("Manual Quantity");
            dlg.setHeaderText("Enter number of pieces (must be > " + maxDefaultPieces + ")");
            dlg.setContentText("Pieces:");

            var opt = dlg.showAndWait();
            if (opt.isEmpty())
                return null;

            String input = opt.get().trim();

            // Check for extremely large numbers (11+ digits or unreasonably large)
            if (input.length() >= 11 || (input.length() >= 5 && !input.startsWith("-"))) {
                try {
                    long testValue = Long.parseLong(input);
                    if (testValue > 10000) {
                        Alerts.warn("Are you crazy?", "Why do you need so much?",
                                "Please enter a reasonable quantity.");
                        return null;
                    }
                } catch (NumberFormatException e) {
                    Alerts.warn("Are you crazy?", "Why do you need so much?",
                            "Please enter a reasonable quantity.");
                    return null;
                }
            }

            int pcs;
            try {
                pcs = Integer.parseInt(input);
            } catch (Exception ex) {
                Alerts.warn("Invalid number", "Pieces must be an integer.", "");
                return null;
            }

            if (pcs <= maxDefaultPieces) {
                Alerts.warn("Unsupported amount", "Manual amount must be greater than the maximum dropdown option ("
                        + maxDefaultPieces + " pcs).", "");
                return null;
            }
            if (pcs < minPieces) {
                Alerts.warn("Unsupported amount", "Minimum order is " + minPieces + " pcs.", "");
                return null;
            }
            if (pcs > availablePieces) {
                Alerts.warn("Not enough stock", "Not enough pieces in stock.",
                        "Available: " + availablePieces + " pcs");
                return null;
            }

            java.math.BigDecimal kg = rule.pieceWeightKg.multiply(java.math.BigDecimal.valueOf(pcs)).setScale(2,
                    java.math.RoundingMode.HALF_UP);
            return new QtyOption(kg, pcs + " pcs (" + Formatters.formatQuantity(kg) + " kg)", pcs);
        }

        // WEIGHT
        java.math.BigDecimal step = rule.minOrderKg == null ? new java.math.BigDecimal("0.25") : rule.minOrderKg;
        java.math.BigDecimal defaultValue = maxDefaultKg.add(step).setScale(2, java.math.RoundingMode.HALF_UP);

        TextInputDialog dlg = new TextInputDialog(defaultValue.toPlainString());
        dlg.setTitle("Manual Weight");
        dlg.setHeaderText("Enter kg amount (must be > " + maxDefaultKg.toPlainString() + " kg)");
        dlg.setContentText("Kg:");

        var opt = dlg.showAndWait();
        if (opt.isEmpty())
            return null;

        String input = opt.get().trim();

        // Check for extremely large numbers
        java.math.BigDecimal kg;
        try {
            kg = new java.math.BigDecimal(input).setScale(2, java.math.RoundingMode.HALF_UP);
            if (kg.compareTo(new java.math.BigDecimal("10000")) > 0) {
                Alerts.warn("Are you crazy?", "Why do you need so much?",
                        "Please enter a reasonable quantity.");
                return null;
            }
        } catch (Exception ex) {
            // Check if it's simply too large to parse
            if (input.length() >= 10) {
                Alerts.warn("Are you crazy?", "Why do you need so much?",
                        "Please enter a reasonable quantity.");
            } else {
                Alerts.warn("Invalid number", "Weight must be a number (kg).", "");
            }
            return null;
        }

        if (kg.compareTo(maxDefaultKg) <= 0) {
            Alerts.warn("Unsupported amount", "Manual amount must be greater than the maximum dropdown option ("
                    + maxDefaultKg.toPlainString() + " kg).", "");
            return null;
        }
        if (kg.compareTo(step) < 0) {
            Alerts.warn("Unsupported amount", "Minimum order is " + step.toPlainString() + " kg.", "");
            return null;
        }
        if (kg.compareTo(availableKg) > 0) {
            Alerts.warn("Not enough stock", "Not enough stock in kg.",
                    "Available: " + Formatters.formatQuantity(availableKg) + " kg");
            return null;
        }
        return new QtyOption(kg, Formatters.formatQuantity(kg) + " kg", 0);
    }

    private String validateQuantityAgainstRule(com.groupxx.greengrocer.config.AppConfig.MinimumOrderRule rule,
            QtyOption chosen,
            java.math.BigDecimal maxDefaultKg,
            int maxDefaultPieces) {
        if (chosen == null)
            return "No amount selected.";

        if (chosen.pieces > 0 && rule.unitType == com.groupxx.greengrocer.config.AppConfig.UnitType.PIECE) {
            if (chosen.pieces < Math.max(1, rule.minPieces)) {
                return "Minimum order is " + Math.max(1, rule.minPieces) + " pcs.";
            }
            // If it was manual (promptText), enforce manual > max default pieces
            if (chosen.pieces > 0 && chosen.pieces != 0 && chosen.pieces > maxDefaultPieces) {
                return null;
            }
            return null;
        }

        java.math.BigDecimal step = rule.minOrderKg == null ? new java.math.BigDecimal("0.25") : rule.minOrderKg;
        if (chosen.kg.compareTo(step) < 0) {
            return "Minimum order is " + step.toPlainString() + " kg.";
        }
        return null;
    }
}
