package com.groupxx.greengrocer.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesRecord(LocalDate day, int orders, BigDecimal revenue) {}
