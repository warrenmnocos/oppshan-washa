package com.oppshan.washa.budget;

import com.oppshan.washa.common.AuditableEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serial;

/** Currency config (the mockup's {@code cur:[{code,sym}]}). ordinal 0 is the base currency. */
@Entity
@Table(name = "currency_setting", schema = "public")
public class CurrencySetting extends AuditableEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Basic(optional = false)
    @Column(name = "code", nullable = false, length = 3)
    @NotEmpty
    private String code;

    @Basic(optional = false)
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "symbol", nullable = false, length = 8)
    @NotEmpty
    private String symbol;

    @Basic(optional = false)
    @Column(name = "decimals", nullable = false)
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
}
