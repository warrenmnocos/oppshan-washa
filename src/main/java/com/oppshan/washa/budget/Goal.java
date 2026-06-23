package com.oppshan.washa.budget;

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

import java.io.Serial;
import java.math.BigDecimal;

@Entity
@Table(name = "goal",
        schema = "washa",
        indexes = {
                @Index(name = "idx_goal_budget_month_uuid", columnList = "budget_month_uuid"),
                @Index(name = "idx_goal_label_currency", columnList = "label,currency"),
        })
public class Goal extends UuidEntity<Goal> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_month_uuid", nullable = false)
    @NotNull
    private BudgetMonth budgetMonth;

    @Basic(optional = false)
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "label", nullable = false)
    @NotEmpty
    private String label;

    @Basic(optional = false)
    @Column(name = "amount", nullable = false)
    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "currency", nullable = false, length = 3)
    @NotEmpty
    private String currency;

    @Enumerated(EnumType.STRING)
    @Basic(optional = false)
    @Column(name = "target_type", nullable = false, length = 16)
    @NotNull
    private TargetType targetType = TargetType.open;

    @Column(name = "target_amount")
    private BigDecimal targetAmount;

    @Column(name = "target_base", length = 32)
    private String targetBase;

    @Column(name = "target_mult")
    private BigDecimal targetMult;

    @Basic(optional = false)
    @Column(name = "savings", nullable = false)
    private boolean savings = false;

    @Basic(optional = false)
    @Column(name = "withdrawal", nullable = false)
    @NotNull
    private BigDecimal withdrawal = BigDecimal.ZERO;

    public enum TargetType {
        open, amount, relative
    }

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

    public TargetType getTargetType() {
        return targetType;
    }

    public Goal setTargetType(TargetType targetType) {
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
}
