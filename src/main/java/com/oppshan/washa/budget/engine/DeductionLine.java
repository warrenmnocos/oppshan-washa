package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;

/**
 * One line of a salary breakdown: a display {@code label} and the deduction {@code amount}, rounded to
 * a whole unit in the salary's own currency (the engine rounds each line HALF_UP).
 */
public record DeductionLine(String label, BigDecimal amount) {
}
