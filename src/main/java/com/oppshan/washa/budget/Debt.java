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
 * One debt line in a budget month's plan, holding everything a payoff projection needs: a
 * {@code principal}, an {@code annualRate}, a scheduled {@code monthly} payment, and an optional
 * {@code termMonths}. It's a lazy, cascade-owned child of {@code BudgetMonth} (one of the month's
 * incomes, expenses, goals, and debts), ordered within the month by {@code ordinal}.
 *
 * <p>Two things make a debt more than a flat loan. Scheduled rate changes live in the
 * {@link DebtRateStep} children (a fixed-then-floating mortgage, say), and when a step moves the
 * rate mid-loan the {@link DebtRepriceMode} decides what gives: re-amortize the payment, or keep it
 * and let the term stretch. Optional annual prepayment ({@code prepay} plus {@code prepayAmount})
 * puts down extra principal, and together {@code monthly} and that prepayment make up this debt's
 * share of the month's money-out.
 */
@Entity
@Table(name = "debt",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_debt_budget_month_uuid",
                        columnList = "budget_month_uuid"
                ),
        })
public class Debt extends UuidEntity<Debt> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "budget_month_uuid",
            nullable = false
    )
    @NotNull
    private BudgetMonth budgetMonth;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "name",
            nullable = false)
    @NotEmpty
    private String name;

    @Basic(optional = false)
    @Column(name = "principal",
            nullable = false)
    @NotNull
    private BigDecimal principal = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "annual_rate",
            nullable = false)
    @NotNull
    private BigDecimal annualRate = BigDecimal.ZERO;

    @Basic(optional = false)
    @Column(name = "monthly",
            nullable = false)
    @NotNull
    private BigDecimal monthly = BigDecimal.ZERO;

    @Column(name = "term_months")
    private Integer termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "reprice_mode",
            length = 16)
    private DebtRepriceMode repriceMode;

    @Basic(optional = false)
    @Column(name = "currency",
            nullable = false,
            length = 3)
    @NotEmpty
    private String currency;

    @Basic(optional = false)
    @Column(name = "prepay",
            nullable = false)
    private boolean prepay = false;

    @Basic(optional = false)
    @Column(name = "prepay_amount",
            nullable = false)
    @NotNull
    private BigDecimal prepayAmount = BigDecimal.ZERO;

    @Column(name = "prepay_currency",
            length = 3)
    private String prepayCurrency;

    @OneToMany(
            mappedBy = "debt",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<DebtRateStep> rateSteps;

    /**
     * The budget month this debt belongs to. Every debt is owned by exactly one month.
     */
    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    /**
     * Sets the owning budget month; returns {@code this}.
     */
    public Debt setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    /**
     * This debt's position within the month's debt list. It fixes display order and nothing else.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the display position; returns {@code this}.
     */
    public Debt setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The debt's label, like "Mortgage" or "Car loan".
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the label; returns {@code this}.
     */
    public Debt setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * The outstanding balance still owed on this debt.
     */
    public BigDecimal getPrincipal() {
        return principal;
    }

    /**
     * Sets the outstanding balance; returns {@code this}.
     */
    public Debt setPrincipal(BigDecimal principal) {
        this.principal = principal;
        return this;
    }

    /**
     * The annual interest rate as a percent, so {@code 2.5} means 2.5%.
     */
    public BigDecimal getAnnualRate() {
        return annualRate;
    }

    /**
     * Sets the annual rate; returns {@code this}.
     */
    public Debt setAnnualRate(BigDecimal annualRate) {
        this.annualRate = annualRate;
        return this;
    }

    /**
     * The scheduled monthly payment. It's also this debt's amortization outflow within the month's
     * money-out.
     */
    public BigDecimal getMonthly() {
        return monthly;
    }

    /**
     * Sets the monthly payment; returns {@code this}.
     */
    public Debt setMonthly(BigDecimal monthly) {
        this.monthly = monthly;
        return this;
    }

    /**
     * The loan's length in months, or {@code null} to derive the term from the principal, rate, and
     * payment instead.
     */
    public Integer getTermMonths() {
        return termMonths;
    }

    /**
     * Sets the term in months (nullable); returns {@code this}.
     */
    public Debt setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
        return this;
    }

    /**
     * How a mid-loan rate change gets absorbed, or {@code null}, which behaves like {@code TERM}:
     * keep the payment and let the term stretch.
     */
    public DebtRepriceMode getRepriceMode() {
        return repriceMode;
    }

    /**
     * Sets the reprice mode (nullable); returns {@code this}.
     */
    public Debt setRepriceMode(DebtRepriceMode repriceMode) {
        this.repriceMode = repriceMode;
        return this;
    }

    /**
     * Three-letter currency code for this debt's principal and payment amounts.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency code; returns {@code this}.
     */
    public Debt setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * Whether the annual extra-principal prepayment is switched on.
     */
    public boolean isPrepay() {
        return prepay;
    }

    /**
     * Sets whether prepayment is on; returns {@code this}.
     */
    public Debt setPrepay(boolean prepay) {
        this.prepay = prepay;
        return this;
    }

    /**
     * The extra principal an annual prepayment puts down, applied once every 12 months while
     * {@code prepay} is on.
     */
    public BigDecimal getPrepayAmount() {
        return prepayAmount;
    }

    /**
     * Sets the prepayment amount; returns {@code this}.
     */
    public Debt setPrepayAmount(BigDecimal prepayAmount) {
        this.prepayAmount = prepayAmount;
        return this;
    }

    /**
     * Currency code for the prepayment, or {@code null} to fall back to this debt's own
     * {@code currency}.
     */
    public String getPrepayCurrency() {
        return prepayCurrency;
    }

    /**
     * Sets the prepayment currency (nullable); returns {@code this}.
     */
    public Debt setPrepayCurrency(String prepayCurrency) {
        this.prepayCurrency = prepayCurrency;
        return this;
    }

    /**
     * This debt's scheduled rate changes, its {@link DebtRateStep} children. They take effect in
     * {@code afterYears} order, not {@code ordinal} order. Lazily initialized so it's never null.
     */
    public List<DebtRateStep> getRateSteps() {
        rateSteps = Objects.requireNonNullElseGet(rateSteps, ArrayList::new);
        return rateSteps;
    }

    /**
     * Two debts are equal when their UUID, audit timestamps, and scalar fields match; the rate steps
     * aren't compared.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final Debt that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(name, that.name) &&
               Objects.equals(principal, that.principal) &&
               Objects.equals(annualRate, that.annualRate) &&
               Objects.equals(monthly, that.monthly) &&
               Objects.equals(termMonths, that.termMonths) &&
               Objects.equals(repriceMode, that.repriceMode) &&
               Objects.equals(currency, that.currency) &&
               prepay == that.prepay &&
               Objects.equals(prepayAmount, that.prepayAmount) &&
               Objects.equals(prepayCurrency, that.prepayCurrency) &&
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
                name,
                principal,
                annualRate,
                monthly,
                termMonths,
                repriceMode,
                currency,
                prepay,
                prepayAmount,
                prepayCurrency,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * A debug string of this debt's own fields; excludes the parent month and rate steps.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("ordinal", ordinal)
                .add("name", name)
                .add("principal", principal)
                .add("annualRate", annualRate)
                .add("monthly", monthly)
                .add("termMonths", termMonths)
                .add("repriceMode", repriceMode)
                .add("currency", currency)
                .add("prepay", prepay)
                .add("prepayAmount", prepayAmount)
                .add("prepayCurrency", prepayCurrency)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
