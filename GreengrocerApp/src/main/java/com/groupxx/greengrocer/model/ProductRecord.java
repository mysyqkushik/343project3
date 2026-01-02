package com.groupxx.greengrocer.model;

import java.math.BigDecimal;

/**
 * Represents a product available for sale.
 *
 * @param id             Unique database identifier.
 * @param name           Display name of the product.
 * @param category       Category of the product (FRUIT or VEGETABLE).
 * @param basePricePerKg Base price per kilogram before dynamic pricing
 *                       adjustments.
 * @param stockKg        Current available stock in kilograms.
 * @param thresholdKg    Stock threshold below which the price doubles (scarcity
 *                       pricing).
 * @param imageBytes     Binary image data for the product thumbnail.
 * @param active         Whether the product is currently listed for sale.
 */
public record ProductRecord(
        long id,
        String name,
        ProductCategory category,
        BigDecimal basePricePerKg,
        BigDecimal stockKg,
        BigDecimal thresholdKg,
        byte[] imageBytes,
        boolean active) {
    /**
     * Calculates the effective price per kilogram based on current stock levels.
     * <p>
     * Implements a dynamic pricing rule: if the stock level is less than or equal
     * to
     * the defined threshold, the price is doubled to reflect scarcity.
     * </p>
     *
     * @return The calculated effective price per kg.
     */
    public BigDecimal effectivePricePerKg() {
        // Rule: if stock <= threshold => price doubled
        BigDecimal base = com.groupxx.greengrocer.util.BigDecimalUtil.nz(basePricePerKg);
        BigDecimal stock = com.groupxx.greengrocer.util.BigDecimalUtil.nz(stockKg);
        BigDecimal threshold = com.groupxx.greengrocer.util.BigDecimalUtil.nz(thresholdKg);
        if (stock.compareTo(threshold) <= 0) {
            return base.multiply(BigDecimal.valueOf(2));
        }
        return base;
    }
}
