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
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * A scheduled rate change on a {@link Debt}: from {@code afterYears} into the loan, the annual rate
 * becomes {@code rate}. A fixed-then-floating mortgage is just a handful of these. Lazy,
 * cascade-owned child of {@code Debt}.
 *
 * <p>The steps sort by {@code afterYears}, and at each loan month the latest step whose
 * {@code afterYears * 12} is still below that month wins. So a step first bites in loan month
 * {@code floor(afterYears * 12) + 1}, and a later step overrides an earlier one. {@code afterYears}
 * is a {@code BigDecimal} so half-year steps (1.5, 2.5) work, and {@code ordinal} is only storage
 * order: it doesn't affect the schedule.
 */
@Entity
@Table(name = "debt_rate_step",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_debt_rate_step_debt_uuid",
                        columnList = "debt_uuid"
                ),
        })
public class DebtRateStep extends UuidEntity<DebtRateStep> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "debt_uuid",
            nullable = false
    )
    @NotNull
    private Debt debt;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "after_years",
            nullable = false)
    @NotNull
    private BigDecimal afterYears;

    @Basic(optional = false)
    @Column(name = "rate",
            nullable = false)
    @NotNull
    private BigDecimal rate;

    /**
     * The debt this rate change applies to.
     */
    public Debt getDebt() {
        return debt;
    }

    /**
     * Sets the owning debt; returns {@code this}.
     */
    public DebtRateStep setDebt(Debt debt) {
        this.debt = debt;
        return this;
    }

    /**
     * Storage and display order only. The schedule sorts by {@code afterYears}, so this doesn't
     * affect when the step takes effect.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the storage order; returns {@code this}.
     */
    public DebtRateStep setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * How many years into the loan this rate takes effect. It's a {@code BigDecimal}, so fractional
     * steps like {@code 1.5} are allowed.
     */
    public BigDecimal getAfterYears() {
        return afterYears;
    }

    /**
     * Sets the effective offset in years; returns {@code this}.
     */
    public DebtRateStep setAfterYears(BigDecimal afterYears) {
        this.afterYears = afterYears;
        return this;
    }

    /**
     * The annual rate that applies from this step onward, a percent like {@code Debt.annualRate}.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the new annual rate; returns {@code this}.
     */
    public DebtRateStep setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * Two steps are equal when their UUID, audit timestamps, and scalar fields match.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final DebtRateStep that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               ordinal == that.ordinal &&
               Objects.equals(afterYears, that.afterYears) &&
               Objects.equals(rate, that.rate) &&
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
                afterYears,
                rate,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * A debug string of this step's fields; excludes the parent debt.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("ordinal", ordinal)
                .add("afterYears", afterYears)
                .add("rate", rate)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
