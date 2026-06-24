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

/** Variable-rate step: from loan month {@code afterYears * 12 + 1}, the rate becomes {@code rate}. */
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

    public Debt getDebt() {
        return debt;
    }

    public DebtRateStep setDebt(Debt debt) {
        this.debt = debt;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public DebtRateStep setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public BigDecimal getAfterYears() {
        return afterYears;
    }

    public DebtRateStep setAfterYears(BigDecimal afterYears) {
        this.afterYears = afterYears;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public DebtRateStep setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

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
