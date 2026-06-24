package com.oppshan.washa.budget;

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
}
