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

import java.io.Serial;
import java.math.BigDecimal;

/** Additive bracket row; belongs to exactly one parent — a deduction OR a variable. */
@Entity
@Table(name = "salary_bracket",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_bracket_deduction_uuid",
                        columnList = "deduction_uuid"
                ),
                @Index(
                        name = "idx_salary_bracket_variable_uuid",
                        columnList = "variable_uuid"
                ),
        })
public class SalaryBracket extends UuidEntity<SalaryBracket> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deduction_uuid")
    private IncomeDeduction deduction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variable_uuid")
    private IncomeVariable variable;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Column(name = "var_name",
            length = 64)
    private String varName;

    @Enumerated(EnumType.STRING)
    @Column(name = "op",
            length = 8)
    private BracketOp op;

    @Column(name = "val")
    private BigDecimal val;

    @Enumerated(EnumType.STRING)
    @Column(name = "type",
            length = 16)
    private BracketType type;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "expr")
    private String expr;

    public IncomeDeduction getDeduction() {
        return deduction;
    }

    public SalaryBracket setDeduction(IncomeDeduction deduction) {
        this.deduction = deduction;
        return this;
    }

    public IncomeVariable getVariable() {
        return variable;
    }

    public SalaryBracket setVariable(IncomeVariable variable) {
        this.variable = variable;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public SalaryBracket setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public SalaryBracket setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public BracketOp getOp() {
        return op;
    }

    public SalaryBracket setOp(BracketOp op) {
        this.op = op;
        return this;
    }

    public BigDecimal getVal() {
        return val;
    }

    public SalaryBracket setVal(BigDecimal val) {
        this.val = val;
        return this;
    }

    public BracketType getType() {
        return type;
    }

    public SalaryBracket setType(BracketType type) {
        this.type = type;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public SalaryBracket setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public String getExpr() {
        return expr;
    }

    public SalaryBracket setExpr(String expr) {
        this.expr = expr;
        return this;
    }
}
