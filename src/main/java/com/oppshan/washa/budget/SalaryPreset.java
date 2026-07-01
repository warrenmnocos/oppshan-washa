package com.oppshan.washa.budget;

import com.google.common.base.MoreObjects;
import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A saved, reusable payroll template ("preset"). It mirrors the {@link Income} aggregate's shape
 * (components, deductions, variables, and their brackets) but drops the month link: an
 * {@code Income} is always owned by a {@code BudgetMonth}, whereas a preset stands on its own, so it
 * gets this parallel entity graph instead of reusing {@code Income}.
 *
 * <p>The store is shared across the household rather than per-user, so a preset has no owner. The
 * four built-ins ("Japan", "Japan No Resident Tax", "Philippines", and "blank") are seeded on
 * startup and can't be deleted; users save and delete their own, and {@code builtIn} tells the two
 * apart.
 */
@Entity
@Table(name = "salary_preset",
        schema = "washa",
        indexes = {
                @Index(
                        name = "idx_salary_preset_name",
                        columnList = "name"
                ),
        })
public class SalaryPreset extends UuidEntity<SalaryPreset> {

    @Serial
    private static final long serialVersionUID = 1L;

    @Basic(optional = false)
    @Column(name = "name",
            nullable = false)
    @NotEmpty
    private String name;

    @Basic(optional = false)
    @Column(name = "built_in",
            nullable = false)
    private boolean builtIn = false;

    @Basic(optional = false)
    @Column(name = "currency",
            nullable = false,
            length = 3)
    @NotEmpty
    private String currency;

    @Basic(optional = false)
    @Column(name = "engine",
            nullable = false,
            length = 64)
    @NotEmpty
    private String engine = "generic";

    @OneToMany(
            mappedBy = "salaryPreset",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SalaryPresetComponent> components;

    @OneToMany(
            mappedBy = "salaryPreset",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SalaryPresetDeduction> deductions;

    @OneToMany(
            mappedBy = "salaryPreset",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<SalaryPresetVariable> variables;

    /**
     * The preset's display name (e.g. "Japan").
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name; returns {@code this}.
     */
    public SalaryPreset setName(String name) {
        this.name = name;
        return this;
    }

    /**
     * Whether this is a seeded built-in preset. Built-ins can't be deleted; user-saved presets
     * ({@code false}) can.
     */
    public boolean isBuiltIn() {
        return builtIn;
    }

    /**
     * Sets the built-in flag; returns {@code this}.
     */
    public SalaryPreset setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
        return this;
    }

    /**
     * Three-letter currency code the preset's amounts are in.
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Sets the currency code; returns {@code this}.
     */
    public SalaryPreset setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * The payroll engine key, {@code generic} by default. Mirrors {@code Income.engine}.
     */
    public String getEngine() {
        return engine;
    }

    /**
     * Sets the engine key; returns {@code this}.
     */
    public SalaryPreset setEngine(String engine) {
        this.engine = engine;
        return this;
    }

    /**
     * The preset's earnings lines, its {@link SalaryPresetComponent} children. Lazily initialized so
     * it's never null.
     */
    public List<SalaryPresetComponent> getComponents() {
        components = Objects.requireNonNullElseGet(components, ArrayList::new);
        return components;
    }

    /**
     * The preset's deduction lines, its {@link SalaryPresetDeduction} children. Lazily initialized so
     * it's never null.
     */
    public List<SalaryPresetDeduction> getDeductions() {
        deductions = Objects.requireNonNullElseGet(deductions, ArrayList::new);
        return deductions;
    }

    /**
     * The preset's intermediate variables, its {@link SalaryPresetVariable} children. Lazily
     * initialized so it's never null.
     */
    public List<SalaryPresetVariable> getVariables() {
        variables = Objects.requireNonNullElseGet(variables, ArrayList::new);
        return variables;
    }

    /**
     * Two presets are equal when their UUID, audit timestamps, and scalar fields match; the child
     * collections aren't compared.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof final SalaryPreset that)) {
            return false;
        }

        return Objects.equals(getUuid(), that.getUuid()) &&
               Objects.equals(name, that.name) &&
               builtIn == that.builtIn &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(engine, that.engine) &&
               Objects.equals(getCreatedAt(), that.getCreatedAt()) &&
               Objects.equals(getLastModifiedAt(), that.getLastModifiedAt());
    }

    /**
     * Hashes the same fields {@link #equals(Object)} compares.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                getUuid(),
                name,
                builtIn,
                currency,
                engine,
                getCreatedAt(),
                getLastModifiedAt()
        );
    }

    /**
     * A debug string of the preset's scalar fields; excludes the child collections.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("uuid", getUuid())
                .add("name", name)
                .add("builtIn", builtIn)
                .add("currency", currency)
                .add("engine", engine)
                .add("createdAt", getCreatedAt())
                .add("lastModifiedAt", getLastModifiedAt())
                .toString();
    }
}
