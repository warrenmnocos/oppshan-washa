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

    private Debt risingRateMortgage(DebtRepriceMode mode) {
        final var debt = mortgage().setRepriceMode(mode);
        debt.getRateSteps().add(new DebtRateStep().setDebt(debt).setOrdinal(0)
                .setAfterYears(new BigDecimal("3")).setRate(new BigDecimal("9.0")));
        return debt;
    }

    @Test
    void shouldDivergeBetweenRepriceModesWhenTheRateRises() {
        final var reAmortize = simulator.simulate(risingRateMortgage(DebtRepriceMode.PAYMENT), BigDecimal.ZERO);
        final var extendTerm = simulator.simulate(risingRateMortgage(DebtRepriceMode.TERM), BigDecimal.ZERO);

        assertThat(reAmortize.amortizes(), is(true));
        assertThat(extendTerm.amortizes(), is(true));

        // Re-amortize keeps the original 240-month term in sight; extend-term lets the term run longer.
        assertThat(reAmortize.months(), lessThan(extendTerm.months()));
        assertThat(reAmortize.months(), allOf(greaterThanOrEqualTo(220), lessThanOrEqualTo(260)));

        // The re-amortized monthly steps up past the original 38000 to hold the term; extend-term keeps
        // paying 38000, so the rate hike pushes its payoff out and it accrues more total interest overall.
        assertThat(reAmortize.finalPayment(), greaterThan(extendTerm.finalPayment()));
        assertThat(extendTerm.totalInterest(), greaterThan(reAmortize.totalInterest()));
    }

    @Test
    void shouldBeIdenticalAcrossRepriceModesWithoutARateStep() {
        final var reAmortize = simulator.simulate(mortgage().setRepriceMode(DebtRepriceMode.PAYMENT), BigDecimal.ZERO);
        final var extendTerm = simulator.simulate(mortgage().setRepriceMode(DebtRepriceMode.TERM), BigDecimal.ZERO);

        assertThat(reAmortize.months(), is(extendTerm.months()));
        assertThat(reAmortize.totalInterest(), comparesEqualTo(extendTerm.totalInterest()));
        assertThat(reAmortize.finalPayment(), comparesEqualTo(extendTerm.finalPayment()));
    }

    @Test
    void shouldTreatNullRepriceModeAsExtendTerm() {
        final var nullMode = simulator.simulate(risingRateMortgage(null), BigDecimal.ZERO);
        final var extendTerm = simulator.simulate(risingRateMortgage(DebtRepriceMode.TERM), BigDecimal.ZERO);

        assertThat(nullMode.months(), is(extendTerm.months()));
        assertThat(nullMode.totalInterest(), comparesEqualTo(extendTerm.totalInterest()));
        assertThat(nullMode.finalPayment(), comparesEqualTo(extendTerm.finalPayment()));
    }

    @Test
    void shouldComputeStandardAmortizingPayment() {
        // 1,000,000 at 1%/month over 120 months → 1,000,000 * 0.01 / (1 - 1.01^-120) ≈ 14,347.09.
        final var payment = simulator.amortizingPayment(
                new BigDecimal("1000000"), new BigDecimal("0.01"), 120);

        assertThat(payment, closeTo(new BigDecimal("14347.09"), new BigDecimal("0.05")));
    }

    @Test
    void shouldSplitPrincipalEvenlyWhenAmortizingAtZeroRate() {
        final var payment = simulator.amortizingPayment(new BigDecimal("1200000"), BigDecimal.ZERO, 24);

        assertThat(payment, comparesEqualTo(new BigDecimal("50000")));
    }

    @Test
    void shouldDeriveTheTermWhenNoneIsStored() {
        assertThat(simulator.termMonths(mortgage().setTermMonths(180)), is(180));        // stored term wins
        assertThat(simulator.termMonths(unsetTerm("1000000", "5", "0")), is(0));         // no payment
        assertThat(simulator.termMonths(unsetTerm("100000", "0", "10000")), is(10));     // zero rate → ceil(P / pay)
        assertThat(simulator.termMonths(unsetTerm("10000000", "12", "1000")), is(1200)); // pay below interest → cap
        assertThat(simulator.termMonths(unsetTerm("5000000", "6.5", "38000")),
                allOf(greaterThan(0), lessThanOrEqualTo(1200)));                          // ordinary → derived term
    }

    @Test
    void shouldNeverAmortizeAtTheCapWhenAPrepaymentDodgesTheEarlyExit() {
        // Payment below interest, but a (too-small) annual prepayment skips the early NEVER check, so the
        // loop runs the full cap and the balance still never clears.
        final var result = simulator.simulate(unsetTerm("10000000", "12", "50000"), new BigDecimal("1000"));

        assertThat(result.amortizes(), is(false));
    }

    private Debt unsetTerm(String principal, String annualRate, String monthly) {
        return new Debt().setName("loan").setPrincipal(new BigDecimal(principal))
                .setAnnualRate(new BigDecimal(annualRate)).setMonthly(new BigDecimal(monthly))
                .setRepriceMode(DebtRepriceMode.PAYMENT).setCurrency("PHP");
    }
}
