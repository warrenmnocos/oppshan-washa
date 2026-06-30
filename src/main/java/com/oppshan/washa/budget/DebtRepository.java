package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Repository
public interface DebtRepository extends CrudRepository<Debt, UUID>, StatefulWriteRepository<Debt> {

    /**
     * Every prepayment-flagged debt in the saved months of one year, excluding the month being
     * planned, so the annual principal-prepayment card can total each debt's prepayment to date
     * across the year (the prototype's {@code debtYearPrepayJpy}). Matched across months by name —
     * the only stable cross-month key, since each month owns its own {@code Debt} rows. The service
     * reduces each prepayment to base currency at the current rates.
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
