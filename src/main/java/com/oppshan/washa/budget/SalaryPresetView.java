package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.SalaryView;

import java.util.UUID;

/**
 * API DTO for one saved salary preset. The payload reuses the export-shaped {@link SalaryView} (the
 * same record the salary dialog already loads), so selecting a preset feeds the dialog the identical
 * structure a month's income carries. {@code builtIn} marks the four seeded presets (which cannot be
 * deleted). Registered for reflection so the native Lambda build keeps its accessors.
 */
public record SalaryPresetView(
        UUID uuid,
        String name,
        boolean builtIn,
        SalaryView salary) {
}
