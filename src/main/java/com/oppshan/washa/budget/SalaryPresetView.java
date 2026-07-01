package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.UUID;

/**
 * API DTO for one saved salary preset. The payload reuses the export-shaped {@link SalaryView}, so a
 * preset carries the identical structure a month's income does. {@code builtIn} marks the four seeded
 * presets, which can't be deleted. Registered for reflection so the native Lambda build keeps its
 * accessors.
 */
@RegisterForReflection
public record SalaryPresetView(
        UUID uuid,
        String name,
        boolean builtIn,
        SalaryView salary) {
}
