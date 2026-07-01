package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link BudgetMonth} aggregate root: the shared household month that owns that month's incomes,
 * expenses, goals, and debts. There's one row per calendar month for the whole household (unique on
 * {@code year_month}), not one per user, so these reads carry no owning-user filter. The
 * tenant-scoping convention that applies to user-owned entities doesn't apply to this shared dataset;
 * the only user reference on the month is the {@code last_modified_by} audit pointer. Writes go
 * through the {@link StatefulWriteRepository} mixin.
 */
@Repository
public interface BudgetMonthRepository
        extends CrudRepository<BudgetMonth, UUID>, StatefulWriteRepository<BudgetMonth> {

    /**
     * The saved month for a {@link YearMonth}, or empty when none exists yet. A Jakarta Data
     * {@code @Find} derived query matching on the {@code yearMonth} attribute; since {@code year_month}
     * is uniquely constrained it resolves to at most one row. The child collections (incomes,
     * expenses, goals, debts) stay lazy, so walking them means re-attaching the returned month to a
     * session first.
     */
    @Find
    Optional<BudgetMonth> findByYearMonth(@NotNull YearMonth yearMonth);
}
