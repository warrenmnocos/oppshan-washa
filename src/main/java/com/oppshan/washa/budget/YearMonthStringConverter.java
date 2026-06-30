package com.oppshan.washa.budget;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

/**
 * Maps {@link YearMonth} to a {@code VARCHAR(7)} "YYYY-MM" column (Hibernate has no native YearMonth
 * type). {@code autoApply = true} applies it to every YearMonth attribute. The string form is
 * lexically sortable, so range/{@code <} comparisons on year_month stay chronologically correct.
 */
@Converter(autoApply = true)
public class YearMonthStringConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        return dbData == null ? null : YearMonth.parse(dbData);
    }
}
