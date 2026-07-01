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
 * One earnings line of a {@link SalaryPreset} (basic pay, an allowance, a bonus). It mirrors
 * {@link IncomeComponent} field-for-field, swapping the live {@code income} owner for a
 * {@code salaryPreset} one, so a preset stores the same component shape a month's salary does. Lazy,
 * cascade-owned child of the preset.
 */
@Entity
@Table(name = "salary_preset_component",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_preset_component_preset_uuid",
                        columnList = "salary_preset_uuid"
                ),
        })
public class SalaryPresetComponent extends UuidEntity<SalaryPresetComponent> {

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
     * The preset this earnings line belongs to.
     */
    public SalaryPreset getSalaryPreset() {
        return salaryPreset;
    }

    /**
     * Sets the owning preset; returns {@code this}.
     */
    public SalaryPresetComponent setSalaryPreset(SalaryPreset salaryPreset) {
        this.salaryPreset = salaryPreset;
        return this;
    }

    /**
     * This line's position within the preset's component list; display and storage order.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the position; returns {@code this}.
     */
    public SalaryPresetComponent setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The earnings line's label (e.g. "Basic", "Housing allowance").
     */
    public String getLabel() {
        return label;
    }

    /**
     * Sets the label; returns {@code this}.
     */
    public SalaryPresetComponent setLabel(String label) {
        this.label = label;
        return this;
    }

    /**
     * The earnings amount for this line, in the preset's currency.
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Sets the amount; returns {@code this}.
     */
    public SalaryPresetComponent setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    /**
     * Whether this line counts toward taxable gross. Defaults to {@code true}.
     */
    public boolean isTaxable() {
        return taxable;
    }

    /**
     * Sets whether the line is taxable; returns {@code this}.
     */
    public SalaryPresetComponent setTaxable(boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    /**
     * Whether this line is the "basic salary". If no component is flagged basic, gross stands in for
     * basic.
     */
    public boolean isBasic() {
        return basic;
    }

    /**
     * Sets the basic-salary flag; returns {@code this}.
     */
    public SalaryPresetComponent setBasic(boolean basic) {
        this.basic = basic;
        return this;
    }

    /**
     * A name that exposes this line's amount to later formulas, or {@code null} to not expose it.
     */
    public String getVarName() {
        return varName;
    }

    /**
     * Sets the formula variable name (nullable); returns {@code this}.
     */
    public SalaryPresetComponent setVarName(String varName) {
        this.varName = varName;
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
    public SalaryPresetComponent setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    /**
     * Two components are equal when their UUID, audit timestamps, and scalar fields match.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final SalaryPresetComponent that)) {
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
     * Hashes the same fields {@link #equals(Object)} compares.
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
     * A debug string of this line's fields; excludes the parent preset.
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
