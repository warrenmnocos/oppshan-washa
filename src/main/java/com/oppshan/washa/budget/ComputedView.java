package com.oppshan.washa.budget;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Live computed figures for a month (all in base currency), returned by {@code /api/budget/compute}. */
public record ComputedView(
        BigDecimal moneyIn,
        BigDecimal moneyOut,
        BigDecimal free,
        BigDecimal tithe,
        Map<String, BigDecimal> salaryNet,
        List<DebtProjection> debts) {

    public record DebtProjection(String name, int months, BigDecimal totalInterest) {
    }
}
