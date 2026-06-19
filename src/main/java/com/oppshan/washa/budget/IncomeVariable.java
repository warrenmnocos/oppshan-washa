package com.oppshan.washa.budget;

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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Named intermediate computation (same engine as a deduction; produces var_name; no pretax/fn). */
@Entity
@Table(name = "income_variable",
        schema = "public",
        indexes = {
                @Index(name = "idx_income_variable_income_uuid", columnList = "income_uuid"),
        })
public class IncomeVariable extends UuidEntity<IncomeVariable> {

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
    @Column(name = "var_name", nullable = false, length = 64)
    @NotEmpty
    private String varName;

    @Column(name = "label")
    private String label;

    @Basic(optional = false)
    @Column(name = "kind", nullable = false, length = 16)
    @NotEmpty
    private String kind = "formula";

    @Column(name = "base", length = 16)
    private String base;

    @Column(name = "base_var", length = 64)
    private String baseVar;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "cap")
    private BigDecimal cap;

    @Column(name = "floor_amount")
    private BigDecimal floorAmount;

    @Basic(optional = false)
    @Column(name = "amount", nullable = false)
    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "expr")
    private String expr;

    @Basic(optional = false)
    @Column(name = "var_auto", nullable = false)
    private boolean varAuto = false;

    @OneToMany(mappedBy = "variable", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SalaryBracket> brackets = new ArrayList<>();

    public Income getIncome() {
        return income;
    }

    public IncomeVariable setIncome(Income income) {
        this.income = income;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public IncomeVariable setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public IncomeVariable setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public IncomeVariable setLabel(String label) {
        this.label = label;
        return this;
    }

    public String getKind() {
        return kind;
    }

    public IncomeVariable setKind(String kind) {
        this.kind = kind;
        return this;
    }

    public String getBase() {
        return base;
    }

    public IncomeVariable setBase(String base) {
        this.base = base;
        return this;
    }

    public String getBaseVar() {
        return baseVar;
    }

    public IncomeVariable setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public IncomeVariable setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public BigDecimal getCap() {
        return cap;
    }

    public IncomeVariable setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    public IncomeVariable setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public IncomeVariable setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getExpr() {
        return expr;
    }

    public IncomeVariable setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public IncomeVariable setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    public List<SalaryBracket> getBrackets() {
        return brackets;
    }
}
