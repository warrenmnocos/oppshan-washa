package com.oppshan.washa.budget;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Upsert request for one FX rate: the base/quote currency pair and the new rate (units of quote per
 * one base). Registered for reflection so the native Lambda build keeps its accessors.
 */
public record FxRateRequest(
        @NotEmpty String base,
        @NotEmpty String quote,
        @NotNull @Positive BigDecimal rate) {
}
