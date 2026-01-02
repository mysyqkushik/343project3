package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.model.ProductCategory;
import com.groupxx.greengrocer.model.ProductRecord;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public final class ProductDao {

    public void ensureProductTableAndSeed() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS product_info (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      name VARCHAR(64) NOT NULL,
                      category ENUM('FRUIT','VEGETABLE') NOT NULL,
                      price_per_kg DECIMAL(10,2) NOT NULL,
                      stock_kg DECIMAL(10,2) NOT NULL,
                      threshold_kg DECIMAL(10,2) NOT NULL,
                      image_blob LONGBLOB NOT NULL,
                      active BOOLEAN NOT NULL DEFAULT TRUE,
                      UNIQUE KEY uq_name_cat (name, category)
                    )
                    """)) {
                ps.execute();
            }

            // If already seeded, do nothing
            if (countProducts(c) >= 24)
                return;

            byte[] placeholder = makePlaceholderPng();

            // 12 Vegetables
            insert(c, "Carrot", ProductCategory.VEGETABLE, "55.00", "40.00", "5.00", placeholder);
            insert(c, "Cucumber", ProductCategory.VEGETABLE, "45.00", "40.00", "5.00", placeholder);
            insert(c, "Eggplant", ProductCategory.VEGETABLE, "70.00", "35.00", "5.00", placeholder);
            insert(c, "Garlic", ProductCategory.VEGETABLE, "120.00", "20.00", "3.00", placeholder);
            insert(c, "Lettuce", ProductCategory.VEGETABLE, "35.00", "25.00", "3.00", placeholder);
            insert(c, "Onion", ProductCategory.VEGETABLE, "40.00", "50.00", "5.00", placeholder);
            insert(c, "Pepper", ProductCategory.VEGETABLE, "75.00", "30.00", "5.00", placeholder);
            insert(c, "Potato", ProductCategory.VEGETABLE, "30.00", "80.00", "8.00", placeholder);
            insert(c, "Spinach", ProductCategory.VEGETABLE, "60.00", "25.00", "4.00", placeholder);
            insert(c, "Tomato", ProductCategory.VEGETABLE, "50.00", "45.00", "5.00", placeholder);
            insert(c, "Zucchini", ProductCategory.VEGETABLE, "55.00", "30.00", "5.00", placeholder);
            insert(c, "Broccoli", ProductCategory.VEGETABLE, "95.00", "20.00", "3.00", placeholder);

            // 12 Fruits
            insert(c, "Apple", ProductCategory.FRUIT, "60.00", "50.00", "5.00", placeholder);
            insert(c, "Banana", ProductCategory.FRUIT, "65.00", "45.00", "5.00", placeholder);
            insert(c, "Cherry", ProductCategory.FRUIT, "180.00", "15.00", "2.00", placeholder);
            insert(c, "Grapes", ProductCategory.FRUIT, "110.00", "20.00", "3.00", placeholder);
            insert(c, "Kiwi", ProductCategory.FRUIT, "140.00", "18.00", "3.00", placeholder);
            insert(c, "Lemon", ProductCategory.FRUIT, "55.00", "30.00", "4.00", placeholder);
            insert(c, "Mango", ProductCategory.FRUIT, "160.00", "12.00", "2.00", placeholder);
            insert(c, "Orange", ProductCategory.FRUIT, "58.00", "40.00", "5.00", placeholder);
            insert(c, "Peach", ProductCategory.FRUIT, "120.00", "18.00", "3.00", placeholder);
            insert(c, "Pear", ProductCategory.FRUIT, "75.00", "30.00", "4.00", placeholder);
            insert(c, "Pineapple", ProductCategory.FRUIT, "150.00", "10.00", "2.00", placeholder);
            insert(c, "Strawberry", ProductCategory.FRUIT, "190.00", "12.00", "2.00", placeholder);
        }
    }

    public List<ProductRecord> fetchAllActiveSorted() throws Exception {
        String sql = """
                SELECT id, name, category, price_per_kg, stock_kg, threshold_kg, image_blob, active
                FROM product_info
                WHERE active = TRUE
                ORDER BY name ASC
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            List<ProductRecord> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new ProductRecord(
                        rs.getLong("id"),
                        rs.getString("name"),
                        ProductCategory.valueOf(rs.getString("category")),
                        rs.getBigDecimal("price_per_kg"),
                        rs.getBigDecimal("stock_kg"),
                        rs.getBigDecimal("threshold_kg"),
                        rs.getBytes("image_blob"),
                        rs.getBoolean("active")));
            }
            return out;
        }
    }

    public List<ProductRecord> fetchAllSorted() throws Exception {
        String sql = """
                SELECT id, name, category, price_per_kg, stock_kg, threshold_kg, image_blob, active
                FROM product_info
                ORDER BY name ASC
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            List<ProductRecord> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new ProductRecord(
                        rs.getLong("id"),
                        rs.getString("name"),
                        ProductCategory.valueOf(rs.getString("category")),
                        rs.getBigDecimal("price_per_kg"),
                        rs.getBigDecimal("stock_kg"),
                        rs.getBigDecimal("threshold_kg"),
                        rs.getBytes("image_blob"),
                        rs.getBoolean("active")));
            }
            return out;
        }
    }

    public Long findIdByName(String name) throws Exception {
        String sql = "SELECT id FROM product_info WHERE LOWER(name) = LOWER(?) LIMIT 1";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        }
    }

    public long createProduct(String name, ProductCategory category, BigDecimal pricePerKg,
            BigDecimal stockKg, BigDecimal thresholdKg, byte[] imageBytes, boolean active) throws Exception {
        // Auto-generate placeholder if no image provided
        if (imageBytes == null || imageBytes.length == 0) {
            imageBytes = makePlaceholderPng();
        }

        String sql = """
                INSERT INTO product_info (name, category, price_per_kg, stock_kg, threshold_kg, image_blob, active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, category.name());
            ps.setBigDecimal(3, pricePerKg);
            ps.setBigDecimal(4, stockKg);
            ps.setBigDecimal(5, thresholdKg);
            ps.setBytes(6, imageBytes);
            ps.setBoolean(7, active);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next())
                    throw new java.sql.SQLException("No generated key for product");
                return rs.getLong(1);
            }
        }
    }

    public void updateProduct(long id, String name, ProductCategory category, BigDecimal pricePerKg,
            BigDecimal stockKg, BigDecimal thresholdKg, boolean active) throws Exception {
        String sql = """
                UPDATE product_info
                SET name=?, category=?, price_per_kg=?, stock_kg=?, threshold_kg=?, active=?
                WHERE id=?
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, category.name());
            ps.setBigDecimal(3, pricePerKg);
            ps.setBigDecimal(4, stockKg);
            ps.setBigDecimal(5, thresholdKg);
            ps.setBoolean(6, active);
            ps.setLong(7, id);
            ps.executeUpdate();
        }
    }

    public void updateImage(long id, byte[] imageBytes) throws Exception {
        String sql = "UPDATE product_info SET image_blob=? WHERE id=?";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBytes(1, imageBytes);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    public void setActive(long id, boolean active) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE product_info SET active=? WHERE id=?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static int countProducts(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM product_info");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void insert(Connection c, String name, ProductCategory cat,
            String price, String stock, String threshold, byte[] img) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO product_info (name, category, price_per_kg, stock_kg, threshold_kg, image_blob, active)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                ON DUPLICATE KEY UPDATE name=name
                """)) {
            ps.setString(1, name);
            ps.setString(2, cat.name());
            ps.setBigDecimal(3, new BigDecimal(price));
            ps.setBigDecimal(4, new BigDecimal(stock));
            ps.setBigDecimal(5, new BigDecimal(threshold));
            ps.setBytes(6, img);
            ps.executeUpdate();
        }
    }

    public static byte[] makePlaceholderPng() throws Exception {
        BufferedImage img = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 240, 220));
        g.fillRect(0, 0, 96, 96);
        g.setColor(new Color(40, 80, 40));
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("IMG", 30, 55);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
