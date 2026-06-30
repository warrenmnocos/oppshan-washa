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

    public Income getIncome() {
        return income;
    }

    public IncomeComponent setIncome(Income income) {
        this.income = income;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public IncomeComponent setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public IncomeComponent setLabel(String label) {
        this.label = label;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public IncomeComponent setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public IncomeComponent setTaxable(boolean taxable) {
        this.taxable = taxable;
        return this;
    }

    public boolean isBasic() {
        return basic;
    }

    public IncomeComponent setBasic(boolean basic) {
        this.basic = basic;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public IncomeComponent setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public IncomeComponent setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

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
