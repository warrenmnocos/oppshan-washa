package com.oppshan.washa.budget;

import com.oppshan.washa.common.AuditableEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot of a live FX rate (units of quote per 1 base). */
@Entity
@Table(name = "fx_rate", schema = "oppshan")
public class FxRate extends AuditableEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @EmbeddedId
    @NotNull
    private FxRateId id;

    @Basic(optional = false)
    @Column(name = "rate", nullable = false)
    @NotNull
    private BigDecimal rate;

    @Basic(optional = false)
    @Column(name = "captured_at", nullable = false)
    @NotNull
    private Instant capturedAt;

    public FxRateId getId() {
        return id;
    }

    public FxRate setId(FxRateId id) {
        this.id = id;
        return this;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public FxRate setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public FxRate setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
        return this;
    }
}
