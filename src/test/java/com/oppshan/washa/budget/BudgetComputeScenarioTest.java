package com.oppshan.washa.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

/**
 * Checks every money figure {@link BudgetService#compute} returns against {@link ComputeOracle}'s independent
 * re-derivation, across the hand-built {@link ComputeScenario#edgeCases} and two thousand generated months that mix
 * seventeen currencies, rates rounded to one to ten significant digits, every section, and every line kind.
 *
 * <p>Each scenario is held to three standards. Every figure matches the oracle to within a trillionth of a currency
 * unit. Every figure the page shows rounds to the same whole unit the exact figure does, read the way the browser reads
 * it (parsed to a double, cut to 15 significant digits, rounded half away from zero). And the figures agree with each
 * other exactly: the categories add up to money out, the sections' subtotals to money out, the sections' per-currency
 * totals to the overall ones, the prepayments to their total, and so on. A slice of the scenarios goes through the
 * compute endpoint too, so the JSON the page receives is proven to carry every figure intact.
 */
@QuarkusTest
class BudgetComputeScenarioTest {

    private static final int GENERATED_SCENARIOS = 2000;

    private static final int SCENARIOS_OVER_HTTP = 200;

    @Inject
    BudgetService budgetService;

    @Inject
    ObjectMapper objectMapper;

    static Stream<ComputeScenario> scenarios() {
        return Stream.concat(
                ComputeScenario.edgeCases().stream(),
                LongStream.rangeClosed(1, GENERATED_SCENARIOS).mapToObj(ComputeScenario::random)
        );
    }

