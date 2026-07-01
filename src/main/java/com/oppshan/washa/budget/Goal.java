package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.ColumnDefault;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One savings or spending goal in a month's budget, owned by one {@code BudgetMonth}. {@code amount} is
 * this month's contribution and {@code withdrawal} what's taken back out; {@code savings} splits goals
 * that build the household savings balance from ordinary spending goals. {@code targetType} defines what
 * "done" means: {@code OPEN} has no target, {@code AMOUNT} a fixed {@code targetAmount}, {@code RELATIVE}
 * a {@code targetMult} multiple of net income, and {@code TIME} a deadline ({@code targetDueDate}, or
 * {@code targetPeriodCount} of {@code targetPeriodUnit} counted from the goal's start). A goal's
 * accumulated balance isn't stored on the row: the same goal is matched across months by {@code label}
 * plus {@code currency} (hence the {@code idx_goal_label_currency} index) and summed when read.
 */
@Entity
@Table(name = "goal",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_goal_budget_month_uuid",
                        columnList = "budget_month_uuid"
                ),
                @Index(
                        name = "idx_goal_label_currency",
                        columnList = "label,currency"
                ),
        })
public class Goal extends UuidEntity<Goal> {

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

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    @Column(name = "target_type",
            nullable = false,
            length = 16)
    @NotNull
    private GoalTargetType targetType = GoalTargetType.OPEN;

    @Column(name = "target_amount")
    private BigDecimal targetAmount;

    @Column(name = "target_base",
            length = 32)
    private String targetBase;

    @Column(name = "target_mult")
    private BigDecimal targetMult;

    @Column(name = "target_due_date")
    private LocalDate targetDueDate;

    @Column(name = "target_period_count")
    private Integer targetPeriodCount;

    @Column(name = "target_period_unit",
            length = 16)
    private String targetPeriodUnit;

    @Basic(optional = false)
    @Column(name = "savings",
            nullable = false)
    private boolean savings = false;

