package com.oppshan.washa.budget;

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

    // YearMonth, auto-converted to VARCHAR(7) "YYYY-MM" by YearMonthStringConverter.
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

    // Shared-dataset "who last touched", distinct from the @Version audit timestamp.
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

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public BudgetMonth setYearMonth(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
        return this;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public BudgetMonth setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
        return this;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public BudgetMonth setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
        return this;
    }

    public UserAccount getLastModifiedBy() {
        return lastModifiedBy;
    }

    public BudgetMonth setLastModifiedBy(UserAccount lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
        return this;
    }

    public List<Income> getIncomes() {
        incomes = Objects.requireNonNullElseGet(incomes, ArrayList::new);
        return incomes;
    }

    public List<Expense> getExpenses() {
        expenses = Objects.requireNonNullElseGet(expenses, ArrayList::new);
        return expenses;
    }

    public List<Goal> getGoals() {
        goals = Objects.requireNonNullElseGet(goals, ArrayList::new);
        return goals;
    }

    public List<Debt> getDebts() {
        debts = Objects.requireNonNullElseGet(debts, ArrayList::new);
        return debts;
    }

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

    @Override
    public String toString() {
        return "BudgetMonth{" +
               "uuid=" + getUuid() +
               ", yearMonth=" + yearMonth +
               ", baseCurrency=" + baseCurrency +
               ", fxRate=" + fxRate +
               ", createdAt=" + getCreatedAt() +
               ", lastModifiedAt=" + getLastModifiedAt() +
               '}';
    }
}
