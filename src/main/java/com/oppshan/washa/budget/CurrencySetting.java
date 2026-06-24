package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.AuditableEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serial;
import java.util.Objects;

/** Currency config (the mockup's {@code cur:[{code,sym}]}). ordinal 0 is the base currency. */
@Entity
@Table(name = "currency_setting",
        schema = "washa")
public class CurrencySetting extends AuditableEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @Column(name = "code",
            nullable = false,
            length = 3)
    @NotEmpty
    private String code;

    @Basic(optional = false)
    @Column(name = "ordinal",
            nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "symbol",
            nullable = false,
            length = 8)
    @NotEmpty
    private String symbol;

    @Basic(optional = false)
    @Column(name = "decimals",
            nullable = false)
    private short decimals = 0;

    public String getCode() {
        return code;
    }

    public CurrencySetting setCode(String code) {
        this.code = code;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public CurrencySetting setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getSymbol() {
        return symbol;
    }

    public CurrencySetting setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public short getDecimals() {
        return decimals;
    }

    public CurrencySetting setDecimals(short decimals) {
        this.decimals = decimals;
        return this;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final CurrencySetting that)) {
            return false;
        }

        return Objects.equals(code, that.code) &&
               ordinal == that.ordinal &&
               Objects.equals(symbol, that.symbol) &&
               decimals == that.decimals &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                code,
                ordinal,
                symbol,
                decimals,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("code", code)
                .add("ordinal", ordinal)
                .add("symbol", symbol)
                .add("decimals", decimals)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
