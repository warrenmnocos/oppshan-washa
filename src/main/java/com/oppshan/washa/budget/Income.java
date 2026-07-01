package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One person's salary within a month, owned by one {@code BudgetMonth} and the root of a small payroll
 * graph. Its ordered children model the walk from gross to net: {@code components} are the pieces of
 * gross (basic pay, allowances, bonuses), {@code variables} are named intermediate values, and
 * {@code deductions} are the pre/post-tax lines subtracted from gross, each set ordered by
 * {@code ordinal}. Every figure is denominated in the salary's own {@code currency}.
 */
@Entity
@Table(name = "income",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_income_budget_month_uuid",
                        columnList = "budget_month_uuid"
                ),
        })
public class Income extends UuidEntity<Income> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "budget_month_uuid",
            nullable = false
    )
    @NotNull
    private BudgetMonth budgetMonth;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "name",
            nullable = false)
    @NotEmpty
    private String name;

    @Basic(optional = false)
    @Column(name = "currency",
            nullable = false,
            length = 3)
    @NotEmpty
    private String currency;

    @Basic(optional = false)
    @Column(name = "engine",
            nullable = false,
            length = 64)
    @NotEmpty
    private String engine = "generic";

    @OneToMany(
            mappedBy = "income",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<IncomeComponent> components;

    @OneToMany(
            mappedBy = "income",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<IncomeDeduction> deductions;

    @OneToMany(
            mappedBy = "income",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<IncomeVariable> variables;

    /**
     * The {@code BudgetMonth} this salary belongs to.
     */
    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    /**
     * Sets the owning month and returns {@code this}.
     */
    public Income setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    /**
     * Order of this salary within the month's income list.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public Income setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The person or label this salary belongs to.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name and returns {@code this}.
     */
    public Income setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * The three-letter currency code every figure in this salary is denominated in.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency and returns {@code this}.
     */
    public Income setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * A free-form preset label for this salary (defaults to {@code "generic"}). It's descriptive
     * metadata only: the payroll walk doesn't branch on its value.
     */
    public String getEngine() {
        return engine;
    }

    /**
     * Sets the preset label and returns {@code this}.
     */
    public Income setEngine(String engine) {
        this.engine = engine;
        return this;
    }

    /**
     * The gross-pay pieces of this salary: its {@code IncomeComponent} children, ordered by
     * {@code ordinal}, cascaded all with orphan removal. Lazily initialised on first access, so it's
     * never null.
     */
    public List<IncomeComponent> getComponents() {
        components = Objects.requireNonNullElseGet(components, ArrayList::new);
        return components;
    }

    /**
     * The lines subtracted from gross: its {@code IncomeDeduction} children, ordered by {@code ordinal},
     * cascaded all with orphan removal. Lazily initialised on first access, so it's never null.
     */
    public List<IncomeDeduction> getDeductions() {
        deductions = Objects.requireNonNullElseGet(deductions, ArrayList::new);
        return deductions;
    }

    /**
     * The named intermediate values in this salary's payroll: its {@code IncomeVariable} children,
     * ordered by {@code ordinal}, cascaded all with orphan removal. Lazily initialised on first access,
     * so it's never null.
     */
    public List<IncomeVariable> getVariables() {
        variables = Objects.requireNonNullElseGet(variables, ArrayList::new);
        return variables;
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

        if (!(other instanceof final Income that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(name, that.name) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(engine, that.engine) &&
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
                ordinal,
                name,
                currency,
                engine,
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
                .add("ordinal", ordinal)
                .add("name", name)
                .add("currency", currency)
                .add("engine", engine)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
