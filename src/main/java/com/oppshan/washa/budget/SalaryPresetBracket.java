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

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One additive row of a graduated schedule on a {@link SalaryPreset}. It mirrors {@link SalaryBracket}
 * exactly, except its parent is a {@link SalaryPresetDeduction} or a {@link SalaryPresetVariable}
 * instead of the live {@code IncomeDeduction} / {@code IncomeVariable}; both parent foreign keys are
 * nullable and exactly one is set.
 *
 * <p>See {@code SalaryBracket} for how a row evaluates: the left-hand value ({@code varName}, default
 * {@code taxable}) tested by {@code op} against {@code val}, then a contribution per {@code type} that
 * sums with the other qualifying rows.
 */
@Entity
@Table(name = "salary_preset_bracket",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_preset_bracket_deduction_uuid",
                        columnList = "deduction_uuid"
                ),
                @Index(
                        name = "idx_salary_preset_bracket_variable_uuid",
                        columnList = "variable_uuid"
                ),
        })
public class SalaryPresetBracket extends UuidEntity<SalaryPresetBracket> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deduction_uuid")
    private SalaryPresetDeduction deduction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variable_uuid")
    private SalaryPresetVariable variable;

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

    /**
     * The preset deduction this bracket belongs to, when its parent is a deduction. Exactly one of
     * {@code deduction} and {@code variable} is set.
     */
    public SalaryPresetDeduction getDeduction() {
        return deduction;
    }

    /**
     * Sets the owning preset deduction; returns {@code this}.
     */
    public SalaryPresetBracket setDeduction(SalaryPresetDeduction deduction) {
        this.deduction = deduction;
        return this;
    }

    /**
     * The preset variable this bracket belongs to, when its parent is a variable. The other side of
     * {@link #getDeduction()}; exactly one is set.
     */
    public SalaryPresetVariable getVariable() {
        return variable;
    }

    /**
     * Sets the owning preset variable; returns {@code this}.
     */
    public SalaryPresetBracket setVariable(SalaryPresetVariable variable) {
        this.variable = variable;
        return this;
    }

    /**
     * This bracket's position within its parent's schedule; display and storage order.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the position; returns {@code this}.
     */
    public SalaryPresetBracket setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The left-hand variable this row tests. {@code null} means {@code taxable}.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the tested variable name (nullable); returns {@code this}.
     */
    public SalaryPresetBracket setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    /**
     * How the left-hand value is compared against {@code val}. {@code null} means {@code GT}.
     */
    public BracketOp getOp() {
        return op;
    }

    /**
     * Sets the comparison operator (nullable); returns {@code this}.
     */
    public SalaryPresetBracket setOp(BracketOp op) {
        this.op = op;
        return this;
    }

    /**
     * The threshold the left-hand value is compared against.
     */
    public BigDecimal getVal() {
        return val;
    }

    /**
     * Sets the threshold; returns {@code this}.
     */
    public SalaryPresetBracket setVal(BigDecimal val) {
        this.val = val;
        return this;
    }

    /**
     * How a qualifying row contributes: a flat {@code rate}, a percent of gross or basic, or an
     * {@code expr} formula. {@code null} means {@code FIXED}.
     */
    public BracketType getType() {
        return type;
    }

    /**
     * Sets the contribution type (nullable); returns {@code this}.
     */
    public SalaryPresetBracket setType(BracketType type) {
        this.type = type;
        return this;
    }

    /**
     * The flat amount for a {@code FIXED} row, or the percent for a {@code PCTGROSS} /
     * {@code PCTBASIC} row.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate or flat amount; returns {@code this}.
     */
    public SalaryPresetBracket setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * The formula for a {@code FORMULA}-type row.
     */
    public String getExpr() {
        return expr;
    }

    /**
     * Sets the formula; returns {@code this}.
     */
    public SalaryPresetBracket setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    /**
     * Two brackets are equal when their UUID, audit timestamps, and scalar fields match.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final SalaryPresetBracket that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(varName, that.varName) &&
               Objects.equals(op, that.op) &&
               Objects.equals(val, that.val) &&
               Objects.equals(type, that.type) &&
               Objects.equals(rate, that.rate) &&
               Objects.equals(expr, that.expr) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /**
     * Hashes the same fields {@link #equals(Object)} compares.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                ordinal,
                varName,
                op,
                val,
                type,
                rate,
                expr,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * A debug string of this bracket's fields; excludes both parent links.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("ordinal", ordinal)
                .add("varName", varName)
                .add("op", op)
                .add("val", val)
                .add("type", type)
                .add("rate", rate)
                .add("expr", expr)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
