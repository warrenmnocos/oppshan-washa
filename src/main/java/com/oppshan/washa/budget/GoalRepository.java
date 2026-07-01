package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link Goal} rows, each a goal line owned by a {@link BudgetMonth} and cascade-managed with it.
 * The month is shared across the household, so these reads aren't user-scoped. A goal's identity
 * across months is its ({@code label}, {@code currency}) pair, not its per-month UUID (backed by the
 * {@code idx_goal_label_currency} index), so both finders group and filter on that pair. Cumulative
 * progress is summed from the month rows on demand, never stored (HANDOVER §13).
 */
@Repository
public interface GoalRepository extends CrudRepository<Goal, UUID>, StatefulWriteRepository<Goal> {

    /**
     * Cumulative contributions toward a goal across all months strictly before the given one, in
     * its own currency (HANDOVER §13: progress is derived by summing month rows, never stored).
     */
    @Query("""
            SELECT COALESCE(SUM(g.amount), 0)
            FROM Goal g
            WHERE g.label = :label
              AND g.currency = :currency
              AND g.budgetMonth.yearMonth < :yearMonth""")
    BigDecimal sumContributionsBefore(@NotEmpty String label,
                                      @NotEmpty String currency,
                                      @NotNull YearMonth yearMonth);

    /**
     * The earliest persisted month a goal with this label/currency appears in: the goal's start, off
     * which a TIME target's elapsed-time progress is measured (the prototype's {@code goalStartDate},
     * derived from {@code g.created}). Empty when the goal has no persisted month yet.
     */
    @Query("""
            SELECT MIN(g.budgetMonth.yearMonth)
            FROM Goal g
            WHERE g.label = :label
              AND g.currency = :currency""")
    Optional<YearMonth> earliestMonthOf(@NotEmpty String label,
                                        @NotEmpty String currency);
}
