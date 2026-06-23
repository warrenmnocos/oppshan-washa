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
@Table(name = "income_component",
        schema = "washa",
        indexes = {
                @Index(name = "idx_income_component_income_uuid", columnList = "income_uuid"),
        })
public class IncomeComponent extends UuidEntity<IncomeComponent> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "income_uuid", nullable = false)
    @NotNull
    private Income income;

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
    @Column(name = "taxable", nullable = false)
    private boolean taxable = true;

    @Basic(optional = false)
    @Column(name = "basic", nullable = false)
    private boolean basic = false;

    @Column(name = "var_name", length = 64)
    private String varName;

    @Basic(optional = false)
    @Column(name = "var_auto", nullable = false)
    private boolean varAuto = false;

    public Income getIncome() {
        return income;
    }

    public IncomeComponent setIncome(Income income) {
        this.income = income;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public IncomeComponent setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public IncomeComponent setLabel(String label) {
        this.label = label;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public IncomeComponent setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public IncomeComponent setTaxable(boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    public boolean isBasic() {
        return basic;
    }

    public IncomeComponent setBasic(boolean basic) {
        this.basic = basic;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public IncomeComponent setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public IncomeComponent setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }
}
