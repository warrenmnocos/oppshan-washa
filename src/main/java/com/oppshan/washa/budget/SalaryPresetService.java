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
 * Salary-preset CRUD over the shared, persisted preset store. Presets are reusable payroll templates:
 * the four built-ins are seeded on startup and can't be deleted, while users may save and delete their
 * own. Reads and writes go through the repository, and it returns {@link SalaryPresetView}s, never
 * entities.
 */
@Transactional
@ApplicationScoped
public class SalaryPresetService {

    private final SalaryPresetRepository salaryPresetRepository;
    private final SalaryPresetMapper salaryPresetMapper;

    /** Injects the preset repository and the mapper between preset entities and views. */
    @Inject
    public SalaryPresetService(SalaryPresetRepository salaryPresetRepository,
                               SalaryPresetMapper salaryPresetMapper) {
        this.salaryPresetRepository = salaryPresetRepository;
        this.salaryPresetMapper = salaryPresetMapper;
    }

    /**
     * Every preset, built-ins first then alphabetical. Attaching each row hands back a managed copy so
     * its lazy children load inside the transaction.
     */
    @NotNull
    public List<@Valid SalaryPresetView> list() {
        return salaryPresetRepository.listOrdered().stream()
                .map(salaryPresetRepository::attachWithSession)
                .map(salaryPresetMapper::toView)
                .toList();
    }

    /**
     * Saves a new user preset from the salary payload and returns its view. Always a user preset: the
     * built-in flag is forced false here, so this path can't mint a seeded built-in.
     */
    @Valid
    @NotNull
    public SalaryPresetView create(@NotEmpty String name,
                                   @Valid @NotNull SalaryView salary) {
        final var preset = salaryPresetMapper.toEntity(name, false, salary);
        salaryPresetRepository.insertWithSession(preset);
        return salaryPresetMapper.toView(preset);
    }

    /**
     * Deletes a user preset by id. Throws {@code BusinessException.salaryPresetNotFound()} when no
     * preset has that id, and {@code BusinessException.salaryPresetBuiltIn()} when it's a seeded
     * built-in (those can't be deleted).
     */
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
