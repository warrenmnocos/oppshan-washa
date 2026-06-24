package com.oppshan.washa.budget;

import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "income_deduction",
        schema = "washa",
        indexes = {
                @Index(name = "idx_income_deduction_income_uuid", columnList = "income_uuid"),
        })
public class IncomeDeduction extends UuidEntity<IncomeDeduction> {

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
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    @NotNull
    private DeductionType type = DeductionType.FIXED;

    @Enumerated(EnumType.STRING)
    @Column(name = "base", length = 16)
    private DeductionBase base;

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

    @Column(name = "fn", length = 32)
    private String fn;

    @Basic(optional = false)
    @Column(name = "pretax", nullable = false)
    private boolean pretax = false;

    @Column(name = "var_name", length = 64)
    private String varName;

    @Basic(optional = false)
    @Column(name = "var_auto", nullable = false)
    private boolean varAuto = false;

    @OneToMany(mappedBy = "deduction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<SalaryBracket> brackets = new ArrayList<>();

    public Income getIncome() {
        return income;
    }

    public IncomeDeduction setIncome(Income income) {
        this.income = income;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public IncomeDeduction setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public IncomeDeduction setLabel(String label) {
        this.label = label;
        return this;
    }

    public DeductionType getType() {
        return type;
    }

    public IncomeDeduction setType(DeductionType type) {
        this.type = type;
        return this;
    }

    public DeductionBase getBase() {
        return base;
    }

    public IncomeDeduction setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    public String getBaseVar() {
        return baseVar;
    }

    public IncomeDeduction setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public IncomeDeduction setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public BigDecimal getCap() {
        return cap;
    }

    public IncomeDeduction setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    public IncomeDeduction setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public IncomeDeduction setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getExpr() {
        return expr;
    }

    public IncomeDeduction setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    public String getFn() {
        return fn;
    }

    public IncomeDeduction setFn(String fn) {
        this.fn = fn;
        return this;
    }

    public boolean isPretax() {
        return pretax;
    }

    public IncomeDeduction setPretax(boolean pretax) {
        this.pretax = pretax;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public IncomeDeduction setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public IncomeDeduction setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    public List<SalaryBracket> getBrackets() {
        return brackets;
    }
}
