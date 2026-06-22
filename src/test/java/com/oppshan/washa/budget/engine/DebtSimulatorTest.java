package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.Debt;
import com.oppshan.washa.budget.DebtRateStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DebtSimulatorTest {

    private final DebtSimulator simulator = new DebtSimulator();

    private Debt mortgage() {
        return new Debt().setName("Home mortgage").setPrincipal(new BigDecimal("5000000"))
                .setAnnualRate(new BigDecimal("6.5")).setMonthly(new BigDecimal("38000"))
                .setTermMonths(240).setRepriceMode("payment").setCurrency("PHP");
    }

    @Test
    void shouldPayOffWithinCapAndAccrueInterest() {
        final var result = simulator.simulate(mortgage(), BigDecimal.ZERO);

        assertThat(result.amortizes()).isTrue();
        assertThat(result.months()).isBetween(1, 1200);
        assertThat(result.totalInterest().signum()).isPositive();
    }

    @Test
    void shouldReportNeverAmortizesWhenPaymentBelowInterest() {
        final var result = simulator.simulate(mortgage().setMonthly(new BigDecimal("100")), BigDecimal.ZERO);

        assertThat(result.amortizes()).isFalse();
        assertThat(result.months()).isEqualTo(SimulationResult.NEVER_AMORTIZES);
    }

    @Test
    void shouldShortenTermWithAnnualExtraPrepayment() {
        final var base = simulator.simulate(mortgage(), BigDecimal.ZERO);
        final var faster = simulator.simulate(mortgage(), new BigDecimal("300000"));

        assertThat(faster.months()).isLessThan(base.months());
    }

    @Test
    void shouldApplyRateStepFromTheStepMonthOnward() {
        final var debt = mortgage();
        debt.getRateSteps().add(new DebtRateStep().setDebt(debt).setOrdinal(0)
                .setAfterYears(new BigDecimal("3")).setRate(new BigDecimal("5.0")));

        // Before 3 years (month 12): base 6.5%/12. After (month 37 > 36): step 5.0%/12.
        assertThat(simulator.monthlyRate(debt, 12))
                .isEqualByComparingTo(new BigDecimal("6.5").divide(new BigDecimal("1200"), java.math.MathContext.DECIMAL128));
        assertThat(simulator.monthlyRate(debt, 37))
                .isEqualByComparingTo(new BigDecimal("5.0").divide(new BigDecimal("1200"), java.math.MathContext.DECIMAL128));
    }
}
