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
 * A named intermediate value in a salary's payroll, owned by one {@code Income}. It's derived with the
 * same rule kinds as a deduction ({@code type} / {@code base} / {@code rate} / {@code expr} /
 * {@code brackets}, clamped by {@code floorAmount} and {@code cap}), but instead of subtracting from
 * gross it publishes its result under {@code varName} so later variables and deductions can reference
 * it. That's why it carries no {@code pretax} or {@code fn} field like a deduction does: a variable
 * never touches the taxable base, it's only ever a stepping stone, resolved ahead of the deductions
 * that use it (both sets ordered by {@code ordinal}).
 */
@Entity
@Table(name = "income_variable",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_income_variable_income_uuid",
                        columnList = "income_uuid"
                ),
        })
public class IncomeVariable extends UuidEntity<IncomeVariable> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "income_uuid",
            nullable = false
    )
    @NotNull
    private Income income;

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
    private List<SalaryBracket> brackets;

    /**
     * The {@code Income} (salary) this variable belongs to.
     */
    public Income getIncome() {
        return income;
    }

    /**
     * Sets the owning salary and returns {@code this}.
     */
    public IncomeVariable setIncome(Income income) {
        this.income = income;
        return this;
    }

    /**
     * Evaluation order of this variable within its {@code Income}; variables resolve ahead of the
     * deductions that reference them.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public IncomeVariable setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * Required. The lowercased name this variable publishes its result under, and the reason it exists:
     * later variables and deductions reference it by this name.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the published variable name and returns {@code this}.
     */
    public IncomeVariable setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    /**
     * Optional human-readable name for this variable.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label and returns {@code this}.
     */
    public IncomeVariable setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * How this variable's value is derived. Defaults to {@code FORMULA}.
     */
    public VariableType getType() {
        return type;
    }

    /**
     * Sets the variable type and returns {@code this}.
     */
    public IncomeVariable setType(VariableType type) {
        this.type = type;
        return this;
    }

    /**
     * A {@code DeductionBase} picking which running total the value builds on, when the rule needs one.
     */
    public DeductionBase getBase() {
        return base;
    }

    /**
     * Sets the base and returns {@code this}.
     */
    public IncomeVariable setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    /**
     * Name of a published variable to use as the base, when set.
     */
    public String getBaseVar() {
        return baseVar;
    }

    /**
     * Sets the base-variable name and returns {@code this}.
     */
    public IncomeVariable setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    /**
     * The percentage taken of the base, when the rule uses one.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate and returns {@code this}.
     */
    public IncomeVariable setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * Upper clamp on the computed value, when set.
     */
    public BigDecimal getCap() {
        return cap;
    }

    /**
     * Sets the cap and returns {@code this}.
     */
    public IncomeVariable setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    /**
     * Lower clamp on the computed value, when set.
     */
    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    /**
     * Sets the floor and returns {@code this}.
     */
    public IncomeVariable setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    /**
     * This variable's stored amount. Defaults to zero.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount and returns {@code this}.
     */
    public IncomeVariable setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * The expression evaluated when {@code type} is {@code FORMULA}.
     */
    public String getExpr() {
        return expr;
    }

    /**
     * Sets the formula expression and returns {@code this}.
     */
    public IncomeVariable setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    /**
     * A companion flag stored alongside this variable; the payroll math doesn't read it.
     */
    public boolean isVarAuto() {
        return varAuto;
    }

    /**
     * Sets the companion flag and returns {@code this}.
     */
    public IncomeVariable setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    /**
     * This variable's {@code SalaryBracket} children, summed for a bracket-style {@code type}, cascaded
     * all with orphan removal. Lazily initialised on first access, so it's never null.
     */
    public List<SalaryBracket> getBrackets() {
        brackets = Objects.requireNonNullElseGet(brackets, ArrayList::new);
        return brackets;
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

        if (!(other instanceof final IncomeVariable that)) {
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
     * Hashes the same fields {@code equals} compares.
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
     * Renders the identifying fields and audit triple for logging.
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
