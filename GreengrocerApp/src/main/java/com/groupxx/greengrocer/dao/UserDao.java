package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.model.Role;
import com.groupxx.greengrocer.model.UserRecord;
import com.groupxx.greengrocer.model.CustomerSummaryRecord;
import com.groupxx.greengrocer.model.CarrierSummaryRecord;
import com.groupxx.greengrocer.util.PasswordHasher;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserDao {

    public void ensureUserTableAndSeed() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            // Create table (safe if exists)
            try (PreparedStatement ps = c.prepareStatement("""
                    CREATE TABLE IF NOT EXISTS user_info (
                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                      username VARCHAR(32) NOT NULL UNIQUE,
                      password_hash_b64 VARCHAR(64) NOT NULL,
                      salt_b64 VARCHAR(24) NOT NULL,
                      role ENUM('CUSTOMER','CARRIER','OWNER') NOT NULL,
                      address VARCHAR(255) NULL,
                      phone VARCHAR(32) NULL,
                      email VARCHAR(96) NULL,
                      deleted BOOLEAN NOT NULL DEFAULT 0,
                      deleted_at TIMESTAMP NULL,
                      active BOOLEAN NOT NULL DEFAULT 1,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    )
                    """)) {
                ps.execute();
            }

            ensureColumn(c, "user_info", "email", "email VARCHAR(96) NULL");
            ensureColumn(c, "user_info", "deleted", "deleted BOOLEAN NOT NULL DEFAULT 0");
            ensureColumn(c, "user_info", "deleted_at", "deleted_at TIMESTAMP NULL");
            ensureColumn(c, "user_info", "active", "active BOOLEAN NOT NULL DEFAULT 1");
            try (PreparedStatement ps = c.prepareStatement("UPDATE user_info SET active=1 WHERE active IS NULL")) {
                ps.executeUpdate();
            }

            // Seed required demo users if missing
            seedIfMissing(c, "cust", "cust", Role.CUSTOMER);
            seedIfMissing(c, "carr", "carr", Role.CARRIER);
            seedIfMissing(c, "own", "own", Role.OWNER);
        }
    }

    private void seedIfMissing(Connection c, String username, String plainPassword, Role role) throws Exception {
        if (usernameExists(username))
            return;

        String saltB64 = PasswordHasher.newSaltB64();
        String hashB64 = PasswordHasher.hashB64(plainPassword.toCharArray(), saltB64);

        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO user_info (username, password_hash_b64, salt_b64, role, address, phone)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, username);
            ps.setString(2, hashB64);
            ps.setString(3, saltB64);
            ps.setString(4, role.name());
            ps.setString(5, "Demo Address");
            ps.setString(6, "+90 000 000 0000");
            ps.executeUpdate();
        }
    }

    public Optional<UserRecord> authenticate(String username, char[] password) throws Exception {
        String sql = """
                SELECT id, username, password_hash_b64, salt_b64, role, address, phone, email, deleted, active
                FROM user_info
                WHERE username = ?
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();

                if (rs.getBoolean("deleted"))
                    return Optional.empty();

                if (!rs.getBoolean("active"))
                    return Optional.empty();

                String saltB64 = rs.getString("salt_b64");
                String hashB64 = rs.getString("password_hash_b64");

                boolean ok = PasswordHasher.verify(password, saltB64, hashB64);
                if (!ok)
                    return Optional.empty();

                boolean active = rs.getBoolean("active");
                if (!active)
                    return Optional.empty();

                Role role = Role.valueOf(rs.getString("role"));
                return Optional.of(new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        role,
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getString("email")));
            }
        }
    }

    public boolean usernameExists(String username) throws Exception {
        String sql = "SELECT 1 FROM user_info WHERE username = ? LIMIT 1";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void createCustomer(String username, char[] password, String address, String phoneOrNull) throws Exception {
        String saltB64 = PasswordHasher.newSaltB64();
        String hashB64 = PasswordHasher.hashB64(password, saltB64);

        String sql = """
                INSERT INTO user_info (username, password_hash_b64, salt_b64, role, address, phone)
                VALUES (?, ?, ?, 'CUSTOMER', ?, ?)
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashB64);
            ps.setString(3, saltB64);
            ps.setString(4, address);
            ps.setString(5, phoneOrNull);
            ps.executeUpdate();
        }
    }

    // ---------- Owner helpers ----------

    public void createCarrier(String username, char[] password) throws Exception {
        createUser(username, password, Role.CARRIER, "", null);
    }

    public void setUserActive(long userId, boolean active) throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement("UPDATE user_info SET active=? WHERE id=?")) {
            ps.setBoolean(1, active);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    public List<CarrierSummaryRecord> listCarriersWithAvgRating() throws Exception {
        String sql = """
                SELECT u.id, u.username, u.active,
                       AVG(o.carrier_rating) AS avg_rating
                FROM user_info u
                LEFT JOIN order_info o
                  ON o.carrier_id = u.id AND o.carrier_rating IS NOT NULL
                WHERE u.role = 'CARRIER' AND u.deleted = 0
                GROUP BY u.id, u.username, u.active
                ORDER BY u.username ASC
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<CarrierSummaryRecord> out = new ArrayList<>();
            while (rs.next()) {
                Object avgObj = rs.getObject("avg_rating");
                Double avg = (avgObj == null) ? null : ((Number) avgObj).doubleValue();
                out.add(new CarrierSummaryRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getBoolean("active"),
                        avg));
            }
            return out;
        }
    }

    public List<UserRecord> listCustomers() throws Exception {
        String sql = """
                SELECT id, username, role, address, phone, email, active
                FROM user_info
                WHERE role='CUSTOMER'
                ORDER BY username
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<UserRecord> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getString("email")));
            }
            return out;
        }
    }

    public List<UserRecord> listAllCustomers() throws Exception {
        String sql = "SELECT id, username, role, address, phone, email FROM user_info WHERE role='CUSTOMER' ORDER BY username ASC";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<UserRecord> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getString("email")));
            }
            return out;
        }
    }

    private void createUser(String username, char[] password, Role role, String address, String phoneOrNull)
            throws Exception {
        if (username == null)
            username = "";
        username = username.trim();
        if (username.isEmpty())
            throw new IllegalArgumentException("Username is empty");
        if (username.length() > 32)
            throw new IllegalArgumentException("Username too long");
        if (usernameExists(username))
            throw new IllegalArgumentException("Username already exists");

        String saltB64 = PasswordHasher.newSaltB64();
        String hashB64 = PasswordHasher.hashB64(password, saltB64);

        String sql = """
                INSERT INTO user_info (username, password_hash_b64, salt_b64, role, address, phone, active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hashB64);
            ps.setString(3, saltB64);
            ps.setString(4, role.name());
            ps.setString(5, address);
            ps.setString(6, phoneOrNull);
            ps.executeUpdate();
        }
    }

    /** Load a user by username (only if not deleted). */
    public Optional<UserRecord> findByUsername(String username) throws Exception {
        String sql = "SELECT id, username, role, address, phone, email FROM user_info WHERE username=? AND deleted=0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return Optional.empty();
                return Optional.of(new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getString("email")));
            }
        }
    }

    /** Update profile information (address/phone/email). */
    public void updateProfile(long userId, String address, String phone, String email) throws Exception {
        String sql = "UPDATE user_info SET address=?, phone=?, email=? WHERE id=? AND deleted=0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, address == null || address.isBlank() ? null : address.trim());
            ps.setString(2, phone == null || phone.isBlank() ? null : phone.trim());
            ps.setString(3, email == null || email.isBlank() ? null : email.trim());
            ps.setLong(4, userId);
            ps.executeUpdate();
        }
    }

    /** Verify if the given password is correct for the user. */
    public boolean verifyPassword(long userId, char[] password) throws Exception {
        String sql = "SELECT password_hash_b64, salt_b64 FROM user_info WHERE id=? AND deleted=0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    return false;
                String saltB64 = rs.getString("salt_b64");
                String hashB64 = rs.getString("password_hash_b64");
                return PasswordHasher.verify(password, saltB64, hashB64);
            }
        }
    }

    /** Update username if the new username is not already taken. */
    public void updateUsername(long userId, String newUsername) throws Exception {
        if (newUsername == null || newUsername.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        newUsername = newUsername.trim();
        if (newUsername.length() > 32) {
            throw new IllegalArgumentException("Username too long (max 32 characters)");
        }
        // Check if new username is already taken by another user
        String checkSql = "SELECT id FROM user_info WHERE username=? AND id<>?";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement checkPs = c.prepareStatement(checkSql)) {
            checkPs.setString(1, newUsername);
            checkPs.setLong(2, userId);
            try (ResultSet rs = checkPs.executeQuery()) {
                if (rs.next()) {
                    throw new IllegalArgumentException("Username already taken");
                }
            }
        }
        // Update username
        String sql = "UPDATE user_info SET username=? WHERE id=? AND deleted=0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newUsername);
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /** Update password (generates new salt and hash). */
    public void updatePassword(long userId, char[] newPassword) throws Exception {
        String saltB64 = PasswordHasher.newSaltB64();
        String hashB64 = PasswordHasher.hashB64(newPassword, saltB64);
        String sql = "UPDATE user_info SET password_hash_b64=?, salt_b64=? WHERE id=? AND deleted=0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, hashB64);
            ps.setString(2, saltB64);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    /** Soft-delete a carrier (persists across logouts). */
    public void deleteCarrier(long carrierId) throws Exception {
        String sql = "UPDATE user_info SET deleted=1, active=0, deleted_at=CURRENT_TIMESTAMP WHERE id=? AND role='CARRIER'";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, carrierId);
            ps.executeUpdate();
        }
    }

    /** Undo carrier deletion (persists across logouts). */
    public void undoDeleteCarrier(long carrierId) throws Exception {
        String sql = "UPDATE user_info SET deleted=0, active=1, deleted_at=NULL WHERE id=? AND role='CARRIER'";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, carrierId);
            ps.executeUpdate();
        }
    }

    /** List carriers that are soft-deleted (for the Owner's undo UI). */
    public List<CarrierSummaryRecord> listDeletedCarriersWithAvgRating() throws Exception {
        String sql = """
                SELECT u.id, u.username, u.active,
                       AVG(o.carrier_rating) AS avg_rating
                FROM user_info u
                LEFT JOIN order_info o
                  ON o.carrier_id = u.id AND o.carrier_rating IS NOT NULL
                WHERE u.role = 'CARRIER' AND u.deleted = 1
                GROUP BY u.id, u.username, u.active
                ORDER BY u.username ASC
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<CarrierSummaryRecord> out = new ArrayList<>();
            while (rs.next()) {
                Object avgObj = rs.getObject("avg_rating");
                Double avg = (avgObj == null) ? null : ((Number) avgObj).doubleValue();
                out.add(new CarrierSummaryRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getBoolean("active"),
                        avg));
            }
            return out;
        }
    }

    private static boolean columnExists(Connection c, String table, String column) throws Exception {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(c.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private static void ensureColumn(Connection c, String table, String column, String definition) throws Exception {
        if (columnExists(c, table, column))
            return;
        try (PreparedStatement ps = c.prepareStatement("ALTER TABLE " + table + " ADD COLUMN " + definition)) {
            ps.execute();
        }
    }

    public List<CustomerSummaryRecord> listCustomersWithOrderCounts() throws Exception {
        String sql = """
                SELECT u.id, u.username, u.role, u.address, u.phone, u.email, u.active, COUNT(o.id) AS order_cnt
                FROM user_info u
                LEFT JOIN order_info o ON o.customer_id = u.id
                WHERE u.role = 'CUSTOMER'
                GROUP BY u.id, u.username, u.role, u.address, u.phone, u.email, u.active
                ORDER BY u.username ASC
                """;

        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            List<CustomerSummaryRecord> out = new ArrayList<>();
            while (rs.next()) {
                UserRecord u = new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        Role.valueOf(rs.getString("role")),
                        rs.getString("address"),
                        rs.getString("phone"),
                        rs.getString("email"));
                int cnt = rs.getInt("order_cnt");
                out.add(new CustomerSummaryRecord(u, cnt));
            }
            return out;
        }
    }

    /** Count total customers (non-deleted). */
    public int countCustomers() throws Exception {
        String sql = "SELECT COUNT(*) FROM user_info WHERE role = 'CUSTOMER' AND deleted = 0";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** List all carriers (including inactive) with average rating. */
    public List<CarrierSummaryRecord> listAllCarriersWithAvgRating() throws Exception {
        String sql = """
                SELECT u.id, u.username, u.active,
                       AVG(o.carrier_rating) AS avg_rating
                FROM user_info u
                LEFT JOIN order_info o
                  ON o.carrier_id = u.id AND o.carrier_rating IS NOT NULL
                WHERE u.role = 'CARRIER'
                GROUP BY u.id, u.username, u.active
                ORDER BY u.username ASC
                """;
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<CarrierSummaryRecord> out = new ArrayList<>();
            while (rs.next()) {
                Object avgObj = rs.getObject("avg_rating");
                Double avg = (avgObj == null) ? null : ((Number) avgObj).doubleValue();
                out.add(new CarrierSummaryRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getBoolean("active"),
                        avg));
            }
            return out;
        }
    }

    /** Get creation dates of all customers (for historical charts). */
    public List<java.time.LocalDate> getCustomerCreationDates() throws Exception {
        String sql = "SELECT DATE(created_at) FROM user_info WHERE role = 'CUSTOMER'";
        try (Connection c = DbAdapter.getInstance().getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            List<java.time.LocalDate> dates = new ArrayList<>();
            while (rs.next()) {
                java.sql.Date d = rs.getDate(1);
                if (d != null)
                    dates.add(d.toLocalDate());
            }
            return dates;
        }
    }
}
