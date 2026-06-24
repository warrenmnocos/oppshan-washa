package com.oppshan.washa.budget;

import com.oppshan.washa.common.UuidEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "income",
        schema = "washa",
        indexes = {
                @Index(name = "idx_income_budget_month_uuid", columnList = "budget_month_uuid"),
        })
public class Income extends UuidEntity<Income> {

    @Serial
    private static final long serialVersionUID = 1L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "budget_month_uuid", nullable = false)
    @NotNull
    private BudgetMonth budgetMonth;

    @Basic(optional = false)
    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Basic(optional = false)
    @Column(name = "name", nullable = false)
    @NotEmpty
    private String name;

    @Basic(optional = false)
    @Column(name = "currency", nullable = false, length = 3)
    @NotEmpty
    private String currency;

    @Basic(optional = false)
    @Column(name = "engine", nullable = false, length = 64)
    @NotEmpty
    private String engine = "generic";

    @OneToMany(mappedBy = "income", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IncomeComponent> components;

    @OneToMany(mappedBy = "income", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IncomeDeduction> deductions;

    @OneToMany(mappedBy = "income", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IncomeVariable> variables;

    public BudgetMonth getBudgetMonth() {
        return budgetMonth;
    }

    public Income setBudgetMonth(BudgetMonth budgetMonth) {
        this.budgetMonth = budgetMonth;
        return this;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public Income setOrdinal(int ordinal) {
        this.ordinal = ordinal;
        return this;
    }

    public String getName() {
        return name;
    }

    public Income setName(String name) {
        this.name = name;
        return this;
    }

    public String getCurrency() {
        return currency;
    }

    public Income setCurrency(String currency) {
        this.currency = currency;
        return this;
    }

    public String getEngine() {
        return engine;
    }

    public Income setEngine(String engine) {
        this.engine = engine;
        return this;
    }

    public List<IncomeComponent> getComponents() {
        components = Objects.requireNonNullElseGet(components, ArrayList::new);
        return components;
    }

    public List<IncomeDeduction> getDeductions() {
        deductions = Objects.requireNonNullElseGet(deductions, ArrayList::new);
        return deductions;
    }

    public List<IncomeVariable> getVariables() {
        variables = Objects.requireNonNullElseGet(variables, ArrayList::new);
        return variables;
    }
}
