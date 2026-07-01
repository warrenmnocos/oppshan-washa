package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * The {@link Debt} rows, each a debt line owned by a {@link BudgetMonth} and cascade-managed with it.
 * The month is shared across the household, so these reads aren't user-scoped. Beyond the inherited
 * CRUD, the one finder reaches across a year's saved months for a prepayment-to-date total.
 */
@Repository
public interface DebtRepository extends CrudRepository<Debt, UUID>, StatefulWriteRepository<Debt> {

    /**
     * Every prepayment-flagged debt in the saved months of one year, excluding the month being
     * planned, so each debt's prepayment to date can be totalled across the year for the annual
     * principal-prepayment figure (the prototype's {@code debtYearPrepayJpy}). Matched across months
     * by name, the only stable cross-month key, since each month owns its own {@code Debt} rows.
     */
    @Query("""
            SELECT d
            FROM Debt d
            WHERE d.prepay = TRUE
              AND d.budgetMonth.yearMonth BETWEEN :yearStart AND :yearEnd
              AND d.budgetMonth.yearMonth <> :current""")
    List<Debt> findPrepaidInYearExcept(@NotNull YearMonth yearStart,
                                       @NotNull YearMonth yearEnd,
                                       @NotNull YearMonth current);
}
