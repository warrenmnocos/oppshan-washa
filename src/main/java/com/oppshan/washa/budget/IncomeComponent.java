package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
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
import java.util.Objects;

/**
 * One piece of a salary's gross pay (basic pay, an allowance, a bonus), owned by one {@code Income} and
 * ordered within it by {@code ordinal}. Its {@code amount} adds to gross; {@code taxable} decides
 * whether it also adds to the taxable base; {@code basic} marks it as basic pay. A component with a
 * {@code varName} publishes its amount under that name, so sibling {@code IncomeVariable}s and
 * {@code IncomeDeduction}s can reference it by name.
 */
@Entity
@Table(name = "income_component",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_income_component_income_uuid",
                        columnList = "income_uuid"
                ),
        })
public class IncomeComponent extends UuidEntity<IncomeComponent> {

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
    @Column(name = "amount",
            nullable = false)
    @NotNull
    private BigDecimal amount = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "taxable",
            nullable = false)
    private boolean taxable = true;

    @Basic(optional = false)
    @Column(name = "basic",
            nullable = false)
    private boolean basic = false;

    @Column(name = "var_name",
            length = 64)
    private String varName;

    @Basic(optional = false)
    @Column(name = "var_auto",
            nullable = false)
    private boolean varAuto = false;

    /**
     * The {@code Income} (salary) this component belongs to.
     */
    public Income getIncome() {
        return income;
    }

    /**
     * Sets the owning salary and returns {@code this}.
     */
    public IncomeComponent setIncome(Income income) {
        this.income = income;
        return this;
    }

    /**
     * Order of this component within its {@code Income}'s component list.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public IncomeComponent setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * Human-readable name for this pay component.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label and returns {@code this}.
     */
    public IncomeComponent setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * This component's contribution to gross pay. Defaults to zero.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount and returns {@code this}.
     */
    public IncomeComponent setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * Whether this component counts toward taxable gross, the base most percentage and tax deductions
     * apply to. Defaults to {@code true}.
     */
    public boolean isTaxable() {
        return taxable;
    }

    /**
     * Sets the taxable flag and returns {@code this}.
     */
    public IncomeComponent setTaxable(boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    /**
     * Whether this component is part of basic pay. If no component on the salary is flagged basic, all
     * of gross counts as basic. Defaults to {@code false}.
     */
    public boolean isBasic() {
        return basic;
    }

    /**
     * Sets the basic-pay flag and returns {@code this}.
     */
    public IncomeComponent setBasic(boolean basic) {
        this.basic = basic;
        return this;
    }

    /**
     * If set, the name (lowercased) this component's {@code amount} is published under, so sibling
     * {@code IncomeVariable}s and {@code IncomeDeduction}s can reference it. Null when the component
     * isn't exposed by name.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the published variable name and returns {@code this}.
     */
    public IncomeComponent setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    /**
     * A companion flag stored alongside {@code varName}; the payroll math doesn't read it.
     */
    public boolean isVarAuto() {
        return varAuto;
    }

    /**
     * Sets the companion flag and returns {@code this}.
     */
    public IncomeComponent setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
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

        if (!(other instanceof final IncomeComponent that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(label, that.label) &&
               Objects.equals(amount, that.amount) &&
               taxable == that.taxable &&
               basic == that.basic &&
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
                amount,
                taxable,
                basic,
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
                .add("amount", amount)
                .add("taxable", taxable)
                .add("basic", basic)
                .add("varName", varName)
                .add("varAuto", varAuto)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
