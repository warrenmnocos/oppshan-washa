package com.oppshan.washa.budget;

import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import com.oppshan.washa.exception.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Salary-preset CRUD over the shared, persisted preset store. Presets are payroll templates the
 * salary dialog loads; the four built-ins are seeded on startup and cannot be deleted, while users
 * may save and delete their own. Reads/writes go through the repository; the service returns
 * {@link SalaryPresetView}s, never entities.
 */
@Transactional
@ApplicationScoped
public class SalaryPresetService {

    private final SalaryPresetRepository salaryPresetRepository;
    private final SalaryPresetMapper salaryPresetMapper;

    @Inject
    public SalaryPresetService(SalaryPresetRepository salaryPresetRepository,
                               SalaryPresetMapper salaryPresetMapper) {
        this.salaryPresetRepository = salaryPresetRepository;
        this.salaryPresetMapper = salaryPresetMapper;
    }

    /** Every preset, built-ins first then alphabetical (the order the dialog lists them in). */
    @NotNull
    public List<@Valid SalaryPresetView> list() {
        return salaryPresetRepository.listOrdered().stream()
                .map(salaryPresetRepository::attachWithSession) // managed copy: lazy children load in-tx
                .map(salaryPresetMapper::toView)
                .toList();
    }

    /** Saves a new user preset (never built-in) from the salary payload. */
    @Valid
    @NotNull
    public SalaryPresetView create(@NotEmpty String name,
                                   @Valid @NotNull SalaryView salary) {
        final var preset = salaryPresetMapper.toEntity(name, false, salary);
        salaryPresetRepository.insertWithSession(preset);
        return salaryPresetMapper.toView(preset);
    }

    /** Deletes a user preset; rejects a built-in preset and a missing one. */
    public void delete(@NotNull UUID uuid) {
        final var preset = salaryPresetRepository.findById(uuid)
                .orElseThrow(BusinessException::salaryPresetNotFound);

        if (preset.isBuiltIn()) {
            throw BusinessException.salaryPresetBuiltIn();
        }

        salaryPresetRepository.findById(uuid)
                .map(salaryPresetRepository::attachWithSession)
                .ifPresent(salaryPresetRepository::deleteWithSession);
    }
}
