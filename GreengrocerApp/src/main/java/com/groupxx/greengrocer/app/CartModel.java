package com.groupxx.greengrocer.app;

import com.groupxx.greengrocer.model.CartLine;
import com.groupxx.greengrocer.model.ProductCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CartModel {
    private static final CartModel INSTANCE = new CartModel();
    private final Map<Long, CartLine> lines = new LinkedHashMap<>();

    private CartModel() {}

    public static CartModel get() {
        return INSTANCE;
    }

    public synchronized List<CartLine> snapshot() {
        return new ArrayList<>(lines.values());
    }

    public synchronized BigDecimal reservedKg(long productId) {
        CartLine l = lines.get(productId);
        return l == null ? BigDecimal.ZERO : com.groupxx.greengrocer.util.BigDecimalUtil.nz(l.kg());
    }

    public synchronized void addOrIncrease(long productId, String name, ProductCategory category,
                                           BigDecimal unitPricePerKg, BigDecimal addKg) {
        BigDecimal add = com.groupxx.greengrocer.util.NumericValidators.requirePositiveScale(
                com.groupxx.greengrocer.util.BigDecimalUtil.nz(addKg), 2, "Amount (kg)");

        CartLine existing = lines.get(productId);
        if (existing == null) {
            lines.put(productId, new CartLine(productId, name, category, unitPricePerKg, scale2(add)));
        } else {
            BigDecimal newKg = com.groupxx.greengrocer.util.BigDecimalUtil.nz(existing.kg()).add(add);
            lines.put(productId, new CartLine(productId, name, category, unitPricePerKg, scale2(newKg)));
        }
    }

    public synchronized void remove(long productId) {
        lines.remove(productId);
    }

    public synchronized void clear() {
        lines.clear();
    }

    public synchronized BigDecimal subtotal() {
        BigDecimal s = BigDecimal.ZERO;
        for (CartLine l : lines.values()) s = s.add(com.groupxx.greengrocer.util.BigDecimalUtil.nz(l.lineTotal()));
        return scale2(s);
    }

    private static BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
