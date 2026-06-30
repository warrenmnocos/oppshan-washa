package com.oppshan.washa.budget;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

/** The JPA converter that stores a YearMonth as the "YYYY-MM" string column. */
class YearMonthStringConverterTest {

    private final YearMonthStringConverter converter = new YearMonthStringConverter();

    @Test
    void shouldConvertBothWaysAndTolerateNull() {
        assertThat(converter.convertToDatabaseColumn(YearMonth.of(2026, 6)), is("2026-06"));
        assertThat(converter.convertToDatabaseColumn(null), is(nullValue()));
        assertThat(converter.convertToEntityAttribute("2026-06"), is(YearMonth.of(2026, 6)));
        assertThat(converter.convertToEntityAttribute(null), is(nullValue()));
    }
}
