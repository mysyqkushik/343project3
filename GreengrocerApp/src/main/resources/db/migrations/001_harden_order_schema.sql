-- Normalize nulls before enforcing NOT NULL constraints
UPDATE order_info
SET order_time = COALESCE(order_time, CURRENT_TIMESTAMP)
WHERE order_time IS NULL;

UPDATE order_info
SET requested_delivery_time = COALESCE(requested_delivery_time, order_time, CURRENT_TIMESTAMP)
WHERE requested_delivery_time IS NULL;

UPDATE order_info
SET subtotal = COALESCE(subtotal, 0.00),
    vat = COALESCE(vat, 0.00),
    discount = COALESCE(discount, 0.00),
    total_incl_vat = COALESCE(total_incl_vat, 0.00)
WHERE subtotal IS NULL
   OR vat IS NULL
   OR discount IS NULL
   OR total_incl_vat IS NULL;

UPDATE order_item
SET amount_kg = COALESCE(amount_kg, 0.00),
    unit_price = COALESCE(unit_price, 0.00),
    line_total = COALESCE(line_total, 0.00)
WHERE amount_kg IS NULL
   OR unit_price IS NULL
   OR line_total IS NULL;

-- Harden columns
ALTER TABLE order_info MODIFY COLUMN customer_id BIGINT NOT NULL;
ALTER TABLE order_info MODIFY COLUMN order_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE order_info MODIFY COLUMN requested_delivery_time DATETIME NOT NULL;
ALTER TABLE order_info MODIFY COLUMN subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_info MODIFY COLUMN vat DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_info MODIFY COLUMN discount DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_info MODIFY COLUMN total_incl_vat DECIMAL(10,2) NOT NULL DEFAULT 0.00;

ALTER TABLE order_item MODIFY COLUMN order_id BIGINT NOT NULL;
ALTER TABLE order_item MODIFY COLUMN product_id BIGINT NOT NULL;
ALTER TABLE order_item MODIFY COLUMN amount_kg DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_item MODIFY COLUMN unit_price DECIMAL(10,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_item MODIFY COLUMN line_total DECIMAL(10,2) NOT NULL DEFAULT 0.00;

-- Relax legacy username columns if they exist
SET @has_customer_username := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_info'
      AND COLUMN_NAME = 'customer_username'
);
SET @sql_customer_username := IF(
    @has_customer_username > 0,
    'ALTER TABLE order_info MODIFY COLUMN customer_username VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql_customer_username;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_carrier_username := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'order_info'
      AND COLUMN_NAME = 'carrier_username'
);
SET @sql_carrier_username := IF(
    @has_carrier_username > 0,
    'ALTER TABLE order_info MODIFY COLUMN carrier_username VARCHAR(32) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql_carrier_username;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Indexes
CREATE INDEX idx_order_info_customer ON order_info (customer_id);
CREATE INDEX idx_order_info_carrier ON order_info (carrier_id);
CREATE INDEX idx_order_info_order_time ON order_info (order_time);
CREATE INDEX idx_order_item_order ON order_item (order_id);
CREATE INDEX idx_order_item_product ON order_item (product_id);

-- Foreign keys
ALTER TABLE order_info
    ADD CONSTRAINT fk_order_info_customer FOREIGN KEY (customer_id) REFERENCES user_info(id),
    ADD CONSTRAINT fk_order_info_carrier FOREIGN KEY (carrier_id) REFERENCES user_info(id);

ALTER TABLE order_item
    ADD CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES order_info(id),
    ADD CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product_info(id);
