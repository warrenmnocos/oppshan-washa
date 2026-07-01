package com.oppshan.washa.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@code FxRate}: the directed {@code baseCurrency} to {@code quoteCurrency}
 * pair. It's a JPA {@code @Embeddable} (hence the no-arg constructor and value-based {@code equals} /
 * {@code hashCode} JPA requires of a key class), so an FX rate is addressed by its currency pair rather
 * than a surrogate id.
 */
@Embeddable
public class FxRateId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "base_currency",
            nullable = false,
            length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency",
            nullable = false,
            length = 3)
    private String quoteCurrency;

    /**
     * No-arg constructor JPA requires of an embeddable key class.
     */
    public FxRateId() {
    }

    /**
     * Builds a key for the given directed base-to-quote currency pair.
     */
    public FxRateId(String baseCurrency,
                    String quoteCurrency) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }

    /**
     * The base currency: 1 unit of this is priced in the quote currency.
     */
    public String getBaseCurrency() {
        return baseCurrency;
    }

    /**
     * Sets the base currency.
     */
    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    /**
     * The quote currency: the price of 1 base unit is expressed in this.
     */
    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    /**
     * Sets the quote currency.
     */
    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    /**
     * Value equality over the {@code baseCurrency} and {@code quoteCurrency} pair, as a JPA key needs.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof final FxRateId that)) {
            return false;
        }
        return Objects.equals(baseCurrency, that.baseCurrency)
               && Objects.equals(quoteCurrency, that.quoteCurrency);
    }

    /**
     * Hashes the {@code baseCurrency} and {@code quoteCurrency} pair.
     */
    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency, quoteCurrency);
    }
}