    static Stream<ComputeScenario> scenariosOverHttp() {
        return Stream.concat(
                ComputeScenario.edgeCases().stream(),
                LongStream.rangeClosed(1, SCENARIOS_OVER_HTTP).mapToObj(ComputeScenario::random)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void shouldMatchTheIndependentFiguresInEveryScenario(ComputeScenario scenario) {
        assertMatchesOracle(scenario, budgetService.compute(scenario.view(), scenario.asOf()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenariosOverHttp")
    @TestSecurity(user = "alice")
    void shouldCarryEveryFigureIntactThroughTheComputeEndpoint(ComputeScenario scenario) throws Exception {
        final var response = given()
                .contentType("application/json")
                .body(objectMapper.writeValueAsString(scenario.view()))
                .when().post("/api/budget/compute?month=" + scenario.asOf())
                .then().statusCode(200)
                .extract().asString();

        assertMatchesOracle(scenario, objectMapper.readValue(response, ComputedView.class));
    }

    private void assertMatchesOracle(ComputeScenario scenario,
                                     ComputedView actual) {
        final var expected = ComputeOracle.expect(scenario.view(), scenario.asOf());
        final var checks = new Checks(scenario);

        // Headline figures: close to the oracle, and shown as the same whole unit.
        checks.shown("money in", actual.moneyIn(), expected.moneyIn());
        checks.shown("money out", actual.moneyOut(), expected.moneyOut());
        checks.shown("free cash", actual.free(), expected.free());
        checks.shown("over budget by", actual.overBudgetBy(), expected.overBudgetBy());
        checks.shown("tithe", actual.tithe(), expected.tithe());
        checks.shown("tithe allocated", actual.titheAllocated(), expected.titheAllocated());
        checks.shown("other expenses", actual.otherExpenses(), expected.otherExpenses());
        checks.shown("debt", actual.debt(), expected.debt());
        checks.shown("savings goals", actual.savingsGoals(), expected.savingsGoals());
        checks.shown("non-savings goals", actual.nonSavingsGoals(), expected.nonSavingsGoals());
        checks.rounded("savings rate", actual.savingsRate(), expected.savingsRateUnrounded(), 1);

        // The headline figures agree with each other exactly.
        checks.exact("money out is its categories", actual.moneyOut(), sum(actual.otherExpenses(),
                actual.titheAllocated(), actual.savingsGoals(), actual.nonSavingsGoals(), actual.debt()));
        checks.exact("free cash is money in less money out", actual.free(),
                actual.moneyIn().subtract(actual.moneyOut()));
        checks.exact("over budget is the overshoot, floored at zero", actual.overBudgetBy(),
                actual.moneyOut().subtract(actual.moneyIn()).max(BigDecimal.ZERO));
        checks.exact("the tithe is a tenth of money in", actual.tithe(),
                actual.moneyIn().multiply(new BigDecimal("0.10")));
        checks.exact("money in is the salaries' nets", actual.moneyIn(),
                actual.salaryNet().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));

        // Salaries: one net per salary in base; the breakdown stays in the salary's own currency, unconverted.
        checks.equal("salary names", List.copyOf(actual.salaryNet().keySet()),
                List.copyOf(expected.salaryNet().keySet()));
        expected.salaryNet().forEach((name, net) -> checks.close("net of " + name, actual.salaryNet().get(name), net));
        checks.equal("salary breakdowns", actual.salaryBreakdown().size(), expected.salaryGross().size());
        for (var index = 0; index < Math.min(actual.salaryBreakdown().size(), expected.salaryGross().size()); index++) {
            final var breakdown = actual.salaryBreakdown().get(index);
            checks.exact("gross of " + breakdown.name(), breakdown.gross(), expected.salaryGross().get(index));
            checks.exact("net of " + breakdown.name() + " (no deductions)", breakdown.net(), breakdown.gross());
        }

        // Section subtotals: the base totals, the per-currency lines, and how they tie together.
        checks.section("expenses", actual.expenseSubtotal(), expected.expensesByCurrency(),
                sum(actual.otherExpenses(), actual.titheAllocated()));
        checks.section("savings & goals", actual.goalSubtotal(), expected.goalsByCurrency(),
                sum(actual.savingsGoals(), actual.nonSavingsGoals()));
        checks.section("debt financing", actual.debtSubtotal(), expected.debtsByCurrency(), actual.debt());
        checks.shown("expenses subtotal", actual.expenseSubtotal().total(),
                sum(expected.otherExpenses(), expected.titheAllocated()));
        checks.shown("savings & goals subtotal", actual.goalSubtotal().total(),
                sum(expected.savingsGoals(), expected.nonSavingsGoals()));
        checks.shown("debt financing subtotal", actual.debtSubtotal().total(), expected.debt());
        checks.exact("the section subtotals are money out", actual.moneyOut(), sum(actual.expenseSubtotal().total(),
                actual.goalSubtotal().total(), actual.debtSubtotal().total()));

        // Overall per-currency totals: the oracle's, and exactly the sections' per-currency totals added up.
        checks.currencies("overall", actual.moneyOutByCurrency(), expected.moneyOutByCurrency());
        for (final var overall : actual.moneyOutByCurrency()) {
            checks.exact("overall " + overall.currency() + " is the sections' " + overall.currency(), overall.amount(),
                    sum(amountIn(actual.expenseSubtotal(), overall.currency()),
                            amountIn(actual.goalSubtotal(), overall.currency()),
                            amountIn(actual.debtSubtotal(), overall.currency())));
        }

        checks.convertsBack("overall per-currency totals", actual.moneyOutByCurrency(), actual.moneyOut());

        // Goals: balances, targets, rounded progress, the running savings balance, and this month's activity.
        checks.equal("goal count", actual.goalProgress().size(), expected.goalProgress().size());
        for (var index = 0; index < Math.min(actual.goalProgress().size(), expected.goalProgress().size()); index++) {
            final var goal = actual.goalProgress().get(index);
            final var want = expected.goalProgress().get(index);
            final var label = "goal " + want.label();
            checks.equal(label + " label", goal.label(), want.label());
            checks.equal(label + " currency", goal.currency(), want.currency());
            checks.shown(label + " balance", goal.balance(), want.balance());
            checks.close(label + " target", goal.target(), want.target());
            checks.rounded(label + " progress", goal.pct(), want.pct(), 4);
            checks.equal(label + " savings", goal.savings(), want.savings());
            checks.equal(label + " complete", goal.complete(), want.complete());
            checks.equal(label + " closed", goal.closed(), want.closed());
        }

        checks.shown("savings balance", actual.savingsBalance(), expected.savingsBalance());
        checks.exact("savings balance is the open savings goals' balances", actual.savingsBalance(),
                actual.goalProgress().stream()
                        .filter(goal -> goal.savings() && !goal.closed())
                        .map(ComputedView.GoalProgress::balance)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));

        checks.equal("activity count", actual.activity().size(), expected.activity().size());
        for (var index = 0; index < Math.min(actual.activity().size(), expected.activity().size()); index++) {
            final var entry = actual.activity().get(index);
            final var want = expected.activity().get(index);
            checks.equal("activity " + index, entry.label() + " " + entry.kind() + " " + entry.currency(),
                    want.label() + " " + want.kind() + " " + want.currency());
            checks.shown("activity " + index + " amount", entry.amount(), want.amount());
        }

        // Debts: the yearly prepayment card and its total, each debt's standing, and what's still owed overall.
        checks.equal("prepayment rows", actual.prepayYear().size(), expected.prepayYear().size());
        for (var index = 0; index < Math.min(actual.prepayYear().size(), expected.prepayYear().size()); index++) {
            final var row = actual.prepayYear().get(index);
            final var want = expected.prepayYear().get(index);
            checks.equal("prepayment " + index, row.name() + " " + row.currency(), want.name() + " " + want.currency());
            checks.shown("prepayment " + index + " amount", row.amount(), want.amount());
            checks.shown("prepayment " + index + " in base", row.amountBase(), want.amountBase());
        }

        checks.shown("prepayment total", actual.prepayYearTotal(), expected.prepayYearTotal());
        checks.exact("prepayment total is its rows", actual.prepayYearTotal(), actual.prepayYear().stream()
                .map(ComputedView.PrepayYear::amountBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        checks.equal("debt count", actual.debtProgress().size(), expected.debtProgress().size());
        for (var index = 0; index < Math.min(actual.debtProgress().size(), expected.debtProgress().size()); index++) {
            final var debt = actual.debtProgress().get(index);
            final var want = expected.debtProgress().get(index);
            final var label = "debt " + want.name();
            checks.equal(label + " identity", debt.name() + " " + debt.currency() + " " + debt.interestFree(),
                    want.name() + " " + want.currency() + " " + want.interestFree());
            checks.shown(label + " balance", debt.balance(), want.balance());
            checks.shown(label + " balance in base", debt.balanceBase(), want.balanceBase());
            checks.close(label + " borrowed", debt.borrowed(), want.borrowed());
            checks.close(label + " repaid", debt.repaid(), want.repaid());
            checks.close(label + " repayment", debt.repayment(), want.repayment());
            checks.rounded(label + " progress", debt.pct(), want.pct(), 4);
            checks.equal(label + " complete", debt.complete(), want.complete());
        }

        checks.shown("still owed", actual.debtBalance(), expected.debtBalance());
        checks.exact("still owed is each debt's balance", actual.debtBalance(), actual.debtProgress().stream()
                .map(ComputedView.DebtProgress::balanceBase)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // Payoff projections, with and without the annual prepayment: the months exactly, the interest as shown.
        checks.equal("projection count", actual.debts().size(), expected.debtProjections().size());
        for (var index = 0; index < Math.min(actual.debts().size(), expected.debtProjections().size()); index++) {
            final var projection = actual.debts().get(index);
            final var want = expected.debtProjections().get(index);
            final var label = "projection " + want.name();
            checks.equal(label + " name", projection.name(), want.name());
            checks.equal(label + " months", projection.months(), want.months());
            checks.shown(label + " interest", projection.totalInterest(), want.totalInterest());
            checks.equal(label + " months with prepayment", projection.prepayMonths(), want.prepayMonths());
            checks.shown(label + " interest with prepayment", projection.prepayInterest(), want.prepayInterest());
            checks.digits(label + " interest", projection.totalInterest());
            checks.digits(label + " interest with prepayment", projection.prepayInterest());
        }

        assertThat(scenario + "\n" + String.join("\n", checks.failures), checks.failures, is(empty()));
    }

    private static BigDecimal sum(BigDecimal... amounts) {
        var total = BigDecimal.ZERO;
        for (final var amount : amounts) {
            total = total.add(amount);
        }

        return total;
    }

    private static BigDecimal amountIn(ComputedView.CategorySubtotal subtotal,
                                       String currency) {
        return subtotal.byCurrency().stream()
                .filter(entry -> entry.currency().equals(currency))
                .map(ComputedView.CurrencyTotal::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Collects every mismatch in a scenario, so one failing run lists all of them rather than the first.
     */
    private static final class Checks {

        /**
         * A trillionth of a currency unit, absolute, plus 10⁻²⁴ of the figure itself: far below anything that
         * could change a displayed figure, far above the service's 34-digit rounding noise.
         */
        private static final BigDecimal ABSOLUTE_TOLERANCE = new BigDecimal("1e-12");

        private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("1e-24");

        private final ComputeScenario scenario;

        private final List<String> failures = new ArrayList<>();

        private Checks(ComputeScenario scenario) {
            this.scenario = scenario;
        }

        /**
         * Same value, or both absent.
         */
        private void equal(String what,
                           Object actual,
                           Object expected) {
            if (!Objects.equals(actual, expected)) {
                failures.add(what + ": got " + actual + ", expected " + expected);
            }
        }

        /**
         * Numerically identical (scale aside): for figures that must agree with each other exactly.
         */
        private void exact(String what,
                           BigDecimal actual,
                           BigDecimal expected) {
            if (actual == null || expected == null || actual.compareTo(expected) != 0) {
                failures.add(what + ": got " + plain(actual) + ", expected exactly " + plain(expected));
            }
        }

        /**
         * Within tolerance of the oracle's figure, or both absent.
         */
        private void close(String what,
                           BigDecimal actual,
                           BigDecimal expected) {
            if (actual == null && expected == null) {
                return;
            }

            if (actual == null || expected == null) {
                failures.add(what + ": got " + plain(actual) + ", expected " + plain(expected));
                return;
            }

            final var tolerance = ABSOLUTE_TOLERANCE.add(expected.abs().multiply(RELATIVE_TOLERANCE));
            if (actual.subtract(expected).abs().compareTo(tolerance) > 0) {
                failures.add(what + ": got " + plain(actual) + ", expected " + plain(expected));
            }
        }

        /**
         * No more than 80 significant digits. The service rounds each step to 34, and adding figures of very
         * different sizes can widen a sum past that, but never by much; hundreds or thousands of digits mean a
         * figure stopped being rounded and grows every month of a loan.
         */
        private void digits(String what,
                            BigDecimal actual) {
            if (actual != null && actual.precision() > 80) {
                failures.add(what + ": carries " + actual.precision() + " digits");
            }
        }

        /**
         * Close to the oracle, and shown on the page as the same whole unit the exact figure rounds to.
         */
        private void shown(String what,
                           BigDecimal actual,
                           BigDecimal expected) {
            close(what, actual, expected);
            if (actual != null && expected != null && displayed(actual).compareTo(displayed(expected)) != 0) {
                failures.add(what + ": shows " + displayed(actual) + ", should show " + displayed(expected));
            }
        }

        /**
         * Rounded to {@code scale} decimals, and within half a unit of that last decimal of the exact figure: what a
         * correct HALF_UP rounding gives (an exact tie may land either side of the service's 34-digit noise).
         */
        private void rounded(String what,
                             BigDecimal actual,
                             BigDecimal exact,
                             int scale) {
            if (actual == null && exact == null) {
                return;
            }

            if (actual == null || exact == null) {
                failures.add(what + ": got " + plain(actual) + ", expected about " + plain(exact));
                return;
            }

            final var halfUnit = BigDecimal.ONE.movePointLeft(scale).divide(BigDecimal.TWO);
            final var withinHalfUnit = actual.subtract(exact).abs().compareTo(halfUnit.add(ABSOLUTE_TOLERANCE)) <= 0;
            final var roundedToScale = actual.stripTrailingZeros().scale() <= scale;
            if (!withinHalfUnit || !roundedToScale) {
                failures.add(what + ": got " + plain(actual) + ", expected " + plain(exact) + " rounded to " + scale
                        + " decimals");
            }
        }

        /**
         * A section's subtotal: its per-currency lines match the oracle's, in order; they convert back to the section
         * total; and the section total is the categories it covers, exactly.
         */
        private void section(String name,
                             ComputedView.CategorySubtotal actual,
                             List<ComputedView.CurrencyTotal> expected,
                             BigDecimal categories) {
            exact(name + " subtotal is its categories", actual.total(), categories);
            currencies(name, actual.byCurrency(), expected);
            convertsBack(name + " per-currency totals", actual.byCurrency(), actual.total());
        }

        /**
         * The per-currency totals list the same currencies in the same order, each close to the oracle and shown as the
         * same whole unit.
         */
        private void currencies(String name,
                                List<ComputedView.CurrencyTotal> actual,
                                List<ComputedView.CurrencyTotal> expected) {
            equal(name + " currencies", actual.stream().map(ComputedView.CurrencyTotal::currency).toList(),
                    expected.stream().map(ComputedView.CurrencyTotal::currency).toList());
            for (var index = 0; index < Math.min(actual.size(), expected.size()); index++) {
                shown(name + " total for " + expected.get(index).currency(), actual.get(index).amount(),
                        expected.get(index).amount());
            }
        }

        /**
         * Converting each per-currency total to base and adding them up gives back the base total.
         */
        private void convertsBack(String what,
                                  List<ComputedView.CurrencyTotal> byCurrency,
                                  BigDecimal total) {
            var converted = BigDecimal.ZERO;
            for (final var entry : byCurrency) {
                converted = converted.add(ComputeOracle.toBase(scenario.view(), entry.amount(), entry.currency()));
            }

            close(what + " convert back to their total", total, converted);
        }

        /**
         * What the page shows for a figure, step for step as the browser gets there: the JSON number parsed to a
         * double, its magnitude cut to 15 significant digits, rounded to a whole unit half away from zero, and the sign
         * put back (a zero stays a plain zero).
         */
        private static BigDecimal displayed(BigDecimal value) {
            final var magnitude = new BigDecimal(Math.abs(value.doubleValue()))
                    .round(new MathContext(15, RoundingMode.HALF_UP))
                    .setScale(0, RoundingMode.HALF_UP);
            return value.signum() < 0 ? magnitude.negate() : magnitude;
        }

        private static String plain(BigDecimal value) {
            return value == null ? "null" : value.toPlainString();
        }
    }
}
