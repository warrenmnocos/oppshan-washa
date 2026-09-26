package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * The {@link Debt} rows, each a debt line owned by a {@link BudgetMonth} and cascade-managed with it.
 * The month is shared across the household, so these reads aren't user-scoped. Beyond the inherited
 * CRUD, the finders reach across saved months: one for a year's prepayment-to-date total, one for an
 * interest-free debt's repayments so far.
 */
@Repository
public interface DebtRepository extends CrudRepository<Debt, UUID>, StatefulWriteRepository<Debt> {

    /**
     * Every prepayment-flagged debt in the saved months of one year, excluding the month being
     * planned, so each debt's prepayment to date can be totalled across the year for the annual
     * principal-prepayment figure (the prototype's {@code debtYearPrepayJpy}). Matched across months
     * by name, the only stable cross-month key, since each month owns its own {@code Debt} rows. An
     * interest-free debt never counts: it has no prepayment, only repayments.
     */
    @Query("""
            SELECT d
            FROM Debt d
            WHERE d.prepay = TRUE
              AND d.interestFree = FALSE
              AND d.budgetMonth.yearMonth BETWEEN :yearStart AND :yearEnd
              AND d.budgetMonth.yearMonth <> :current""")
    List<Debt> findPrepaidInYearExcept(@NotNull YearMonth yearStart,
                                       @NotNull YearMonth yearEnd,
                                       @NotNull YearMonth current);

    /**
     * An interest-free debt's repayments across all saved months strictly before the given one, in the debt's own
     * currency, matched by name and currency like a goal's contributions. The raw sum can overshoot what was borrowed
     * when a repayment was carried past payoff, so callers cap it at the principal. Progress is derived by summing
     * month rows, never stored.
     */
    @Query("""
            SELECT COALESCE(SUM(d.monthly), 0)
            FROM Debt d
            WHERE d.interestFree = TRUE
              AND d.name = :name
              AND d.currency = :currency
              AND d.budgetMonth.yearMonth < :yearMonth""")
    BigDecimal sumInterestFreeRepaymentsBefore(@NotEmpty String name,
                                               @NotEmpty String currency,
                                               @NotNull YearMonth yearMonth);
}
