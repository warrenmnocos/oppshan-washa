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

/**
 * One row of the shared household currency list: a currency {@code code} (the natural primary key), its
 * display {@code symbol}, how many {@code decimals} it shows, and an {@code ordinal} for display order.
 * There's a single global list, not one per month, so currency edits (add, remove, reorder, re-symbol)
 * persist independently of any {@code BudgetMonth}. {@code ordinal} 0 is the base currency every figure
 * reduces to. Unlike the UUID-keyed budget entities, this has a natural key and so extends
 * {@code AuditableEntity} directly.
 */
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

    /**
     * The three-letter currency code; this row's natural primary key.
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the currency code and returns {@code this}.
     */
    public CurrencySetting setCode(String code) {
        this.code = code;
        return this;
    }

    /**
     * Display order in the currency list; {@code ordinal} 0 marks the base currency every figure
     * reduces to.
     */
    public int getOrdinal() {
        return ordinal;
    }

    /**
     * Sets the ordinal and returns {@code this}.
     */
    public CurrencySetting setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    /**
     * The display symbol for this currency (e.g. ¥ or $).
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Sets the symbol and returns {@code this}.
     */
    public CurrencySetting setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    /**
     * How many fraction digits this currency shows. Defaults to 0 (e.g. JPY shows none).
     */
    public short getDecimals() {
        return decimals;
    }

    /**
     * Sets the fraction-digit count and returns {@code this}.
     */
    public CurrencySetting setDecimals(short decimals) {
        this.decimals = decimals;
        return this;
    }

    /**
     * Value equality over all identifying fields plus the audit triple ({@code code}, {@code createdAt},
     * {@code lastModifiedAt}).
     */
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

    /**
     * Hashes the same fields {@code equals} compares.
     */
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

    /**
     * Renders the identifying fields and audit triple for logging.
     */
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
