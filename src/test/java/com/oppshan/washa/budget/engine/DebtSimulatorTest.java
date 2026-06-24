package com.oppshan.washa.budget.engine;

import com.oppshan.washa.budget.Debt;
import com.oppshan.washa.budget.DebtRateStep;
import com.oppshan.washa.budget.DebtRepriceMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class DebtSimulatorTest {

    private final DebtSimulator simulator = new DebtSimulator();

    private Debt mortgage() {
        return new Debt()
                .setName("Home mortgage")
                .setPrincipal(new BigDecimal("5000000"))
                .setAnnualRate(new BigDecimal("6.5"))
                .setMonthly(new BigDecimal("38000"))
                .setTermMonths(240)
                .setRepriceMode(DebtRepriceMode.PAYMENT)
                .setCurrency("PHP");
    }

    @Test
    void shouldPayOffWithinCapAndAccrueInterest() {
        final var result = simulator.simulate(mortgage(), BigDecimal.ZERO);

        assertThat(result.amortizes(), is(true));
        assertThat(result.months(), allOf(greaterThanOrEqualTo(1), lessThanOrEqualTo(1200)));
        assertThat(result.totalInterest().signum(), greaterThan(0));
    }

    @Test
    void shouldReportNeverAmortizesWhenPaymentBelowInterest() {
        final var result = simulator.simulate(mortgage().setMonthly(new BigDecimal("100")), BigDecimal.ZERO);

        assertThat(result.amortizes(), is(false));
        assertThat(result.months(), is(SimulationResult.NEVER_AMORTIZES));
    }

    @Test
    void shouldShortenTermWithAnnualExtraPrepayment() {
        final var base = simulator.simulate(mortgage(), BigDecimal.ZERO);
        final var faster = simulator.simulate(mortgage(), new BigDecimal("300000"));

        assertThat(faster.months(), lessThan(base.months()));
    }

    @Test
    void shouldApplyRateStepFromTheStepMonthOnward() {
        final var debt = mortgage();
        debt.getRateSteps().add(new DebtRateStep().setDebt(debt).setOrdinal(0)
                .setAfterYears(new BigDecimal("3")).setRate(new BigDecimal("5.0")));

        // Before 3 years (month 12): base 6.5%/12. After (month 37 > 36): step 5.0%/12.
        assertThat(simulator.monthlyRate(debt, 12),
                comparesEqualTo(new BigDecimal("6.5").divide(new BigDecimal("1200"), java.math.MathContext.DECIMAL128)));
        assertThat(simulator.monthlyRate(debt, 37),
                comparesEqualTo(new BigDecimal("5.0").divide(new BigDecimal("1200"), java.math.MathContext.DECIMAL128)));
    }
}
