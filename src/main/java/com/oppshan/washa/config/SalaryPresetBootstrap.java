package com.oppshan.washa.config;

import com.oppshan.washa.budget.BracketOp;
import com.oppshan.washa.budget.BracketType;
import com.oppshan.washa.budget.BudgetMonthView.BracketView;
import com.oppshan.washa.budget.BudgetMonthView.ComponentView;
import com.oppshan.washa.budget.BudgetMonthView.DeductionView;
import com.oppshan.washa.budget.BudgetMonthView.SalaryView;
import com.oppshan.washa.budget.DeductionBase;
import com.oppshan.washa.budget.DeductionType;
import com.oppshan.washa.budget.SalaryPresetMapper;
import com.oppshan.washa.budget.SalaryPresetRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds the four built-in salary presets on startup, named "Japan", "Japan No Resident Tax",
 * "Philippines", and a "blank" starter. These mirror the prototype's payroll regimes
 * (tokyo_budget_tool.html: its {@code jp}, {@code jp0}, {@code ph}, and {@code blank} presets),
 * translated to Java. The Japanese income/resident tax formulas, the social-insurance percentages,
 * and the Philippine withholding-tax bracket schedule are published tax rules, not the household's
 * own figures, so hardcoding them here is fine.
 *
 * <p>Idempotent: a built-in of a given name is inserted only when absent. The guard is scoped to
 * built-ins, so a user-saved preset of the same name neither blocks the seed nor gets overwritten.
 * Writes go through {@code insertWithSession} (a managed-session persist so the
 * {@code @UuidGenerator} and the child cascades run). No direct {@code EntityManager} use.
 */
@ApplicationScoped
public class SalaryPresetBootstrap {

    /**
     * Japan payroll, written as monthly formulas over the {@code gross} and {@code annual}
     * (= gross × 12) variables. Social insurance: pension and health are capped percentages of gross,
     * employment insurance an uncapped one. Income and resident tax are the graduated national
     * formulas: the employment-income deduction, the ¥1,000-floored taxable, the 2.1% reconstruction
     * surtax on income tax, and the flat 10% + ¥5,000 resident levy.
     */
    private static final String JP_INCOME_TAX_EXPR =
            """
            si = round(min(0.0915*gross, 59475)) + round(min(0.0495*gross, 68805)) + round(0.0055*gross)
            emp = max(550000, min(0.4*annual-100000, 0.3*annual+80000, 0.2*annual+440000, 0.1*annual+1100000, 1950000))
            ti = max(0, floor((annual - emp - 580000 - si*12)/1000)*1000)
            tax = max(0.05*ti, 0.1*ti-97500, 0.2*ti-427500, 0.23*ti-636000, 0.33*ti-1536000, 0.4*ti-2796000, 0.45*ti-4796000)
            round(tax * 1.021 / 12)""";

    private static final String JP_RESIDENT_TAX_EXPR =
            """
            si = round(min(0.0915*gross, 59475)) + round(min(0.0495*gross, 68805)) + round(0.0055*gross)
            emp = max(550000, min(0.4*annual-100000, 0.3*annual+80000, 0.2*annual+440000, 0.1*annual+1100000, 1950000))
            tr = max(0, floor((annual - emp - 430000 - si*12)/1000)*1000)
            round((tr*0.10 + 5000)/12)""";

    private final SalaryPresetRepository salaryPresetRepository;
    private final SalaryPresetMapper salaryPresetMapper;

    /** Injects the salary-preset repository and the view-to-entity mapper the seed writes through. */
    @Inject
    public SalaryPresetBootstrap(SalaryPresetRepository salaryPresetRepository,
                                 SalaryPresetMapper salaryPresetMapper) {
        this.salaryPresetRepository = salaryPresetRepository;
        this.salaryPresetMapper = salaryPresetMapper;
    }

    /** Runs the seed once the container is up (CDI startup observer). */
    void onStart(@Observes StartupEvent event) {
        seed();
    }

    /** Seeds all four built-in presets; each insert is guarded, so a re-run is a no-op. */
    @Transactional
    public void seed() {
        seedPreset("Japan", japanSalary(true));
        seedPreset("Japan No Resident Tax", japanSalary(false));
        seedPreset("Philippines", philippinesSalary());
        seedPreset("blank", blankSalary());
    }

    /** Inserts the built-in only if none of that name exists yet; the idempotent-seed guard. */
    private void seedPreset(String name,
                            SalaryView salary) {
        if (salaryPresetRepository.existsBuiltInByName(name)) {
            return;
        }

        salaryPresetRepository.insertWithSession(salaryPresetMapper.toEntity(name, true, salary));
    }

    /**
     * The Japan preset: JPY with the basic-salary line and the Japanese deductions (resident tax
     * included when {@code withResidentTax}).
     */
    private static SalaryView japanSalary(boolean withResidentTax) {
        return new SalaryView("jp", "JPY", "generic",
                List.of(basicSalaryComponent()), japanDeductions(withResidentTax), List.of());
    }

