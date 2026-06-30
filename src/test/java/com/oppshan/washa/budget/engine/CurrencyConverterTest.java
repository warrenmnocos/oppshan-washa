package com.oppshan.washa.budget.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class CurrencyConverterTest {

    private final CurrencyConverter converter =
            new CurrencyConverter("JPY", Map.of("PHP", new BigDecimal("0.36")));

    @Test
    void shouldLeaveBaseCurrencyAmountUnchanged() {
        assertThat(converter.toBase(new BigDecimal("1000"), "JPY"), is(comparesEqualTo(new BigDecimal("1000"))));
    }

    @Test
    void shouldConvertNonBaseAmountByDividingByItsRate() {
        // PHP rate 0.36 means 360 PHP == 1000 JPY.
        assertThat(converter.toBase(new BigDecimal("360"), "PHP"), is(comparesEqualTo(new BigDecimal("1000"))));
    }

    @Test
    void shouldTreatUnknownOrNullCurrencyAsRateOne() {
        assertThat(converter.rateOf("USD"), is(comparesEqualTo(new BigDecimal("1"))));
        assertThat(converter.rateOf(null), is(comparesEqualTo(new BigDecimal("1"))));
    }

    @Test
    void shouldComputeTitheAsTenPercentOfCombinedNet() {
        assertThat(TitheCalculator.tithe(new BigDecimal("1000000")), is(comparesEqualTo(new BigDecimal("100000"))));
    }
}
