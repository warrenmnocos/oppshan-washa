package com.oppshan.washa.budget;

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
@Table(name = "debt",
        schema = "washa",
        indexes = {
                @Index(name = "idx_debt_budget_month_uuid", columnList = "budget_month_uuid"),
        })
public class Debt extends UuidEntity<Debt> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_month_uuid", nullable = false)
    @NotNull
    private BudgetMonth budgetMonth;

    @Basic(optional = false)
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "name", nullable = false)
    @NotEmpty
    private String name;

    @Basic(optional = false)
    @Column(name = "principal", nullable = false)
    @NotNull
    private BigDecimal principal = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "annual_rate", nullable = false)
    @NotNull
    private BigDecimal annualRate = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "monthly", nullable = false)
    @NotNull
    private BigDecimal monthly = BigDecimal.ZERO;

    @Column(name = "term_months")
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "reprice_mode", length = 16)
    private DebtRepriceMode repriceMode;

    @Basic(optional = false)
    @Column(name = "currency", nullable = false, length = 3)
    @NotEmpty
    private String currency;

    @Basic(optional = false)
    @Column(name = "prepay", nullable = false)
    private boolean prepay = false;

    @Basic(optional = false)
    @Column(name = "prepay_amount", nullable = false)
    @NotNull
    private BigDecimal prepayAmount = BigDecimal.ZERO;

    @Column(name = "prepay_currency", length = 3)
    private String prepayCurrency;

    @OneToMany(mappedBy = "debt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DebtRateStep> rateSteps;

    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    public Debt setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Debt setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getName() {
        return name;
    }

    public Debt setName(String name) {
        this.name = name;
        return this;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public Debt setPrincipal(BigDecimal principal) {
        this.principal = principal;
        return this;
    }

    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    public Debt setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
        return this;
    }

    public BigDecimal getMonthly() {
        return monthly;
    }

    public Debt setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
        return this;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public Debt setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
        return this;
    }

    public DebtRepriceMode getRepriceMode() {
        return repriceMode;
    }

    public Debt setRepriceMode(DebtRepriceMode repriceMode) {
        this.repriceMode = repriceMode;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public Debt setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public boolean isPrepay() {
        return prepay;
    }

    public Debt setPrepay(boolean prepay) {
        this.prepay = prepay;
        return this;
    }

    public BigDecimal getPrepayAmount() {
        return prepayAmount;
    }

    public Debt setPrepayAmount(BigDecimal prepayAmount) {
        this.prepayAmount = prepayAmount;
        return this;
    }

    public String getPrepayCurrency() {
        return prepayCurrency;
    }

    public Debt setPrepayCurrency(String prepayCurrency) {
        this.prepayCurrency = prepayCurrency;
        return this;
    }

    public List<DebtRateStep> getRateSteps() {
        rateSteps = Objects.requireNonNullElseGet(rateSteps, ArrayList::new);
        return rateSteps;
    }
}
