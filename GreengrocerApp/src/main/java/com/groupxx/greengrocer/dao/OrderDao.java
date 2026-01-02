package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.config.AppConfig;
import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.util.InvoicePdfGenerator;
import com.groupxx.greengrocer.dao.SettingsDao;
import com.groupxx.greengrocer.model.CartLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OrderDao {
    private static final Logger LOG = Logger.getLogger(OrderDao.class.getName());

    public void ensureOrderTables() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {

            try (PreparedStatement ps = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS order_info (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      customer_id BIGINT NOT NULL,
                      carrier_id BIGINT NULL,
                      order_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      requested_delivery_time DATETIME NOT NULL,
                      delivered_time DATETIME NULL,
                      is_canceled BOOLEAN NOT NULL DEFAULT 0,
                      is_delivered BOOLEAN NOT NULL DEFAULT 0,
                      coupon_code VARCHAR(32) NULL,
                      subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      vat DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      total_incl_vat DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      FOREIGN KEY (customer_id) REFERENCES user_info(id),
                      FOREIGN KEY (carrier_id) REFERENCES user_info(id)
                    )
                    """)) {
                ps.execute();
            }

            try (PreparedStatement ps = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS order_item (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      order_id BIGINT NOT NULL,
                      product_id BIGINT NOT NULL,
                      product_name VARCHAR(64) NOT NULL,
                      amount_kg DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      line_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
                      FOREIGN KEY (order_id) REFERENCES order_info(id),
                      FOREIGN KEY (product_id) REFERENCES product_info(id)
                    )
                    """)) {
                ps.execute();
            }

            ensureColumn(c, "order_info", "customer_id", "customer_id BIGINT NULL");
            ensureColumn(c, "order_info", "carrier_id", "carrier_id BIGINT NULL");
            ensureColumn(c, "order_info", "order_time", "order_time DATETIME NULL");
            ensureColumn(c, "order_info", "requested_delivery_time", "requested_delivery_time DATETIME NULL");
            ensureColumn(c, "order_info", "delivered_time", "delivered_time DATETIME NULL");
            ensureColumn(c, "order_info", "is_canceled", "is_canceled BOOLEAN NOT NULL DEFAULT 0");
            ensureColumn(c, "order_info", "is_delivered", "is_delivered BOOLEAN NOT NULL DEFAULT 0");
            ensureColumn(c, "order_info", "coupon_code", "coupon_code VARCHAR(32) NULL");
            ensureColumn(c, "order_info", "subtotal", "subtotal DECIMAL(10,2) NULL");
            ensureColumn(c, "order_info", "vat", "vat DECIMAL(10,2) NULL");
            ensureColumn(c, "order_info", "discount", "discount DECIMAL(10,2) NULL");
            ensureColumn(c, "order_info", "total_incl_vat", "total_incl_vat DECIMAL(10,2) NULL");

            // Invoice + rating
            ensureColumn(c, "order_info", "invoice_pdf", "invoice_pdf LONGBLOB NULL");
            ensureColumn(c, "order_info", "carrier_rating", "carrier_rating INT NULL");
            ensureColumn(c, "order_info", "carrier_comment", "carrier_comment VARCHAR(255) NULL");

            // Timestamps for reporting
            ensureColumn(c, "order_info", "created_at", "created_at DATETIME DEFAULT CURRENT_TIMESTAMP");

            // Backfill created_at from order_time for existing rows
            try (PreparedStatement ps = c
                    .prepareStatement("UPDATE order_info SET created_at = order_time WHERE created_at IS NULL")) {
                ps.executeUpdate();
            }

            ensureColumn(c, "order_item", "amount_kg", "amount_kg DECIMAL(10,2) NULL");

            migrateLegacyOrderData(c);
            normalizeOrderDefaults(c);
            hardenOrderColumns(c);
            relaxLegacyOrderColumns(c);
            ensureIndexes(c);
            ensureForeignKeys(c);
        }
    }

    public long placeOrder(String customerUsername, List<CartLine> lines,
            String couponCodeOrNull, LocalDateTime deliveryTs) throws Exception {

        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Cart is empty.");

        if (deliveryTs == null)
            throw new IllegalArgumentException("Delivery date/time is required.");

        try (Connection c = DbAdapter.getInstance().getConnection()) {
            c.setAutoCommit(false);

            try {
                Integer customerId = userIdByUsername(c, customerUsername);
                if (customerId == null) {
                    throw new IllegalStateException("Customer not found: " + customerUsername);
                }

                // 1) Check stock + update stock
                for (CartLine l : lines) {
                    BigDecimal stock = com.groupxx.greengrocer.util.BigDecimalUtil
                            .nz(getStockForUpdate(c, l.productId()));
                    BigDecimal kg = com.groupxx.greengrocer.util.BigDecimalUtil.nz(l.kg());
                    if (stock.compareTo(kg) < 0) {
                        throw new IllegalStateException("Not enough stock for: " + l.name());
                    }
                    BigDecimal newStock = stock.subtract(kg);
                    updateStock(c, l.productId(), newStock);
                }

                // 2) Compute totals (using cart unit prices shown to user)
                BigDecimal subtotal = scale2(
                        lines.stream().map(CartLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));

                // Read discounts from settings (owner-editable). Fallback to AppConfig
                // defaults.
                String couponCode = settingsGetString(c, SettingsDao.COUPON_CODE, AppConfig.COUPON_CODE);
                BigDecimal couponRate = settingsGetDecimal(c, SettingsDao.COUPON_RATE, AppConfig.COUPON_DISCOUNT_RATE);
                BigDecimal couponMin = settingsGetDecimal(c, SettingsDao.COUPON_MIN_SUBTOTAL,
                        AppConfig.COUPON_MIN_SUBTOTAL);

                BigDecimal loyaltyRate = settingsGetDecimal(c, SettingsDao.LOYALTY_RATE, new BigDecimal("0.00"));
                int loyaltyMinDelivered = settingsGetInt(c, SettingsDao.LOYALTY_MIN_DELIVERED_ORDERS, 0);

                String coupon = (couponCodeOrNull == null || couponCodeOrNull.isBlank()) ? null
                        : couponCodeOrNull.trim();

                BigDecimal couponDiscount = BigDecimal.ZERO;
                if (coupon != null && couponCode != null
                        && coupon.equalsIgnoreCase(couponCode)
                        && subtotal.compareTo(couponMin) >= 0) {
                    couponDiscount = scale2(subtotal.multiply(couponRate));
                }

                int deliveredCount = countDeliveredOrdersForCustomerId(c, customerId);
                BigDecimal loyaltyDiscount = BigDecimal.ZERO;
                if (loyaltyRate.compareTo(BigDecimal.ZERO) > 0
                        && loyaltyMinDelivered > 0
                        && deliveredCount >= loyaltyMinDelivered) {
                    BigDecimal afterCoupon = subtotal.subtract(couponDiscount);
                    if (afterCoupon.compareTo(BigDecimal.ZERO) < 0)
                        afterCoupon = BigDecimal.ZERO;
                    loyaltyDiscount = scale2(afterCoupon.multiply(loyaltyRate));
                }

                BigDecimal discount = scale2(couponDiscount.add(loyaltyDiscount));

                BigDecimal discountedSubtotal = subtotal.subtract(discount);
                BigDecimal vat = scale2(discountedSubtotal.multiply(AppConfig.VAT_RATE));
                BigDecimal total = scale2(discountedSubtotal.add(vat));

                long orderId;
                LocalDateTime orderTime = LocalDateTime.now();

                // 3) Insert order_info
                try (PreparedStatement ps = c.prepareStatement("""
                            INSERT INTO order_info (
                                customer_id,
                                carrier_id,
                                order_time,
                                requested_delivery_time,
                                delivered_time,
                                is_canceled,
                                is_delivered,
                                coupon_code,
                                subtotal,
                                vat,
                                discount,
                                total_incl_vat
                        )
                        VALUES (?, NULL, ?, ?, NULL, 0, 0, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {

                    int i = 1;
                    ps.setInt(i++, customerId);

                    ps.setTimestamp(i++, Timestamp.valueOf(orderTime));

                    Timestamp delTs = Timestamp.valueOf(deliveryTs);
                    ps.setTimestamp(i++, delTs); // requested_delivery_time

                    ps.setString(i++, coupon);
                    ps.setBigDecimal(i++, subtotal);
                    ps.setBigDecimal(i++, vat);
                    ps.setBigDecimal(i++, discount);
                    ps.setBigDecimal(i++, total); // total_incl_vat

                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next())
                            throw new SQLException("Failed to create order (no ID).");
                        orderId = rs.getLong(1);
                    }
                }

                // 4) Insert order items
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO order_item (order_id, product_id, product_name, amount_kg, unit_price, line_total)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    for (CartLine l : lines) {
                        ps.setLong(1, orderId);
                        ps.setLong(2, l.productId());
                        ps.setString(3, l.name());
                        ps.setBigDecimal(4, l.kg());
                        ps.setBigDecimal(5, l.unitPricePerKg());
                        ps.setBigDecimal(6, scale2(l.lineTotal()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // 5) Generate + store invoice PDF in DB
                try {
                    byte[] pdf = InvoicePdfGenerator.generate(
                            orderId,
                            customerUsername,
                            orderTime,
                            deliveryTs,
                            lines,
                            subtotal,
                            discount,
                            vat,
                            total);
                    try (PreparedStatement ps = c.prepareStatement("UPDATE order_info SET invoice_pdf=? WHERE id=?")) {
                        ps.setBytes(1, pdf);
                        ps.setLong(2, orderId);
                        ps.executeUpdate();
                    }
                } catch (Exception pdfEx) {
                    LOG.log(Level.WARNING, "Failed to generate invoice PDF for order=" + orderId, pdfEx);
                    // Invoice is best-effort: do not block checkout.
                }

                c.commit();
                return orderId;

            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    private static BigDecimal getStockForUpdate(Connection c, long productId) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT stock_kg FROM product_info WHERE id = ? FOR UPDATE
                """)) {
            ps.setLong(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new IllegalStateException("Product not found: " + productId);
                return rs.getBigDecimal(1);
            }
        }
    }

    private static void updateStock(Connection c, long productId, BigDecimal newStock) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE product_info SET stock_kg = ? WHERE id = ?
                """)) {
            ps.setBigDecimal(1, newStock);
            ps.setLong(2, productId);
            ps.executeUpdate();
        }
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    // ====== SECTION 4 (UPDATED): History / Cancel / Carrier / Owner queries
    // (username-based) ======

    public java.util.List<com.groupxx.greengrocer.model.OrderSummaryRecord> listOrdersForCustomerUsername(
            String customerUsername) throws java.sql.SQLException {
        String sql = """
                SELECT o.id,
                       o.customer_id,
                       u.username AS customer_username,
                       u.address  AS customer_address,
                       o.carrier_id,
                       c.username AS carrier_username,
                       o.order_time,
                       o.requested_delivery_time,
                       o.delivered_time,
                       o.is_canceled,
                       o.is_delivered,
                       o.total_incl_vat
                FROM order_info o
                JOIN user_info u ON u.id = o.customer_id
                LEFT JOIN user_info c ON c.id = o.carrier_id
                WHERE u.username = ?
                ORDER BY o.id DESC
                """;

        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setString(1, customerUsername);
            try (var rs = ps.executeQuery()) {
                var out = new java.util.ArrayList<com.groupxx.greengrocer.model.OrderSummaryRecord>();
                while (rs.next())
                    out.add(mapOrderSummary(rs));
                return out;
            }
        }
    }

    public boolean cancelOrderByUsername(String customerUsername, long orderId, long cancelWindowMinutes)
            throws java.sql.SQLException {
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                Integer customerId = userIdByUsername(conn, customerUsername);
                if (customerId == null) {
                    conn.rollback();
                    return false;
                }

                String sel = """
                        SELECT id, order_time, carrier_id, is_delivered, is_canceled, delivered_time
                        FROM order_info
                        WHERE id = ? AND customer_id = ?
                        FOR UPDATE
                        """;

                java.time.LocalDateTime orderTime;
                Integer carrierId;
                boolean delivered, canceled;
                java.time.LocalDateTime deliveredTime;

                try (var ps = conn.prepareStatement(sel)) {
                    ps.setLong(1, orderId);
                    ps.setInt(2, customerId);
                    try (var rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        var orderTs = rs.getTimestamp("order_time");
                        if (orderTs == null) {
                            conn.rollback();
                            return false;
                        }
                        orderTime = orderTs.toLocalDateTime();
                        carrierId = (Integer) rs.getObject("carrier_id");
                        delivered = rs.getBoolean("is_delivered");
                        canceled = rs.getBoolean("is_canceled");
                        var ts = rs.getTimestamp("delivered_time");
                        deliveredTime = (ts == null ? null : ts.toLocalDateTime());
                    }
                }

                if (canceled || delivered || deliveredTime != null) {
                    conn.rollback();
                    return false;
                }
                if (carrierId != null) {
                    conn.rollback();
                    return false;
                }

                long minutes = java.time.Duration.between(orderTime, java.time.LocalDateTime.now()).toMinutes();
                if (minutes < 0 || minutes > cancelWindowMinutes) {
                    conn.rollback();
                    return false;
                }

                // restore stock from order items
                String items = "SELECT product_id, amount_kg FROM order_item WHERE order_id = ?";
                try (var ps = conn.prepareStatement(items)) {
                    ps.setLong(1, orderId);
                    try (var rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int productId = rs.getInt("product_id");
                            java.math.BigDecimal kg = rs.getBigDecimal("amount_kg");
                            String upd = "UPDATE product_info SET stock_kg = stock_kg + ? WHERE id = ?";
                            try (var ups = conn.prepareStatement(upd)) {
                                ups.setBigDecimal(1, kg);
                                ups.setInt(2, productId);
                                ups.executeUpdate();
                            }
                        }
                    }
                }

                String cancel = "UPDATE order_info SET is_canceled = 1 WHERE id = ? AND customer_id = ?";
                try (var ps = conn.prepareStatement(cancel)) {
                    ps.setLong(1, orderId);
                    ps.setInt(2, customerId);
                    if (ps.executeUpdate() != 1) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public java.util.List<com.groupxx.greengrocer.model.OrderSummaryRecord> listAvailableOrders()
            throws java.sql.SQLException {
        String sql = """
                SELECT o.id,
                       o.customer_id,
                       u.username AS customer_username,
                       u.address  AS customer_address,
                       o.carrier_id,
                       c.username AS carrier_username,
                       o.order_time,
                       o.requested_delivery_time,
                       o.delivered_time,
                       o.is_canceled,
                       o.is_delivered,
                       o.total_incl_vat
                FROM order_info o
                JOIN user_info u ON u.id = o.customer_id
                LEFT JOIN user_info c ON c.id = o.carrier_id
                WHERE o.carrier_id IS NULL AND o.is_delivered = 0 AND o.is_canceled = 0
                ORDER BY o.id ASC
                """;
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection();
                var ps = conn.prepareStatement(sql);
                var rs = ps.executeQuery()) {
            var out = new java.util.ArrayList<com.groupxx.greengrocer.model.OrderSummaryRecord>();
            while (rs.next())
                out.add(mapOrderSummary(rs));
            return out;
        }
    }

    public java.util.List<com.groupxx.greengrocer.model.OrderSummaryRecord> listCurrentOrdersForCarrierUsername(
            String carrierUsername) throws java.sql.SQLException {
        String sql = """
                SELECT o.id,
                       o.customer_id,
                       u.username AS customer_username,
                       u.address  AS customer_address,
                       o.carrier_id,
                       c.username AS carrier_username,
                       o.order_time,
                       o.requested_delivery_time,
                       o.delivered_time,
                       o.is_canceled,
                       o.is_delivered,
                       o.total_incl_vat
                FROM order_info o
                JOIN user_info u ON u.id = o.customer_id
                JOIN user_info c ON c.id = o.carrier_id
                WHERE c.username = ? AND o.is_delivered = 0 AND o.is_canceled = 0
                ORDER BY o.id ASC
                """;
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setString(1, carrierUsername);
            try (var rs = ps.executeQuery()) {
                var out = new java.util.ArrayList<com.groupxx.greengrocer.model.OrderSummaryRecord>();
                while (rs.next())
                    out.add(mapOrderSummary(rs));
                return out;
            }
        }
    }

    public java.util.List<com.groupxx.greengrocer.model.OrderSummaryRecord> listCompletedOrdersForCarrierUsername(
            String carrierUsername) throws java.sql.SQLException {
        String sql = """
                SELECT o.id,
                       o.customer_id,
                       u.username AS customer_username,
                       u.address  AS customer_address,
                       o.carrier_id,
                       c.username AS carrier_username,
                       o.order_time,
                       o.requested_delivery_time,
                       o.delivered_time,
                       o.is_canceled,
                       o.is_delivered,
                       o.total_incl_vat
                FROM order_info o
                JOIN user_info u ON u.id = o.customer_id
                JOIN user_info c ON c.id = o.carrier_id
                WHERE c.username = ? AND o.is_delivered = 1
                ORDER BY o.id DESC
                """;
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection();
                var ps = conn.prepareStatement(sql)) {
            ps.setString(1, carrierUsername);
            try (var rs = ps.executeQuery()) {
                var out = new java.util.ArrayList<com.groupxx.greengrocer.model.OrderSummaryRecord>();
                while (rs.next())
                    out.add(mapOrderSummary(rs));
                return out;
            }
        }
    }

    public boolean claimOrderByCarrierUsername(String carrierUsername, long orderId) throws java.sql.SQLException {
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection()) {
            Integer carrierId = userIdByUsername(conn, carrierUsername);
            if (carrierId == null)
                return false;

            String sql = """
                    UPDATE order_info
                    SET carrier_id = ?
                    WHERE id = ?
                      AND carrier_id IS NULL
                      AND is_delivered = 0
                      AND is_canceled = 0
                    """;
            try (var ps = conn.prepareStatement(sql)) {
                ps.setInt(1, carrierId);
                ps.setLong(2, orderId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    public boolean markDeliveredByCarrierUsername(String carrierUsername, long orderId,
            java.time.LocalDateTime deliveredAt) throws java.sql.SQLException {
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection()) {
            Integer carrierId = userIdByUsername(conn, carrierUsername);
            if (carrierId == null)
                return false;

            String sql = """
                    UPDATE order_info
                    SET is_delivered = 1,
                        delivered_time = ?
                    WHERE id = ?
                      AND carrier_id = ?
                      AND is_delivered = 0
                      AND is_canceled = 0
                    """;
            try (var ps = conn.prepareStatement(sql)) {
                ps.setTimestamp(1, java.sql.Timestamp.valueOf(deliveredAt));
                ps.setLong(2, orderId);
                ps.setInt(3, carrierId);
                return ps.executeUpdate() == 1;
            }
        }
    }

    public java.util.List<com.groupxx.greengrocer.model.OrderSummaryRecord> listAllOrders()
            throws java.sql.SQLException {
        String sql = """
                SELECT o.id,
                       o.customer_id,
                       u.username AS customer_username,
                       u.address  AS customer_address,
                       o.carrier_id,
                       c.username AS carrier_username,
                       o.order_time,
                       o.requested_delivery_time,
                       o.delivered_time,
                       o.is_canceled,
                       o.is_delivered,
                       o.total_incl_vat
                FROM order_info o
                JOIN user_info u ON u.id = o.customer_id
                LEFT JOIN user_info c ON c.id = o.carrier_id
                ORDER BY o.id DESC
                """;
        try (var conn = com.groupxx.greengrocer.db.DbAdapter.getInstance().getConnection();
                var ps = conn.prepareStatement(sql);
                var rs = ps.executeQuery()) {
            var out = new java.util.ArrayList<com.groupxx.greengrocer.model.OrderSummaryRecord>();
            while (rs.next())
                out.add(mapOrderSummary(rs));
            return out;
        }
    }

    private Integer userIdByUsername(java.sql.Connection conn, String username) throws java.sql.SQLException {
        if (username == null || username.isBlank())
            return null;
        String sql = "SELECT id FROM user_info WHERE username = ? LIMIT 1";
        try (var ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                if (!rs.next())
                    return null;
                return rs.getInt(1);
            }
        }
    }

    // Helper: ResultSet -> OrderSummaryRecord
    private com.groupxx.greengrocer.model.OrderSummaryRecord mapOrderSummary(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        long id = rs.getLong("id");

        int custId = rs.getInt("customer_id");
        String custUser = rs.getString("customer_username");
        String custAddr = rs.getString("customer_address");

        Object carrierObj = rs.getObject("carrier_id");
        Integer carrierId = (carrierObj == null) ? null : ((Number) carrierObj).intValue();
        String carrierUser = rs.getString("carrier_username");

        java.sql.Timestamp tsOrder = rs.getTimestamp("order_time");
        java.time.LocalDateTime orderTime = (tsOrder == null) ? null : tsOrder.toLocalDateTime();

        java.sql.Timestamp tsReq = rs.getTimestamp("requested_delivery_time");
        java.time.LocalDateTime req = (tsReq == null) ? null : tsReq.toLocalDateTime();

        java.sql.Timestamp tsDel = rs.getTimestamp("delivered_time");
        java.time.LocalDateTime deliveredTime = (tsDel == null) ? null : tsDel.toLocalDateTime();

        boolean canceled = rs.getBoolean("is_canceled");
        boolean delivered = rs.getBoolean("is_delivered");

        java.math.BigDecimal total = rs.getBigDecimal("total_incl_vat");

        return new com.groupxx.greengrocer.model.OrderSummaryRecord(
                id,
                custId,
                custUser,
                custAddr,
                carrierId,
                carrierUser,
                orderTime,
                req,
                deliveredTime,
                canceled,
                delivered,
                total);
    }

    // ---------- Invoice + rating API ----------

    public byte[] loadInvoicePdfForCustomerUsername(String customerUsername, long orderId) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            Integer customerId = userIdByUsername(c, customerUsername);
            if (customerId == null)
                return null;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT invoice_pdf FROM order_info WHERE id=? AND customer_id=?
                    """)) {
                ps.setLong(1, orderId);
                ps.setInt(2, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return null;
                    return rs.getBytes(1);
                }
            }
        }
    }

    public Integer getCarrierRatingForCustomerUsername(String customerUsername, long orderId) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            Integer customerId = userIdByUsername(c, customerUsername);
            if (customerId == null)
                return null;
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT carrier_rating FROM order_info WHERE id=? AND customer_id=?
                    """)) {
                ps.setLong(1, orderId);
                ps.setInt(2, customerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next())
                        return null;
                    Object v = rs.getObject(1);
                    return (v == null) ? null : ((Number) v).intValue();
                }
            }
        }
    }

    public int countDeliveredOrdersForCustomerUsername(String customerUsername) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            Integer customerId = userIdByUsername(c, customerUsername);
            if (customerId == null)
                return 0;
            return countDeliveredOrdersForCustomerId(c, customerId);
        }
    }

    public java.util.List<com.groupxx.greengrocer.model.DailySalesRecord> listDailySalesLastDays(int days)
            throws Exception {
        int d = Math.max(1, Math.min(days, 90));
        String sql = """
                SELECT DATE(created_at) AS day,
                       COUNT(*) AS orders,
                       COALESCE(SUM(total_incl_vat), 0) AS revenue
                FROM order_info
                WHERE created_at >= (NOW() - INTERVAL ? DAY)
                  AND is_canceled = 0
                GROUP BY DATE(created_at)
                ORDER BY DATE(created_at) ASC
                """;

        java.util.List<com.groupxx.greengrocer.model.DailySalesRecord> out = new java.util.ArrayList<>();
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, d);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date dt = rs.getDate("day");
                    out.add(new com.groupxx.greengrocer.model.DailySalesRecord(
                            dt.toLocalDate(),
                            rs.getInt("orders"),
                            rs.getBigDecimal("revenue")));
                }
            }
        }
        return out;
    }

    public boolean rateCarrierByCustomerUsername(String customerUsername, long orderId, int rating, String comment)
            throws Exception {
        if (rating < 1 || rating > 5)
            throw new IllegalArgumentException("Rating must be 1..5");
        if (comment == null)
            comment = "";
        comment = comment.trim();
        if (comment.length() > 255)
            comment = comment.substring(0, 255);

        try (Connection c = DbAdapter.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer customerId = userIdByUsername(c, customerUsername);
                if (customerId == null)
                    return false;

                // Only delivered, assigned orders; do not allow double-rating.
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT carrier_id, is_delivered, is_canceled, carrier_rating
                        FROM order_info
                        WHERE id=? AND customer_id=?
                        FOR UPDATE
                        """)) {
                    ps.setLong(1, orderId);
                    ps.setInt(2, customerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next())
                            return false;
                        Object carrierObj = rs.getObject("carrier_id");
                        boolean delivered = rs.getBoolean("is_delivered");
                        boolean canceled = rs.getBoolean("is_canceled");
                        Object existing = rs.getObject("carrier_rating");
                        if (carrierObj == null)
                            return false;
                        if (!delivered || canceled)
                            return false;
                        if (existing != null)
                            return false;
                    }
                }

                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE order_info
                        SET carrier_rating=?, carrier_comment=?
                        WHERE id=? AND customer_id=? AND carrier_rating IS NULL
                        """)) {
                    ps.setInt(1, rating);
                    ps.setString(2, comment);
                    ps.setLong(3, orderId);
                    ps.setInt(4, customerId);
                    int updated = ps.executeUpdate();
                    c.commit();
                    return updated == 1;
                }
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }
        }
    }

    /**
     * List all carrier reviews (ratings + comments) for display in Owner tab.
     * Returns reviews ordered by most recent first.
     */
    public java.util.List<com.groupxx.greengrocer.model.CarrierReviewRecord> listCarrierReviews() throws Exception {
        String sql = """
                SELECT o.id AS order_id,
                       cust.username AS customer_username,
                       carr.username AS carrier_username,
                       o.carrier_rating,
                       o.carrier_comment,
                       o.delivered_time
                FROM order_info o
                JOIN user_info cust ON cust.id = o.customer_id
                JOIN user_info carr ON carr.id = o.carrier_id
                WHERE o.carrier_rating IS NOT NULL
                ORDER BY o.delivered_time DESC
                """;

        java.util.List<com.groupxx.greengrocer.model.CarrierReviewRecord> out = new java.util.ArrayList<>();
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.sql.Timestamp ts = rs.getTimestamp("delivered_time");
                java.time.LocalDateTime reviewTime = (ts == null) ? null : ts.toLocalDateTime();
                out.add(new com.groupxx.greengrocer.model.CarrierReviewRecord(
                        rs.getLong("order_id"),
                        rs.getString("customer_username"),
                        rs.getString("carrier_username"),
                        rs.getInt("carrier_rating"),
                        rs.getString("carrier_comment"),
                        reviewTime));
            }
        }
        return out;
    }

    /**
     * Counts orders assigned to this carrier that are not yet delivered (active
     * deliveries).
     * Used to prevent deactivating a carrier while they have deliveries in
     * progress.
     */
    public int countActiveOrdersForCarrierId(long carrierId) throws Exception {
        String sql = """
                SELECT COUNT(*) FROM order_info
                WHERE carrier_id = ? AND is_canceled = 0 AND is_delivered = 0
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, carrierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---------- Discount helpers (settings_info) ----------

    private static String settingsGetString(Connection c, String key, String defaultVal) {
        if (key == null || key.isBlank())
            return defaultVal;
        try (PreparedStatement ps = c.prepareStatement("SELECT v FROM settings_info WHERE k=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return defaultVal;
                String v = rs.getString(1);
                return (v == null || v.isBlank()) ? defaultVal : v;
            }
        } catch (Exception ignore) {
            return defaultVal;
        }
    }

    private static BigDecimal settingsGetDecimal(Connection c, String key, BigDecimal defaultVal) {
        String s = settingsGetString(c, key, null);
        if (s == null)
            return defaultVal;
        try {
            return new BigDecimal(s);
        } catch (Exception ignore) {
            return defaultVal;
        }
    }

    private static int settingsGetInt(Connection c, String key, int defaultVal) {
        String s = settingsGetString(c, key, null);
        if (s == null)
            return defaultVal;
        try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return defaultVal;
        }
    }

    private static int countDeliveredOrdersForCustomerId(Connection c, int customerId) {
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT COUNT(*)
                FROM order_info
                WHERE customer_id=? AND is_delivered=1 AND is_canceled=0
                """)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return 0;
                return rs.getInt(1);
            }
        } catch (Exception ignore) {
            return 0;
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) throws SQLException {
        if (columnExists(c, table, column)) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement("ALTER TABLE " + table + " ADD COLUMN " + definition)) {
            ps.execute();
        }
    }

    private static boolean columnHasNulls(Connection c, String table, String column) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + table + " WHERE " + column + " IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private static void ensureNotNullIfClean(Connection c, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(c, table, column)) {
            return;
        }
        if (columnHasNulls(c, table, column)) {
            LOG.warning("Skipping NOT NULL enforcement for " + table + "." + column + " due to NULL data.");
            return;
        }
        String sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static void ensureNullableIfExists(Connection c, String table, String column, String definition)
            throws SQLException {
        if (!columnExists(c, table, column)) {
            return;
        }
        String sql = "ALTER TABLE " + table + " MODIFY COLUMN " + column + " " + definition;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static void ensureIndex(Connection c, String table, String indexName, String createSql)
            throws SQLException {
        if (indexExists(c, table, indexName)) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(createSql)) {
            ps.execute();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Failed to create index " + indexName + " on " + table, ex);
        }
    }

    private static boolean indexExists(Connection c, String table, String indexName) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getIndexInfo(c.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (indexName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void ensureForeignKey(Connection c, String table, String fkName, String createSql)
            throws SQLException {
        if (foreignKeyExists(c, table, fkName)) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(createSql)) {
            ps.execute();
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Failed to create foreign key " + fkName + " on " + table, ex);
        }
    }

    private static boolean foreignKeyExists(Connection c, String table, String fkName) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getImportedKeys(c.getCatalog(), null, table)) {
            while (rs.next()) {
                String name = rs.getString("FK_NAME");
                if (fkName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void migrateLegacyOrderData(Connection c) throws SQLException {
        boolean hasCustomerUsername = columnExists(c, "order_info", "customer_username");
        boolean hasCustomerId = columnExists(c, "order_info", "customer_id");
        if (hasCustomerUsername && hasCustomerId) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info o
                    JOIN user_info u ON u.username = o.customer_username
                    SET o.customer_id = u.id
                    WHERE o.customer_id IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "delivery_ts")
                && columnExists(c, "order_info", "requested_delivery_time")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET requested_delivery_time = delivery_ts
                    WHERE requested_delivery_time IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "created_at") && columnExists(c, "order_info", "order_time")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET order_time = created_at
                    WHERE order_time IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "total") && columnExists(c, "order_info", "total_incl_vat")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET total_incl_vat = total
                    WHERE total_incl_vat IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "delivered_ts") && columnExists(c, "order_info", "delivered_time")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET delivered_time = delivered_ts
                    WHERE delivered_time IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "delivered_time") && columnExists(c, "order_info", "is_delivered")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET is_delivered = 1
                    WHERE delivered_time IS NOT NULL AND is_delivered = 0
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "status") && columnExists(c, "order_info", "is_canceled")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info
                    SET is_canceled = 1
                    WHERE status = 'CANCELLED' AND is_canceled = 0
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_info", "carrier_username") && columnExists(c, "order_info", "carrier_id")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_info o
                    JOIN user_info u ON u.username = o.carrier_username
                    SET o.carrier_id = u.id
                    WHERE o.carrier_id IS NULL AND o.carrier_username IS NOT NULL
                    """)) {
                ps.executeUpdate();
            }
        }

        if (columnExists(c, "order_item", "kg") && columnExists(c, "order_item", "amount_kg")) {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE order_item
                    SET amount_kg = kg
                    WHERE amount_kg IS NULL
                    """)) {
                ps.executeUpdate();
            }
        }
    }

    private static void normalizeOrderDefaults(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE order_info
                SET order_time = COALESCE(order_time, CURRENT_TIMESTAMP)
                WHERE order_time IS NULL
                """)) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE order_info
                SET requested_delivery_time = COALESCE(requested_delivery_time, order_time, CURRENT_TIMESTAMP)
                WHERE requested_delivery_time IS NULL
                """)) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE order_info
                SET subtotal = COALESCE(subtotal, 0.00),
                    vat = COALESCE(vat, 0.00),
                    discount = COALESCE(discount, 0.00),
                    total_incl_vat = COALESCE(total_incl_vat, 0.00)
                WHERE subtotal IS NULL
                   OR vat IS NULL
                   OR discount IS NULL
                   OR total_incl_vat IS NULL
                """)) {
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE order_item
                SET amount_kg = COALESCE(amount_kg, 0.00),
                    unit_price = COALESCE(unit_price, 0.00),
                    line_total = COALESCE(line_total, 0.00)
                WHERE amount_kg IS NULL
                   OR unit_price IS NULL
                   OR line_total IS NULL
                """)) {
            ps.executeUpdate();
        }
    }

    private static void hardenOrderColumns(Connection c) throws SQLException {
        ensureNotNullIfClean(c, "order_info", "customer_id", "BIGINT NOT NULL");
        ensureNotNullIfClean(c, "order_info", "order_time", "DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
        ensureNotNullIfClean(c, "order_info", "requested_delivery_time", "DATETIME NOT NULL");
        ensureNotNullIfClean(c, "order_info", "subtotal", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        ensureNotNullIfClean(c, "order_info", "vat", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        ensureNotNullIfClean(c, "order_info", "discount", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        ensureNotNullIfClean(c, "order_info", "total_incl_vat", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");

        ensureNotNullIfClean(c, "order_item", "order_id", "BIGINT NOT NULL");
        ensureNotNullIfClean(c, "order_item", "product_id", "BIGINT NOT NULL");
        ensureNotNullIfClean(c, "order_item", "amount_kg", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        ensureNotNullIfClean(c, "order_item", "unit_price", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        ensureNotNullIfClean(c, "order_item", "line_total", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
    }

    private static void relaxLegacyOrderColumns(Connection c) throws SQLException {
        ensureNullableIfExists(c, "order_info", "customer_username", "VARCHAR(32) NULL");
        ensureNullableIfExists(c, "order_info", "carrier_username", "VARCHAR(32) NULL");
    }

    private static void ensureIndexes(Connection c) throws SQLException {
        ensureIndex(c, "order_info", "idx_order_info_customer",
                "CREATE INDEX idx_order_info_customer ON order_info (customer_id)");
        ensureIndex(c, "order_info", "idx_order_info_carrier",
                "CREATE INDEX idx_order_info_carrier ON order_info (carrier_id)");
        ensureIndex(c, "order_info", "idx_order_info_order_time",
                "CREATE INDEX idx_order_info_order_time ON order_info (order_time)");
        ensureIndex(c, "order_item", "idx_order_item_order",
                "CREATE INDEX idx_order_item_order ON order_item (order_id)");
        ensureIndex(c, "order_item", "idx_order_item_product",
                "CREATE INDEX idx_order_item_product ON order_item (product_id)");
    }

    private static void ensureForeignKeys(Connection c) throws SQLException {
        ensureForeignKey(c, "order_info", "fk_order_info_customer",
                "ALTER TABLE order_info ADD CONSTRAINT fk_order_info_customer FOREIGN KEY (customer_id) REFERENCES user_info(id)");
        ensureForeignKey(c, "order_info", "fk_order_info_carrier",
                "ALTER TABLE order_info ADD CONSTRAINT fk_order_info_carrier FOREIGN KEY (carrier_id) REFERENCES user_info(id)");
        ensureForeignKey(c, "order_item", "fk_order_item_order",
                "ALTER TABLE order_item ADD CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES order_info(id)");
        ensureForeignKey(c, "order_item", "fk_order_item_product",
                "ALTER TABLE order_item ADD CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product_info(id)");
    }

}
