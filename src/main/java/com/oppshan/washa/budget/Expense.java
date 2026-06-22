package com.oppshan.washa.budget;

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

@Entity
@Table(name = "expense",
        schema = "oppshan",
        indexes = {
                @Index(name = "idx_expense_budget_month_uuid", columnList = "budget_month_uuid"),
        })
public class Expense extends UuidEntity<Expense> {

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

    @Column(name = "auto", length = 64)
    private String auto;

    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    public Expense setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Expense setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public Expense setLabel(String label) {
        this.label = label;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Expense setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public Expense setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public String getAuto() {
        return auto;
    }

    public Expense setAuto(String auto) {
        this.auto = auto;
        return this;
    }
}
