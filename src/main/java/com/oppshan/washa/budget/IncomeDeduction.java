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
 * One deduction line subtracted from a salary's gross (income tax, social insurance, a fixed levy),
 * owned by one {@code Income}. {@code type} picks how the amount is derived: {@code FIXED} uses the
 * stored {@code amount}, {@code PCT} takes {@code rate}% of {@code base} (or the {@code baseVar}-named
 * value), {@code FORMULA} evaluates {@code expr}, and {@code BRACKETS} sums its {@code brackets}; then
 * {@code floorAmount} and {@code cap} clamp the result. Lines are ordered by {@code ordinal}, and that
 * order matters: a {@code pretax} line lowers the taxable base for every line after it.
 */
@Entity
@Table(name = "income_deduction",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_income_deduction_income_uuid",
                        columnList = "income_uuid"
                ),
        })
public class IncomeDeduction extends UuidEntity<IncomeDeduction> {

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
    @Column(name = "label",
            nullable = false)
    @NotEmpty
    private String label;

    @Basic(optional = false)
    @Enumerated(EnumType.STRING)
    @Column(name = "type",
            nullable = false,
            length = 16)
    @NotNull
    private DeductionType type = DeductionType.FIXED;

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

    @Column(name = "fn",
            length = 32)
    private String fn;

    @Basic(optional = false)
    @Column(name = "pretax",
            nullable = false)
    private boolean pretax = false;

    @Column(name = "var_name",
            length = 64)
    private String varName;

    @Basic(optional = false)
    @Column(name = "var_auto",
            nullable = false)
    private boolean varAuto = false;

    @OneToMany(
            mappedBy = "deduction",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SalaryBracket> brackets;

    /**
     * The {@code Income} (salary) this deduction belongs to.
     */
    public Income getIncome() {
        return income;
    }

    /**
     * Sets the owning salary and returns {@code this}.
     */
    public IncomeDeduction setIncome(Income income) {
        this.income = income;
        return this;
    }

    /**
     * Evaluation order of this deduction within its {@code Income}; order matters because a
     * {@code pretax} line lowers the taxable base for the lines after it.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public IncomeDeduction setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * Human-readable name for this deduction line.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label and returns {@code this}.
     */
    public IncomeDeduction setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * How this deduction's amount is derived: {@code FIXED} (the stored {@code amount}), {@code PCT}
     * ({@code rate}% of {@code base}), {@code FORMULA} ({@code expr}), or {@code BRACKETS} (sum of
     * {@code brackets}). Defaults to {@code FIXED}.
     */
    public DeductionType getType() {
        return type;
    }

    /**
     * Sets the deduction type and returns {@code this}.
     */
    public IncomeDeduction setType(DeductionType type) {
        this.type = type;
        return this;
    }

    /**
     * Which running total a {@code PCT} deduction applies to; a null base defaults to {@code GROSS}.
     */
    public DeductionBase getBase() {
        return base;
    }

    /**
     * Sets the base and returns {@code this}.
     */
    public IncomeDeduction setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    /**
     * Name of a published variable to use as the base instead of a {@code DeductionBase}, when set.
     */
    public String getBaseVar() {
        return baseVar;
    }

    /**
     * Sets the base-variable name and returns {@code this}.
     */
    public IncomeDeduction setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    /**
     * The percentage a {@code PCT} deduction takes of its base.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate and returns {@code this}.
     */
    public IncomeDeduction setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * Upper clamp on the computed amount, when set.
     */
    public BigDecimal getCap() {
        return cap;
    }

    /**
     * Sets the cap and returns {@code this}.
     */
    public IncomeDeduction setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    /**
     * Lower clamp on the computed amount, when set.
     */
    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    /**
     * Sets the floor and returns {@code this}.
     */
    public IncomeDeduction setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    /**
     * The value used when {@code type} is {@code FIXED}. Defaults to zero.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the fixed amount and returns {@code this}.
     */
    public IncomeDeduction setAmount(BigDecimal amount) {
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
    public IncomeDeduction setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    /**
     * Optional function tag. It's persisted but inert: a deduction's amount comes from {@code type},
     * {@code base}, {@code rate}, {@code expr}, and {@code brackets}, not from {@code fn}. An
     * {@code IncomeVariable}, by contrast, carries no {@code fn} at all.
     */
    public String getFn() {
        return fn;
    }

    /**
     * Sets the function tag and returns {@code this}.
     */
    public IncomeDeduction setFn(String fn) {
        this.fn = fn;
        return this;
    }

    /**
     * Whether this line is subtracted before tax. A pretax line lowers the taxable base for every line
     * ordered after it, so ordering matters. Defaults to {@code false}.
     */
    public boolean isPretax() {
        return pretax;
    }

    /**
     * Sets the pretax flag and returns {@code this}.
     */
    public IncomeDeduction setPretax(boolean pretax) {
        this.pretax = pretax;
        return this;
    }

    /**
     * Persisted companion field. Unlike an {@code IncomeComponent} or {@code IncomeVariable}, a
     * deduction doesn't publish its amount under a name, so this is stored but not referenced by name
     * elsewhere.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the companion variable name and returns {@code this}.
     */
    public IncomeDeduction setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    /**
     * Companion flag stored alongside {@code varName}; like it, the payroll math doesn't read it.
     */
    public boolean isVarAuto() {
        return varAuto;
    }

    /**
     * Sets the companion flag and returns {@code this}.
     */
    public IncomeDeduction setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    /**
     * The bracket rows summed when {@code type} is {@code BRACKETS}: this deduction's
     * {@code SalaryBracket} children, cascaded all with orphan removal. Lazily initialised on first
     * access, so it's never null.
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

        if (!(other instanceof final IncomeDeduction that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(label, that.label) &&
               Objects.equals(type, that.type) &&
               Objects.equals(base, that.base) &&
               Objects.equals(baseVar, that.baseVar) &&
               Objects.equals(rate, that.rate) &&
               Objects.equals(cap, that.cap) &&
               Objects.equals(floorAmount, that.floorAmount) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(expr, that.expr) &&
               Objects.equals(fn, that.fn) &&
               pretax == that.pretax &&
               Objects.equals(varName, that.varName) &&
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
                label,
                type,
                base,
                baseVar,
                rate,
                cap,
                floorAmount,
                amount,
                expr,
                fn,
                pretax,
                varName,
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
                .add("label", label)
                .add("type", type)
                .add("base", base)
                .add("baseVar", baseVar)
                .add("rate", rate)
                .add("cap", cap)
                .add("floorAmount", floorAmount)
                .add("amount", amount)
                .add("expr", expr)
                .add("fn", fn)
                .add("pretax", pretax)
                .add("varName", varName)
                .add("varAuto", varAuto)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
