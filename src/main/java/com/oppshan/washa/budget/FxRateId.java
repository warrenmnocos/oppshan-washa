package com.oppshan.washa.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FxRateId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    public FxRateId() {
    }

    public FxRateId(String baseCurrency, String quoteCurrency) {
        this.baseCurrency = baseCurrency;
        this.quoteCurrency = quoteCurrency;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

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

    @Override
    public int hashCode() {
        return Objects.hash(baseCurrency, quoteCurrency);
    }
}
