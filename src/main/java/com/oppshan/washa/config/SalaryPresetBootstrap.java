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
 * Seeds the four built-in salary presets (jp, jp0, ph, blank) on startup. Idempotent — a built-in of
 * a given name is inserted only when absent, so re-running adds nothing and a user-saved preset never
 * collides. These translate {@code salary-presets.ts} to Java verbatim: the Japanese income/resident
 * tax formulas, the capped social-insurance percentages, and the Philippine withholding-tax bracket
 * schedule are published tax rules, not the household's figures.
 *
 * <p>Writes go through {@code insertWithSession} (managed-session persist so the {@code @UuidGenerator}
 * and cascades run) — mirrors {@link IdentityBootstrap}; no direct {@code EntityManager} use.
 */
@ApplicationScoped
public class SalaryPresetBootstrap {

    // Japan: pension / health / employment insurance are capped percentages of gross; income and
    // resident tax are the graduated national formulas (employment-income deduction, ¥1,000-floored
    // taxable, the 2.1% reconstruction surtax on income tax, and the flat 10% + ¥5,000 resident levy),
    // evaluated monthly from gross and annual (= gross × 12).
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

    @Inject
    public SalaryPresetBootstrap(SalaryPresetRepository salaryPresetRepository,
                                 SalaryPresetMapper salaryPresetMapper) {
        this.salaryPresetRepository = salaryPresetRepository;
        this.salaryPresetMapper = salaryPresetMapper;
    }

    void onStart(@Observes StartupEvent event) {
        seed();
    }

    @Transactional
    public void seed() {
        seedPreset("jp", japanSalary(true));
        seedPreset("jp0", japanSalary(false));
        seedPreset("ph", philippinesSalary());
        seedPreset("blank", blankSalary());
    }

    private void seedPreset(String name, SalaryView salary) {
        if (salaryPresetRepository.existsBuiltInByName(name)) {
            return;
        }

        salaryPresetRepository.insertWithSession(salaryPresetMapper.toEntity(name, true, salary));
    }

    // ---------- preset content (translated from salary-presets.ts) ----------

    private static SalaryView japanSalary(boolean withResidentTax) {
        return new SalaryView("jp", "JPY", "generic",
                List.of(basicSalaryComponent()), japanDeductions(withResidentTax), List.of());
    }

    private static SalaryView philippinesSalary() {
        return new SalaryView("ph", "PHP", "generic",
                List.of(basicSalaryComponent()), philippinesDeductions(), List.of());
    }

    private static SalaryView blankSalary() {
        return new SalaryView("blank", "JPY", "generic",
                List.of(basicSalaryComponent()), List.of(), List.of());
    }

    private static ComponentView basicSalaryComponent() {
        return new ComponentView("Basic salary", BigDecimal.ZERO, true, true, null, false);
    }

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

    private static DeductionView pctDeduction(String label, BigDecimal rate, BigDecimal cap) {
        return new DeductionView(label, DeductionType.PCT, DeductionBase.GROSS, null, rate, cap, null,
                BigDecimal.ZERO, null, null, true, null, false, List.of());
    }

    private static DeductionView pctDeductionWithFloor(String label,
                                                       BigDecimal rate,
                                                       BigDecimal cap,
                                                       BigDecimal floor) {
        return new DeductionView(label, DeductionType.PCT, DeductionBase.BASIC, null, rate, cap, floor,
                BigDecimal.ZERO, null, null, true, null, false, List.of());
    }

    private static DeductionView formulaDeduction(String label, String expr) {
        return new DeductionView(label, DeductionType.FORMULA, null, null, null, null, null,
                BigDecimal.ZERO, expr, null, false, null, false, List.of());
    }

    private static BracketView withholdingBracket(String threshold, String expr) {
        return new BracketView("taxable", BracketOp.GT, new BigDecimal(threshold), BracketType.FORMULA, null, expr);
    }
}
