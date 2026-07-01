package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;
import java.util.List;

/**
 * What {@code SalaryEngine.compute} produces for one salary, all in that salary's own currency (no FX
 * is applied here; the caller reduces to base). {@code gross} is the summed components, {@code basic}
 * is the basic-pay figure that percentage deductions and brackets can key on (it falls back to gross
 * when no component is flagged basic), {@code lines} are the ordered, integer-rounded deduction
 * amounts, and {@code net} is gross minus the sum of those lines.
 */
public record Breakdown(BigDecimal gross, BigDecimal basic, List<DeductionLine> lines, BigDecimal net) {
}
