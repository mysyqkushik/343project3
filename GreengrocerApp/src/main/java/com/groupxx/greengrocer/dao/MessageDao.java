package com.groupxx.greengrocer.dao;

import com.groupxx.greengrocer.db.DbAdapter;
import com.groupxx.greengrocer.model.MessageRecord;
import com.groupxx.greengrocer.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Messaging between Customer and Owner.
 *
 * Conversation is keyed by customer_id. Owner is implicit (the OWNER role).
 */
public final class MessageDao {

    public void ensureMessageTable() throws Exception {
        try (Connection c = DbAdapter.getInstance().getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("""
                CREATE TABLE IF NOT EXISTS message_info (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_id BIGINT NOT NULL,
                  sender_role ENUM('CUSTOMER','OWNER') NOT NULL,
                  message_text VARCHAR(1000) NOT NULL,
                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_msg_customer (customer_id),
                  INDEX idx_msg_created (created_at),
                  FOREIGN KEY (customer_id) REFERENCES user_info(id)
                )
                """)) {
                ps.execute();
            }
        }
    }

    public void sendFromCustomer(long customerId, String text) throws Exception {
        insert(customerId, Role.CUSTOMER, text);
    }

    public void sendFromOwner(long customerId, String text) throws Exception {
        insert(customerId, Role.OWNER, text);
    }

    private void insert(long customerId, Role sender, String text) throws Exception {
        if (text == null) text = "";
        text = text.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("Message is empty");
        if (text.length() > 1000) text = text.substring(0, 1000);

        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("""
                 INSERT INTO message_info (customer_id, sender_role, message_text)
                 VALUES (?, ?, ?)
                 """)) {
            ps.setLong(1, customerId);
            ps.setString(2, sender.name());
            ps.setString(3, text);
            ps.executeUpdate();
        }
    }

    public List<MessageRecord> listConversationForCustomerId(long customerId) throws Exception {
        String sql = """
            SELECT m.id, m.customer_id, u.username AS customer_username,
                   m.sender_role, m.message_text, m.created_at
            FROM message_info m
            JOIN user_info u ON u.id = m.customer_id
            WHERE m.customer_id = ?
            ORDER BY m.created_at ASC, m.id ASC
            """;

        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MessageRecord> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        }
    }

    /**
     * For Owner: list distinct customers who have messages, with their username.
     */
    public List<Long> listCustomerIdsWithMessages() throws Exception {
        String sql = "SELECT DISTINCT customer_id FROM message_info ORDER BY customer_id ASC";
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Long> ids = new ArrayList<>();
            while (rs.next()) ids.add(rs.getLong(1));
            return ids;
        }
    }

    public List<MessageRecord> listConversationForCustomerUsername(String customerUsername) throws Exception {
        if (customerUsername == null || customerUsername.isBlank()) return List.of();
        String sql = "SELECT id FROM user_info WHERE username=? AND role='CUSTOMER' LIMIT 1";
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return List.of();
                long id = rs.getLong(1);
                return listConversationForCustomerId(id);
            }
        }
    }

    public void sendFromCustomerUsername(String customerUsername, String text) throws Exception {
        long id = customerIdByUsername(customerUsername);
        sendFromCustomer(id, text);
    }

    public void sendFromOwnerToCustomerUsername(String customerUsername, String text) throws Exception {
        long id = customerIdByUsername(customerUsername);
        sendFromOwner(id, text);
    }

    private long customerIdByUsername(String customerUsername) throws Exception {
        if (customerUsername == null || customerUsername.isBlank()) throw new IllegalArgumentException("Invalid username");
        String sql = "SELECT id FROM user_info WHERE username=? AND role='CUSTOMER' LIMIT 1";
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, customerUsername);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Unknown customer: " + customerUsername);
                return rs.getLong(1);
            }
        }
    }

    private static MessageRecord map(ResultSet rs) throws Exception {
        Timestamp ts = rs.getTimestamp("created_at");
        LocalDateTime dt = ts == null ? null : ts.toLocalDateTime();
        return new MessageRecord(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                rs.getString("customer_username"),
                Role.valueOf(rs.getString("sender_role")),
                rs.getString("message_text"),
                dt
        );
    }


    /** Owner view: list all messages across all customers. */
    public List<MessageRecord> listAllMessagesForOwner() throws Exception {
        String sql = """
            SELECT m.id, m.customer_id, u.username AS customer_username,
                   m.sender_role, m.message_text, m.created_at
            FROM message_info m
            JOIN user_info u ON u.id = m.customer_id
            ORDER BY m.created_at ASC, m.id ASC
            """;
        try (Connection c = DbAdapter.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            List<MessageRecord> out = new ArrayList<>();
            while (rs.next()) {
                out.add(new MessageRecord(
                        rs.getLong("id"),
                        rs.getLong("customer_id"),
                        rs.getString("customer_username"),
                        Role.valueOf(rs.getString("sender_role")),
                        rs.getString("message_text"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                ));
            }
            return out;
        }
    }

}