    @Basic(optional = false)
    @Column(name = "withdrawal",
            nullable = false)
    @NotNull
    private BigDecimal withdrawal = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "closed",
            nullable = false)
    @ColumnDefault("false")
    private boolean closed = false;

    @Column(name = "closed_key",
            length = 7)
    private String closedKey;


    /**
     * The {@code BudgetMonth} this goal belongs to.
     */
    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    /**
     * Sets the owning month and returns {@code this}.
     */
    public Goal setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    /**
     * Display order within the month's goal list.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public Goal setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * Human-readable name for this goal; with {@code currency}, the key that matches the same goal
     * across months.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label and returns {@code this}.
     */
    public Goal setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * This month's contribution to the goal. Defaults to zero.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the contribution amount and returns {@code this}.
     */
    public Goal setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * Three-letter currency code the goal's figures are in; with {@code label}, the cross-month match
     * key.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency and returns {@code this}.
     */
    public Goal setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * What "done" means for this goal: {@code OPEN} (no target), {@code AMOUNT} ({@code targetAmount}),
     * {@code RELATIVE} (a {@code targetMult} multiple of net income), or {@code TIME} (a deadline).
     * Defaults to {@code OPEN}.
     */
    public GoalTargetType getTargetType() {
        return targetType;
    }

    /**
     * Sets the target type and returns {@code this}.
     */
    public Goal setTargetType(GoalTargetType targetType) {
        this.targetType = targetType;
        return this;
    }

    /**
     * The fixed target for an {@code AMOUNT} goal.
     */
    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    /**
     * Sets the target amount and returns {@code this}.
     */
    public Goal setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
        return this;
    }

    /**
     * A label describing what a {@code RELATIVE} target's {@code targetMult} multiplies (e.g. net
     * income). Descriptive only: {@code targetMult} holds the actual factor.
     */
    public String getTargetBase() {
        return targetBase;
    }

    /**
     * Sets the target-base label and returns {@code this}.
     */
    public Goal setTargetBase(String targetBase) {
        this.targetBase = targetBase;
        return this;
    }

    /**
     * The multiple of net income a {@code RELATIVE} goal targets.
     */
    public BigDecimal getTargetMult() {
        return targetMult;
    }

    /**
     * Sets the target multiple and returns {@code this}.
     */
    public Goal setTargetMult(BigDecimal targetMult) {
        this.targetMult = targetMult;
        return this;
    }

    /**
     * The deadline for a {@code TIME} goal, as a date.
     */
    public LocalDate getTargetDueDate() {
        return targetDueDate;
    }

    /**
     * Sets the target due date and returns {@code this}.
     */
    public Goal setTargetDueDate(LocalDate targetDueDate) {
        this.targetDueDate = targetDueDate;
        return this;
    }

    /**
     * For a {@code TIME} goal, the number of {@code targetPeriodUnit} periods from the goal's start to
     * its deadline.
     */
    public Integer getTargetPeriodCount() {
        return targetPeriodCount;
    }

    /**
     * Sets the target period count and returns {@code this}.
     */
    public Goal setTargetPeriodCount(Integer targetPeriodCount) {
        this.targetPeriodCount = targetPeriodCount;
        return this;
    }

    /**
     * For a {@code TIME} goal, the period unit paired with {@code targetPeriodCount}.
     */
    public String getTargetPeriodUnit() {
        return targetPeriodUnit;
    }

    /**
     * Sets the target period unit and returns {@code this}.
     */
    public Goal setTargetPeriodUnit(String targetPeriodUnit) {
        this.targetPeriodUnit = targetPeriodUnit;
        return this;
    }

    /**
     * Whether this goal feeds the household savings balance ({@code true}) or is an ordinary spending
     * goal ({@code false}). Defaults to {@code false}.
     */
    public boolean isSavings() {
        return savings;
    }

    /**
     * Sets the savings flag and returns {@code this}.
     */
    public Goal setSavings(boolean savings) {
        this.savings = savings;
        return this;
    }

    /**
     * Amount taken back out of the goal this month. Defaults to zero.
     */
    public BigDecimal getWithdrawal() {
        return withdrawal;
    }

    /**
     * Sets the withdrawal and returns {@code this}.
     */
    public Goal setWithdrawal(BigDecimal withdrawal) {
        this.withdrawal = withdrawal;
        return this;
    }

    /**
     * Whether the goal has been closed. Defaults to {@code false}. The {@code @ColumnDefault("false")}
     * mirrors the Flyway column DEFAULT so a drop-and-create test schema that omits the column gets the
     * same default prod does, rather than a NOT-NULL violation.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Sets the closed flag and returns {@code this}.
     */
    public Goal setClosed(boolean closed) {
        this.closed = closed;
        return this;
    }

    /**
     * The {@code "YYYY-MM"} month the goal was closed in; null while the goal is open.
     */
    public String getClosedKey() {
        return closedKey;
    }

    /**
     * Sets the closed-month key and returns {@code this}.
     */
    public Goal setClosedKey(String closedKey) {
        this.closedKey = closedKey;
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

        if (!(other instanceof final Goal that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(label, that.label) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(targetType, that.targetType) &&
               Objects.equals(targetAmount, that.targetAmount) &&
               Objects.equals(targetBase, that.targetBase) &&
               Objects.equals(targetMult, that.targetMult) &&
               Objects.equals(targetDueDate, that.targetDueDate) &&
               Objects.equals(targetPeriodCount, that.targetPeriodCount) &&
               Objects.equals(targetPeriodUnit, that.targetPeriodUnit) &&
               savings == that.savings &&
               Objects.equals(withdrawal, that.withdrawal) &&
               closed == that.closed &&
               Objects.equals(closedKey, that.closedKey) &&
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
                targetType,
                targetAmount,
                targetBase,
                targetMult,
                targetDueDate,
                targetPeriodCount,
                targetPeriodUnit,
                savings,
                withdrawal,
                closed,
                closedKey,
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
                .add("targetType", targetType)
                .add("targetAmount", targetAmount)
                .add("targetBase", targetBase)
                .add("targetMult", targetMult)
                .add("targetDueDate", targetDueDate)
                .add("targetPeriodCount", targetPeriodCount)
                .add("targetPeriodUnit", targetPeriodUnit)
                .add("savings", savings)
                .add("withdrawal", withdrawal)
                .add("closed", closed)
                .add("closedKey", closedKey)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
