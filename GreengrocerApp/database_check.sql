-- Database Verification Script for GreengrocerApp
-- Run this in MySQL to verify your database has sufficient data

-- Connect to database
USE greengrocer;

-- Show all tables
SHOW TABLES;

-- Count rows in each table (need ≥25 rows per table for submission)
SELECT 'user_info' AS table_name, COUNT(*) AS row_count FROM user_info
UNION ALL
SELECT 'product_info', COUNT(*) FROM product_info
UNION ALL
SELECT 'order_info', COUNT(*) FROM order_info
UNION ALL
SELECT 'order_lines', COUNT(*) FROM order_lines
UNION ALL
SELECT 'settings', COUNT(*) FROM settings
UNION ALL
SELECT 'messages', COUNT(*) FROM messages;

-- Verify test users exist
SELECT username, role, address 
FROM user_info 
WHERE username IN ('cust', 'carr', 'own')
ORDER BY username;

-- Check product variety (should have fruits and vegetables)
SELECT category, COUNT(*) as count
FROM product_info
GROUP BY category;

-- Sample products
SELECT id, name, category, price_per_kg, stock_kg, threshold_kg
FROM product_info
ORDER BY category, name
LIMIT 10;

-- Check orders exist
SELECT 
    o.id,
    o.status,
    o.order_time,
    o.requested_delivery_time,
    o.total_incl_vat,
    u.username AS customer
FROM order_info o
JOIN user_info u ON o.customer_id = u.id
ORDER BY o.order_time DESC
LIMIT 10;

-- Check order lines
SELECT 
    ol.order_id,
    p.name AS product_name,
    ol.quantity_kg,
    ol.price_per_kg,
    ol.subtotal
FROM order_lines ol
JOIN product_info p ON ol.product_id = p.id
ORDER BY ol.order_id DESC
LIMIT 10;

-- Check messages
SELECT 
    m.id,
    sender.username AS sender,
    recipient.username AS recipient,
    m.subject,
    m.sent_at
FROM messages m
JOIN user_info sender ON m.sender_id = sender.id
JOIN user_info recipient ON m.recipient_id = recipient.id
ORDER BY m.sent_at DESC
LIMIT 5;

-- Verify BLOB/CLOB storage
SELECT 
    id,
    name,
    LENGTH(image_data) AS image_size_bytes
FROM product_info
WHERE image_data IS NOT NULL
LIMIT 5;

SELECT 
    id,
    LENGTH(invoice_pdf) AS pdf_size_bytes
FROM order_info
WHERE invoice_pdf IS NOT NULL
LIMIT 5;

-- Summary for submission readiness
SELECT 
    'Requirement Check' AS check_type,
    CASE 
        WHEN (SELECT COUNT(*) FROM user_info) >= 25 THEN '✅ PASS'
        ELSE '❌ FAIL - Need more users'
    END AS user_info_status,
    CASE 
        WHEN (SELECT COUNT(*) FROM product_info) >= 24 THEN '✅ PASS'
        ELSE '❌ FAIL - Need ≥24 products'
    END AS product_info_status,
    CASE 
        WHEN (SELECT COUNT(*) FROM order_info) >= 25 THEN '✅ PASS'
        ELSE '⚠️  WARNING - Need ≥25 orders'
    END AS order_info_status;
