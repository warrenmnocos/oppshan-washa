package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One expense line in a month's budget: a {@code label}, an {@code amount} in a {@code currency}, and a
 * display {@code ordinal}, owned by one {@code BudgetMonth}. {@code auto} marks a derived line whose
 * amount is computed rather than user-entered: the non-removable tithe carries {@code auto = "tithe"}
 * and keeps its stored {@code amount} at zero, since the real figure is derived, not persisted.
 */
@Entity
@Table(name = "expense",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_expense_budget_month_uuid",
                        columnList = "budget_month_uuid"
                ),
        })
public class Expense extends UuidEntity<Expense> {

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
    @Column(name = "label",
            nullable = false)
    @NotEmpty
    private String label;

    @Basic(optional = false)
    @Column(name = "amount",
            nullable = false)
    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "currency",
            nullable = false,
            length = 3)
    @NotEmpty
    private String currency;

    @Column(name = "auto",
            length = 64)
    private String auto;

    /**
     * The {@code BudgetMonth} this expense belongs to.
     */
    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    /**
     * Sets the owning month and returns {@code this}.
     */
    public Expense setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    /**
     * Display order of this line within the month's expense list.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the display ordinal and returns {@code this}.
     */
    public Expense setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * Human-readable name for this expense line.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label and returns {@code this}.
     */
    public Expense setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * This line's amount, in {@code currency}. Defaults to zero, which is also what a derived
     * ({@code auto}) line keeps, since its real value is computed rather than stored.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount and returns {@code this}.
     */
    public Expense setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * The three-letter currency code {@code amount} is denominated in.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency and returns {@code this}.
     */
    public Expense setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * Tag marking a derived line. It's null on a normal user-entered line; a non-null value (currently
     * {@code "tithe"}) means the line's real amount is computed rather than taken from {@code amount}.
     */
    public String getAuto() {
        return auto;
    }

    /**
     * Sets the derived-line tag and returns {@code this}.
     */
    public Expense setAuto(String auto) {
        this.auto = auto;
        return this;
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

        if (!(other instanceof final Expense that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(label, that.label) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(auto, that.auto) &&
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
                label,
                amount,
                currency,
                auto,
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
                .add("label", label)
                .add("amount", amount)
                .add("currency", currency)
                .add("auto", auto)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
