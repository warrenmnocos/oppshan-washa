package com.oppshan.washa.budget;

import com.oppshan.washa.common.StatefulWriteRepository;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SalaryPresetRepository
        extends CrudRepository<SalaryPreset, UUID>, StatefulWriteRepository<SalaryPreset> {

    /**
     * Loads one preset with its components, deductions, and variables in a single round-trip so the
     * mapper can read the children outside a lazy proxy. Brackets stay lazy (loaded per parent in the
     * same transaction); fetching three sibling bag collections at once would multiply the row count.
     */
    @Query("""
            SELECT DISTINCT p
            FROM SalaryPreset p
            LEFT JOIN FETCH p.components
            LEFT JOIN FETCH p.deductions
            LEFT JOIN FETCH p.variables
            WHERE p.uuid = :uuid""")
    Optional<SalaryPreset> findWithChildren(@NotNull UUID uuid);

    /** All presets, built-ins first (then alphabetical), so the dialog lists the seeded ones on top. */
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
