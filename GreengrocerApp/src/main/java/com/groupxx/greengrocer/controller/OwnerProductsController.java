package com.groupxx.greengrocer.controller;

import com.groupxx.greengrocer.dao.ProductDao;
import com.groupxx.greengrocer.model.ProductCategory;
import com.groupxx.greengrocer.model.ProductRecord;
import com.groupxx.greengrocer.util.Alerts;
import com.groupxx.greengrocer.util.Formatters;
import com.groupxx.greengrocer.util.Validators;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OwnerProductsController {
    private static final Logger LOG = Logger.getLogger(OwnerProductsController.class.getName());

    @FXML
    private TableView<ProductRecord> table;
    @FXML
    private TableColumn<ProductRecord, String> colId;
    @FXML
    private TableColumn<ProductRecord, String> colName;
    @FXML
    private TableColumn<ProductRecord, String> colCategory;
    @FXML
    private TableColumn<ProductRecord, String> colPrice;
    @FXML
    private TableColumn<ProductRecord, String> colStock;
    @FXML
    private TableColumn<ProductRecord, String> colThreshold;
    @FXML
    private TableColumn<ProductRecord, String> colActive;

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<ProductCategory> categoryBox;
    @FXML
    private TextField priceField;
    @FXML
    private TextField stockField;
    @FXML
    private TextField thresholdField;
    @FXML
    private CheckBox activeCheck;
    @FXML
    private Label selectedIdLabel;

    private final ProductDao productDao = new ProductDao();
    private ProductRecord selected;

    @FXML
    private void initialize() {
        colId.setCellValueFactory(v -> new SimpleStringProperty(String.valueOf(v.getValue().id())));
        colName.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().name()));
        colCategory.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().category().name()));
        // Owner edits the BASE price; Customer view uses effectivePricePerKg() when
        // stock <= threshold.
        colPrice.setCellValueFactory(
                v -> new SimpleStringProperty(Formatters.formatMoney(v.getValue().basePricePerKg())));
        colStock.setCellValueFactory(v -> new SimpleStringProperty(Formatters.formatQuantity(v.getValue().stockKg())));
        colThreshold.setCellValueFactory(
                v -> new SimpleStringProperty(Formatters.formatQuantity(v.getValue().thresholdKg())));
        colActive.setCellValueFactory(v -> new SimpleStringProperty(v.getValue().active() ? "Yes" : "No"));

        categoryBox.setItems(FXCollections.observableArrayList(ProductCategory.values()));
        categoryBox.getSelectionModel().select(ProductCategory.VEGETABLE);
        activeCheck.setSelected(true);

        table.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            selected = b;
            if (b == null) {
                clearForm();
            } else {
                fillForm(b);
            }
        });

        reload();
    }

    @FXML
    private void onReload() {
        reload();
    }

    @FXML
    private void onNew() {
        table.getSelectionModel().clearSelection();
        selected = null;
        clearForm();
    }

    @FXML
    private void onSave() {
        try {
            String name = Validators.normalize(nameField.getText());
            if (name.isEmpty()) {
                Alerts.warn("Validation", "Product name is required.");
                return;
            }
            ProductCategory category = categoryBox.getSelectionModel().getSelectedItem();
            if (category == null)
                category = ProductCategory.VEGETABLE;

            // Validate Price field
            BigDecimal price;
            try {
                price = Validators.parseBigDecimal(priceField.getText());
            } catch (IllegalArgumentException ex) {
                Alerts.warn("Validation", "Price/kg must be a valid number (e.g., 50.00).");
                return;
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                Alerts.warn("Validation", "Price must be > 0.");
                return;
            }

            // Validate Stock field
            BigDecimal stock;
            try {
                stock = Validators.parseBigDecimal(stockField.getText());
            } catch (IllegalArgumentException ex) {
                Alerts.warn("Validation", "Stock (kg) must be a valid number (e.g., 10.00).");
                return;
            }
            if (stock.compareTo(BigDecimal.ZERO) < 0) {
                Alerts.warn("Validation", "Stock must be >= 0.");
                return;
            }

            // Validate Threshold field
            BigDecimal threshold;
            try {
                threshold = Validators.parseBigDecimal(thresholdField.getText());
            } catch (IllegalArgumentException ex) {
                Alerts.warn("Validation", "Threshold (kg) must be a valid number (e.g., 5.00).");
                return;
            }
            if (threshold.compareTo(BigDecimal.ZERO) < 0) {
                Alerts.warn("Validation", "Threshold must be >= 0.");
                return;
            }

            boolean active = activeCheck.isSelected();

            if (selected == null) {
                long id = productDao.createProduct(name, category, price, stock, threshold, null, active);
                Alerts.info("Saved", "Product created. New product id: " + id);
            } else {
                productDao.updateProduct(selected.id(), name, category, price, stock, threshold, active);
                Alerts.info("Saved", "Product updated. ID: " + selected.id());
            }

            reload();
        } catch (java.sql.SQLIntegrityConstraintViolationException ex) {
            // Duplicate name+category
            Alerts.warn("Duplicate Product",
                    "A product with this name and category already exists.\nPlease use a different name or edit the existing product.");
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to save product", ex);
            Alerts.showError("Save Failed", "Could not save product.", ex.getMessage());
        }
    }

    @FXML
    private void onToggleActive() {
        ProductRecord row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            Alerts.warn("No Selection", "Select a product first.");
            return;
        }
        try {
            productDao.setActive(row.id(), !row.active());
            reload();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to toggle active", ex);
            Alerts.showError("Update Failed", "Could not update active flag.", ex.getMessage());
        }
    }

    @FXML
    private void onUploadImage() {
        ProductRecord row = table.getSelectionModel().getSelectedItem();
        if (row == null) {
            Alerts.warn("No Selection", "Select a product first.");
            return;
        }
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose product image");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(table.getScene().getWindow());
            if (f == null)
                return;
            byte[] bytes = Files.readAllBytes(f.toPath());
            productDao.updateImage(row.id(), bytes);
            reload();
            Alerts.info("Image Updated", "Product image saved: " + f.getName());
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to upload image", ex);
            Alerts.showError("Image Failed", "Could not update product image.", ex.getMessage());
        }
    }

    public void reload() {
        try {
            List<ProductRecord> all = productDao.fetchAllSorted();
            table.setItems(FXCollections.observableArrayList(all));
            if (selected != null) {
                // try reselect
                for (ProductRecord p : all) {
                    if (p.id() == selected.id()) {
                        table.getSelectionModel().select(p);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Failed to load products", ex);
            Alerts.showError("Load Failed", "Could not load products.", ex.getMessage());
        }
    }

    private void clearForm() {
        selectedIdLabel.setText("New");
        nameField.setText("");
        categoryBox.getSelectionModel().select(ProductCategory.VEGETABLE);
        priceField.setText("");
        stockField.setText("0");
        thresholdField.setText("0");
        activeCheck.setSelected(true);
    }

    private void fillForm(ProductRecord p) {
        selectedIdLabel.setText(String.valueOf(p.id()));
        nameField.setText(p.name());
        categoryBox.getSelectionModel().select(p.category());
        priceField.setText(p.basePricePerKg().toPlainString());
        stockField.setText(p.stockKg().toPlainString());
        thresholdField.setText(p.thresholdKg().toPlainString());
        activeCheck.setSelected(p.active());
    }
}
