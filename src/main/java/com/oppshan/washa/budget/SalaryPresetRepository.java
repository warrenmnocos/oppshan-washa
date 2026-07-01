package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The {@link SalaryPreset} store: shared, standalone payroll templates with no tie to any budget
 * month. Four built-ins are seeded on startup and can't be deleted; users save and delete their own.
 * The store is shared across the household, so nothing here filters by an owning user. The finders
 * eager-load a preset's payroll graph, list presets in display order, and guard the idempotent
 * built-in seed.
 */
@Repository
public interface SalaryPresetRepository
        extends CrudRepository<SalaryPreset, UUID>, StatefulWriteRepository<SalaryPreset> {

    /**
     * Loads one preset with its components, deductions, and variables in a single round-trip so the
     * children are readable outside a lazy proxy. Brackets stay lazy (loaded per parent in the same
     * transaction); fetching three sibling bag collections at once would multiply the row count.
     */
    @Query("""
            SELECT DISTINCT p
            FROM SalaryPreset p
            LEFT JOIN FETCH p.components
            LEFT JOIN FETCH p.deductions
            LEFT JOIN FETCH p.variables
            WHERE p.uuid = :uuid""")
    Optional<SalaryPreset> findWithChildren(@NotNull UUID uuid);

    /** All presets, built-ins first, then alphabetical, so the seeded ones sort to the top. */
    @Query("""
            SELECT p
            FROM SalaryPreset p
            ORDER BY p.builtIn DESC, p.name ASC""")
    List<SalaryPreset> listOrdered();

    /** Whether a built-in preset with the given name already exists (the idempotent-seed guard). */
    @Query("""
            SELECT COUNT(p) > 0
            FROM SalaryPreset p
            WHERE p.name = :name AND p.builtIn = TRUE""")
    boolean existsBuiltInByName(@NotNull String name);
}
