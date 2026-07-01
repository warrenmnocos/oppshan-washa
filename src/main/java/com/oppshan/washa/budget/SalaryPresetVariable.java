package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
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
import java.util.Objects;

/**
 * A named intermediate value of a {@link SalaryPreset}: computed with the same rule kinds a deduction
 * uses, then bound into the formula scope under {@code varName} so later deductions and variables can
 * reference it. It mirrors {@link IncomeVariable} field-for-field (no {@code pretax}, no {@code fn}),
 * swapping the live {@code income} owner for a {@code salaryPreset} one. Lazy, cascade-owned child of
 * the preset.
 *
 * <p>Variables are evaluated before deductions, so a variable can feed a later deduction line but not
 * the other way around.
 */
@Entity
@Table(name = "salary_preset_variable",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_preset_variable_preset_uuid",
                        columnList = "salary_preset_uuid"
                ),
        })
public class SalaryPresetVariable extends UuidEntity<SalaryPresetVariable> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "salary_preset_uuid",
            nullable = false
    )
    @NotNull
    private SalaryPreset salaryPreset;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "var_name",
            nullable = false,
            length = 64)
    @NotEmpty
    private String varName;

    @Column(name = "label")
    private String label;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "type",
            nullable = false,
            length = 16)
    @NotNull
    private VariableType type = VariableType.FORMULA;

    @Enumerated(EnumType.STRING)
    @Column(name = "base",
            length = 16)
    private DeductionBase base;

    @Column(name = "base_var",
            length = 64)
    private String baseVar;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "cap")
    private BigDecimal cap;

    @Column(name = "floor_amount")
    private BigDecimal floorAmount;

    @Basic(optional = false)
    @Column(name = "amount",
            nullable = false)
    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "expr")
    private String expr;

    @Basic(optional = false)
    @Column(name = "var_auto",
            nullable = false)
    private boolean varAuto = false;

    @OneToMany(
            mappedBy = "variable",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SalaryPresetBracket> brackets;

    /**
     * The preset this variable belongs to.
     */
    public SalaryPreset getSalaryPreset() {
        return salaryPreset;
    }

    /**
     * Sets the owning preset; returns {@code this}.
     */
    public SalaryPresetVariable setSalaryPreset(SalaryPreset salaryPreset) {
        this.salaryPreset = salaryPreset;
        return this;
    }

    /**
     * This variable's position within the preset's variable list. Order matters, since a variable can
     * feed later lines.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the position; returns {@code this}.
     */
    public SalaryPresetVariable setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The scope key later formulas reference this value by. Required, since a variable exists to be
     * referenced.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the scope key; returns {@code this}.
     */
    public SalaryPresetVariable setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    /**
     * An optional human-readable label for the variable.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label (nullable); returns {@code this}.
     */
    public SalaryPresetVariable setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * How the value is computed, the same rule kinds a deduction uses. Defaults to {@code FORMULA}.
     */
    public VariableType getType() {
        return type;
    }

    /**
     * Sets the computation type; returns {@code this}.
     */
    public SalaryPresetVariable setType(VariableType type) {
        this.type = type;
        return this;
    }

    /**
     * What a {@code PCT} variable takes its percent of. {@code null} means {@code GROSS}.
     */
    public DeductionBase getBase() {
        return base;
    }

    /**
     * Sets the percent base (nullable); returns {@code this}.
     */
    public SalaryPresetVariable setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    /**
     * The variable a {@code base=VAR} computation reads its base from.
     */
    public String getBaseVar() {
        return baseVar;
    }

    /**
     * Sets the base variable name; returns {@code this}.
     */
    public SalaryPresetVariable setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    /**
     * The percent for a {@code PCT} variable.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the percent; returns {@code this}.
     */
    public SalaryPresetVariable setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * The maximum the computed value is capped at, or {@code null} for no cap.
     */
    public BigDecimal getCap() {
        return cap;
    }

    /**
     * Sets the cap (nullable); returns {@code this}.
     */
    public SalaryPresetVariable setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    /**
     * Lower bound on the computed value, or {@code null} for no floor.
     */
    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    /**
     * Sets the floor (nullable); returns {@code this}.
     */
    public SalaryPresetVariable setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    /**
     * The flat amount for a fixed-value variable.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the flat amount; returns {@code this}.
     */
    public SalaryPresetVariable setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * The formula for a {@code FORMULA} variable.
     */
    public String getExpr() {
        return expr;
    }

    /**
     * Sets the formula; returns {@code this}.
     */
    public SalaryPresetVariable setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    /**
     * A hint flag that's persisted and round-tripped but never read when computing the salary.
     */
    public boolean isVarAuto() {
        return varAuto;
    }

    /**
     * Sets the hint flag; returns {@code this}.
     */
    public SalaryPresetVariable setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    /**
     * This variable's graduated rows, its {@link SalaryPresetBracket} children (used when it sums
     * over brackets). Lazily initialized so it's never null.
     */
    public List<SalaryPresetBracket> getBrackets() {
        brackets = Objects.requireNonNullElseGet(brackets, ArrayList::new);
        return brackets;
    }

    /**
     * Two variables are equal when their UUID, audit timestamps, and scalar fields match; the
     * brackets aren't compared.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final SalaryPresetVariable that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(varName, that.varName) &&
               Objects.equals(label, that.label) &&
               Objects.equals(type, that.type) &&
               Objects.equals(base, that.base) &&
               Objects.equals(baseVar, that.baseVar) &&
               Objects.equals(rate, that.rate) &&
               Objects.equals(cap, that.cap) &&
               Objects.equals(floorAmount, that.floorAmount) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(expr, that.expr) &&
               varAuto == that.varAuto &&
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
                label,
                type,
                base,
                baseVar,
                rate,
                cap,
                floorAmount,
                amount,
                expr,
                varAuto,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * A debug string of this variable's scalar fields; excludes the parent preset and brackets.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("ordinal", ordinal)
                .add("varName", varName)
                .add("label", label)
                .add("type", type)
                .add("base", base)
                .add("baseVar", baseVar)
                .add("rate", rate)
                .add("cap", cap)
                .add("floorAmount", floorAmount)
                .add("amount", amount)
                .add("expr", expr)
                .add("varAuto", varAuto)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