    /** The Philippines preset: PHP with the basic-salary line and the PH deductions. */
    private static SalaryView philippinesSalary() {
        return new SalaryView("ph", "PHP", "generic",
                List.of(basicSalaryComponent()), philippinesDeductions(), List.of());
    }

    /** The blank starter preset: JPY with just the basic-salary line and no deductions. */
    private static SalaryView blankSalary() {
        return new SalaryView("blank", "JPY", "generic",
                List.of(basicSalaryComponent()), List.of(), List.of());
    }

    /**
     * The one earnings line every preset ships with: a zero {@code Basic salary} placeholder, flagged
     * basic and taxable so it counts toward the taxable base.
     */
    private static ComponentView basicSalaryComponent() {
        return new ComponentView("Basic salary", BigDecimal.ZERO, true, true, null, false);
    }

    /**
     * The Japanese deductions: capped pension and health plus uncapped employment insurance (all
     * percentages of gross), income tax, and resident tax when {@code withResidentTax} (both taxes
     * formula-based).
     */
    private static List<DeductionView> japanDeductions(boolean withResidentTax) {
        final var pension = pctDeduction("Employees' pension", new BigDecimal("9.15"), new BigDecimal("59475"));
        final var health = pctDeduction("Health insurance", new BigDecimal("4.95"), new BigDecimal("68805"));
        final var employment = pctDeduction("Employment insurance", new BigDecimal("0.55"), null);
        final var incomeTax = formulaDeduction("Income tax", JP_INCOME_TAX_EXPR);
        final var residentTax = formulaDeduction("Resident tax", JP_RESIDENT_TAX_EXPR);

        return withResidentTax
                ? List.of(pension, health, employment, incomeTax, residentTax)
                : List.of(pension, health, employment, incomeTax);
    }

    /**
     * The Philippine deductions: SSS, PhilHealth, and Pag-IBIG (capped, sometimes floored percentages
     * of basic salary) plus the bracketed withholding tax.
     */
    private static List<DeductionView> philippinesDeductions() {
        final var sss = pctDeductionWithFloor("SSS", new BigDecimal("5"), new BigDecimal("1750"), new BigDecimal("250"));
        final var philHealth = pctDeductionWithFloor("PhilHealth", new BigDecimal("2.5"), new BigDecimal("2500"), new BigDecimal("250"));
        final var pagIbig = pctDeductionWithFloor("Pag-IBIG", new BigDecimal("2"), new BigDecimal("200"), null);
        final var withholding = new DeductionView("Withholding tax", DeductionType.BRACKETS,
                DeductionBase.TAXABLE, null, null, null, null, BigDecimal.ZERO, null, null, false, null, false,
                List.of(
                        withholdingBracket("20833.33", "0.15*(taxable-20833.33)"),
                        withholdingBracket("33333.33", "0.05*(taxable-33333.33)"),
                        withholdingBracket("66666.67", "0.05*(taxable-66666.67)"),
                        withholdingBracket("166666.67", "0.05*(taxable-166666.67)"),
                        withholdingBracket("666666.67", "0.05*(taxable-666666.67)")));

        return List.of(sss, philHealth, pagIbig, withholding);
    }

    /** JP-style contribution: a percentage of GROSS with an optional cap, no floor. */
    private static DeductionView pctDeduction(String label,
                                              BigDecimal rate,
                                              BigDecimal cap) {
        return new DeductionView(label, DeductionType.PCT, DeductionBase.GROSS, null, rate, cap, null,
                BigDecimal.ZERO, null, null, true, null, false, List.of());
    }

    /** PH-style contribution: a percentage of BASIC salary with a cap and (sometimes) a floor. */
    private static DeductionView pctDeductionWithFloor(String label,
                                                       BigDecimal rate,
                                                       BigDecimal cap,
                                                       BigDecimal floor) {
        return new DeductionView(label, DeductionType.PCT, DeductionBase.BASIC, null, rate, cap, floor,
                BigDecimal.ZERO, null, null, true, null, false, List.of());
    }

    /** A deduction computed by a formula expression (income / resident tax), not a flat percentage. */
    private static DeductionView formulaDeduction(String label,
                                                  String expr) {
        return new DeductionView(label, DeductionType.FORMULA, null, null, null, null, null,
                BigDecimal.ZERO, expr, null, false, null, false, List.of());
    }

    /** One PH withholding-tax bracket: its formula applies once {@code taxable} climbs past the threshold. */
    private static BracketView withholdingBracket(String threshold,
                                                  String expr) {
        return new BracketView("taxable", BracketOp.GT, new BigDecimal(threshold), BracketType.FORMULA, null, expr);
    }
}
