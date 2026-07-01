package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Create request for a salary preset: a name plus the salary payload (the salary dialog's working
 * draft, reusing the export-shaped {@link SalaryView}). Registered for reflection so the native
 * Lambda build keeps its accessors.
 */
@RegisterForReflection
public record SalaryPresetRequest(
        @NotEmpty String name,
        @Valid @NotNull SalaryView salary) {
}
