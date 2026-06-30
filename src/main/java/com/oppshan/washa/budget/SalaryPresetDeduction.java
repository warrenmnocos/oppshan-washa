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

@Entity
@Table(name = "salary_preset_deduction",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_preset_deduction_preset_uuid",
                        columnList = "salary_preset_uuid"
                ),
        })
public class SalaryPresetDeduction extends UuidEntity<SalaryPresetDeduction> {

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
    private List<SalaryPresetBracket> brackets;

    public SalaryPreset getSalaryPreset() {
        return salaryPreset;
    }

    public SalaryPresetDeduction setSalaryPreset(SalaryPreset salaryPreset) {
        this.salaryPreset = salaryPreset;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public SalaryPresetDeduction setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getLabel() {
        return label;
    }

    public SalaryPresetDeduction setLabel(String label) {
        this.label = label;
        return this;
    }

    public DeductionType getType() {
        return type;
    }

    public SalaryPresetDeduction setType(DeductionType type) {
        this.type = type;
        return this;
    }

    public DeductionBase getBase() {
        return base;
    }

    public SalaryPresetDeduction setBase(DeductionBase base) {
        this.base = base;
        return this;
    }

    public String getBaseVar() {
        return baseVar;
    }

    public SalaryPresetDeduction setBaseVar(String baseVar) {
        this.baseVar = baseVar;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public SalaryPresetDeduction setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public BigDecimal getCap() {
        return cap;
    }

    public SalaryPresetDeduction setCap(BigDecimal cap) {
        this.cap = cap;
        return this;
    }

    public BigDecimal getFloorAmount() {
        return floorAmount;
    }

    public SalaryPresetDeduction setFloorAmount(BigDecimal floorAmount) {
        this.floorAmount = floorAmount;
        return this;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public SalaryPresetDeduction setAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }

    public String getExpr() {
        return expr;
    }

    public SalaryPresetDeduction setExpr(String expr) {
        this.expr = expr;
        return this;
    }

    public String getFn() {
        return fn;
    }

    public SalaryPresetDeduction setFn(String fn) {
        this.fn = fn;
        return this;
    }

    public boolean isPretax() {
        return pretax;
    }

    public SalaryPresetDeduction setPretax(boolean pretax) {
        this.pretax = pretax;
        return this;
    }

    public String getVarName() {
        return varName;
    }

    public SalaryPresetDeduction setVarName(String varName) {
        this.varName = varName;
        return this;
    }

    public boolean isVarAuto() {
        return varAuto;
    }

    public SalaryPresetDeduction setVarAuto(boolean varAuto) {
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

        if (!(other instanceof final SalaryPresetDeduction that)) {
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
