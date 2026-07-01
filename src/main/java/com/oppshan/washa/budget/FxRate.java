package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
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

/**
 * A stored foreign-exchange rate for one directed currency pair: {@code rate} units of the quote
 * currency per 1 unit of the base, with {@code capturedAt} recording when it was fetched. The pair
 * itself is the composite primary key ({@code FxRateId} = base + quote), so there's one row per
 * direction. These rows are the persisted conversion factors for reducing amounts to a base currency.
 * Natural-keyed, so it extends {@code AuditableEntity} directly.
 */
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

    /**
     * This rate's composite key: the directed base-to-quote currency pair ({@code FxRateId}).
     */
    public FxRateId getId() {
        return id;
    }

    /**
     * Sets the composite key and returns {@code this}.
     */
    public FxRate setId(FxRateId id) {
        this.id = id;
        return this;
    }

    /**
     * Units of the quote currency per 1 unit of the base.
     */
    public BigDecimal getRate() {
        return rate;
    }

    /**
     * Sets the rate and returns {@code this}.
     */
    public FxRate setRate(BigDecimal rate) {
        this.rate = rate;
        return this;
    }

    /**
     * When this rate was fetched.
     */
    public Instant getCapturedAt() {
        return capturedAt;
    }

    /**
     * Sets the capture timestamp and returns {@code this}.
     */
    public FxRate setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
        return this;
    }

    /**
     * Value equality over {@code id}, {@code rate}, {@code capturedAt}, and the audit triple
     * ({@code createdAt}, {@code lastModifiedAt}).
     */
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

    /**
     * Hashes the same fields {@code equals} compares.
     */
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

    /**
     * Renders the key, rate, and audit triple for logging.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("rate", rate)
                .add("capturedAt", capturedAt)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
