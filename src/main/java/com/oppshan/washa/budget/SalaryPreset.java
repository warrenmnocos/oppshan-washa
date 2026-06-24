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
 * A saved payroll template a user can load into the salary dialog. The four built-ins (jp, jp0, ph,
 * blank) are seeded on startup; users may save and delete their own. Mirrors the {@link Income}
 * aggregate's payroll shape (components, deductions, variables, brackets) without the month
 * relationship — a preset is standalone, not tied to a budget month.
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

    public String getName() {
        return name;
    }

    public SalaryPreset setName(String name) {
        this.name = name;
        return this;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public SalaryPreset setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public SalaryPreset setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public String getEngine() {
        return engine;
    }

    public SalaryPreset setEngine(String engine) {
        this.engine = engine;
        return this;
    }

    public List<SalaryPresetComponent> getComponents() {
        components = Objects.requireNonNullElseGet(components, ArrayList::new);
        return components;
    }

    public List<SalaryPresetDeduction> getDeductions() {
        deductions = Objects.requireNonNullElseGet(deductions, ArrayList::new);
        return deductions;
    }

    public List<SalaryPresetVariable> getVariables() {
        variables = Objects.requireNonNullElseGet(variables, ArrayList::new);
        return variables;
    }

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
