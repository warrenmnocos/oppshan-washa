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

    public SalaryPreset getSalaryPreset() {
        return salaryPreset;
    }

    public SalaryPresetComponent setSalaryPreset(SalaryPreset salaryPreset) {
        this.salaryPreset = salaryPreset;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public SalaryPresetComponent setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public SalaryPresetComponent setLabel(String label) {
        this.label = label;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SalaryPresetComponent setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public SalaryPresetComponent setTaxable(boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    public boolean isBasic() {
        return basic;
    }

    public SalaryPresetComponent setBasic(boolean basic) {
        this.basic = basic;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public SalaryPresetComponent setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public SalaryPresetComponent setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

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
