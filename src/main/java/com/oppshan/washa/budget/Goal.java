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


    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    public Goal setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Goal setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Goal setLabel(String label) {
        this.label = label;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Goal setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public Goal setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public GoalTargetType getTargetType() {
        return targetType;
    }

    public Goal setTargetType(GoalTargetType targetType) {
        this.targetType = targetType;
        return this;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public Goal setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
        return this;
    }

    public String getTargetBase() {
        return targetBase;
    }

    public Goal setTargetBase(String targetBase) {
        this.targetBase = targetBase;
        return this;
    }

    public BigDecimal getTargetMult() {
        return targetMult;
    }

    public Goal setTargetMult(BigDecimal targetMult) {
        this.targetMult = targetMult;
        return this;
    }

    public LocalDate getTargetDueDate() {
        return targetDueDate;
    }

    public Goal setTargetDueDate(LocalDate targetDueDate) {
        this.targetDueDate = targetDueDate;
        return this;
    }

    public Integer getTargetPeriodCount() {
        return targetPeriodCount;
    }

    public Goal setTargetPeriodCount(Integer targetPeriodCount) {
        this.targetPeriodCount = targetPeriodCount;
        return this;
    }

    public String getTargetPeriodUnit() {
        return targetPeriodUnit;
    }

    public Goal setTargetPeriodUnit(String targetPeriodUnit) {
        this.targetPeriodUnit = targetPeriodUnit;
        return this;
    }

    public boolean isSavings() {
        return savings;
    }

    public Goal setSavings(boolean savings) {
        this.savings = savings;
        return this;
    }

    public BigDecimal getWithdrawal() {
        return withdrawal;
    }

    public Goal setWithdrawal(BigDecimal withdrawal) {
        this.withdrawal = withdrawal;
        return this;
    }

    public boolean isClosed() {
        return closed;
    }

    public Goal setClosed(boolean closed) {
        this.closed = closed;
        return this;
    }

    public String getClosedKey() {
        return closedKey;
    }

    public Goal setClosedKey(String closedKey) {
        this.closedKey = closedKey;
        return this;
    }

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
