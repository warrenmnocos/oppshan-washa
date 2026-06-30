package com.oppshan.washa.budget.engine;

import java.math.BigDecimal;
import java.util.List;

/** The computed result of one salary: gross, the basic figure, ordered deduction lines, and net. */
public record Breakdown(BigDecimal gross, BigDecimal basic, List<DeductionLine> lines, BigDecimal net) {
}
