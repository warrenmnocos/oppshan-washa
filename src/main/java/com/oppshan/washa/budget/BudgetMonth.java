package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import com.oppshan.washa.user.UserAccount;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One month's snapshot of the shared household budget, and the aggregate root the rest of the budget
 * model hangs off. It owns that month's {@code incomes}, {@code expenses}, {@code goals}, and
 * {@code debts} as cascade-all / orphan-removal children, so persisting or deleting the month carries
 * the whole graph with it. There's one row per calendar month (unique {@code year_month}), and a month
 * is replaced by delete-and-reinsert rather than mutated in place. {@code baseCurrency} is the currency
 * every figure reduces to. Cumulative figures (goal balances, year-to-date prepayment) aren't stored on
 * the month; they're summed across month rows when read.
 */
@Entity
@Table(name = "budget_month",
        schema = "washa",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uc_budget_month_year_month",
                        columnNames = {
                                "year_month"
                        }
                ),
        })
public class BudgetMonth extends UuidEntity<BudgetMonth> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Basic(optional = false)
    @Column(name = "year_month",
            nullable = false,
            updatable = false,
            length = 7)
    @NotNull
    private YearMonth yearMonth;

    @Basic(optional = false)
    @Column(name = "base_currency",
            nullable = false,
            length = 3)
    @NotEmpty
    private String baseCurrency;

    @Column(name = "fx_rate")
    private BigDecimal fxRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by")
    private UserAccount lastModifiedBy;

    @OneToMany(
            mappedBy = "budgetMonth",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Income> incomes;

    @OneToMany(
            mappedBy = "budgetMonth",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Expense> expenses;

    @OneToMany(
            mappedBy = "budgetMonth",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Goal> goals;

    @OneToMany(
            mappedBy = "budgetMonth",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<Debt> debts;

    /**
     * The calendar month this snapshot covers, and the row's business key: unique per month
     * ({@code uc_budget_month_year_month}) and never updated once set. It's stored as a
     * {@code VARCHAR(7)} {@code "YYYY-MM"} string by {@code YearMonthStringConverter} (auto-applied),
     * since Hibernate has no native {@code YearMonth} type.
     */
    public YearMonth getYearMonth() {
        return yearMonth;
    }

    /**
     * Sets the calendar month and returns {@code this}.
     */
    public BudgetMonth setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
        return this;
    }

    /**
     * The three-letter currency code every figure in this month reduces to.
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Sets the base currency and returns {@code this}.
     */
    public BudgetMonth setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
        return this;
    }

    /**
     * A single base-to-quote FX scalar stored on the month. It's effectively vestigial: conversion runs
     * off the per-pair {@code FxRate} rows instead, so this field is currently unused.
     */
    public BigDecimal getFxRate() {
        return fxRate;
    }

    /**
     * Sets the base-to-quote FX scalar and returns {@code this}.
     */
    public BudgetMonth setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
        return this;
    }

    /**
     * The {@code UserAccount} that last edited the shared household dataset. This is a business
     * "last editor" pointer, distinct from the {@code @Version} {@code last_modified_at} timestamp on
     * {@code AuditableEntity} that Hibernate stamps on every flush.
     */
    public UserAccount getLastModifiedBy() {
        return lastModifiedBy;
    }

    /**
     * Sets the last-editor reference and returns {@code this}.
     */
    public BudgetMonth setLastModifiedBy(UserAccount lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
        return this;
    }

    /**
     * This month's income (salary) lines: its {@code Income} children, cascaded all with orphan removal.
     * Lazily initialised to an empty list on first access, so it's never null.
     */
    public List<Income> getIncomes() {
        incomes = Objects.requireNonNullElseGet(incomes, ArrayList::new);
        return incomes;
    }

    /**
     * This month's expense lines: its {@code Expense} children, cascaded all with orphan removal. Lazily
     * initialised to an empty list on first access, so it's never null.
     */
    public List<Expense> getExpenses() {
        expenses = Objects.requireNonNullElseGet(expenses, ArrayList::new);
        return expenses;
    }

    /**
     * This month's savings and spending goals: its {@code Goal} children, cascaded all with orphan
     * removal. Lazily initialised to an empty list on first access, so it's never null.
     */
    public List<Goal> getGoals() {
        goals = Objects.requireNonNullElseGet(goals, ArrayList::new);
        return goals;
    }

    /**
     * This month's debts: its {@code Debt} children, cascaded all with orphan removal. Lazily
     * initialised to an empty list on first access, so it's never null.
     */
    public List<Debt> getDebts() {
        debts = Objects.requireNonNullElseGet(debts, ArrayList::new);
        return debts;
    }

    /**
     * Value equality over all identifying fields plus the audit triple ({@code uuid}, {@code createdAt},
     * {@code lastModifiedAt}).
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final BudgetMonth that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               Objects.equals(yearMonth, that.yearMonth) &&
               Objects.equals(baseCurrency, that.baseCurrency) &&
               Objects.equals(fxRate, that.fxRate) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /**
     * Hashes the same fields {@code equals} compares.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                yearMonth,
                baseCurrency,
                fxRate,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * Renders the identifying fields and audit triple for logging.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("yearMonth", yearMonth)
                .add("baseCurrency", baseCurrency)
                .add("fxRate", fxRate)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
