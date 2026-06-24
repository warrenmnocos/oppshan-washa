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

/** Named intermediate computation (same engine as a deduction; produces var_name; no pretax/fn). */
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

    public SalaryPreset getSalaryPreset() {
        return salaryPreset;
    }

    public SalaryPresetVariable setSalaryPreset(SalaryPreset salaryPreset) {
        this.salaryPreset = salaryPreset;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public SalaryPresetVariable setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public SalaryPresetVariable setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public SalaryPresetVariable setLabel(String label) {
        this.label = label;
        return this;
    }

    public VariableType getType() {
        return type;
    }

    public SalaryPresetVariable setType(VariableType type) {
        this.type = type;
        return this;
    }

    public DeductionBase getBase() {
        return base;
    }

    public SalaryPresetVariable setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    public String getBaseVar() {
        return baseVar;
    }

    public SalaryPresetVariable setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public SalaryPresetVariable setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public BigDecimal getCap() {
        return cap;
    }

    public SalaryPresetVariable setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    public SalaryPresetVariable setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SalaryPresetVariable setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getExpr() {
        return expr;
    }

    public SalaryPresetVariable setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public SalaryPresetVariable setVarAuto(boolean varAuto) {
        this.varAuto = varAuto;
        return this;
    }

    public List<SalaryPresetBracket> getBrackets() {
        brackets = Objects.requireNonNullElseGet(brackets, ArrayList::new);
        return brackets;
    }

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
