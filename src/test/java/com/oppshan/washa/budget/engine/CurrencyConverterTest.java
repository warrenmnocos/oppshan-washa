package com.oppshan.washa.budget.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyConverterTest {

    private final CurrencyConverter converter =
            new CurrencyConverter("JPY", Map.of("PHP", new BigDecimal("0.36")));

    @Test
    void shouldLeaveBaseCurrencyAmountUnchanged() {
        assertThat(converter.toBase(new BigDecimal("1000"), "JPY")).isEqualByComparingTo("1000");
    }

    @Test
    void shouldConvertNonBaseAmountByDividingByItsRate() {
        // PHP rate 0.36 means 360 PHP == 1000 JPY.
        assertThat(converter.toBase(new BigDecimal("360"), "PHP")).isEqualByComparingTo("1000");
    }

    @Test
    void shouldTreatUnknownOrNullCurrencyAsRateOne() {
        assertThat(converter.rateOf("USD")).isEqualByComparingTo("1");
        assertThat(converter.rateOf(null)).isEqualByComparingTo("1");
    }

    @Test
    void shouldComputeTitheAsTenPercentOfCombinedNet() {
        assertThat(TitheCalculator.tithe(new BigDecimal("1000000"))).isEqualByComparingTo("100000");
    }
}
