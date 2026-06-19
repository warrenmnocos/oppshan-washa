package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/** One computed deduction line of a salary breakdown. */
public record DeductionLine(String label, BigDecimal amount) {
}
