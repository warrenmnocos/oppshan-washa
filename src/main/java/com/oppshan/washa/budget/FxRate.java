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
import java.util.Objects;

/** Snapshot of a live FX rate (units of quote per 1 base). */
@Entity
@Table(name = "fx_rate",
        schema = "washa")
public class FxRate extends AuditableEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @EmbeddedId
    @NotNull
    private FxRateId id;

    @Basic(optional = false)
    @Column(name = "rate",
            nullable = false)
    @NotNull
    private BigDecimal rate;

    @Basic(optional = false)
    @Column(name = "captured_at",
            nullable = false)
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final FxRate that)) {
            return false;
        }

        return Objects.equals(id, that.id) &&
               Objects.equals(rate, that.rate) &&
               Objects.equals(capturedAt, that.capturedAt) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                rate,
                capturedAt,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    @Override
    public String toString() {
        return "FxRate{" +
               "id=" + id +
               ", rate=" + rate +
               ", capturedAt=" + capturedAt +
               ", createdAt=" + getCreatedAt() +
               ", lastModifiedAt=" + getLastModifiedAt() +
               '}';
    }
}
