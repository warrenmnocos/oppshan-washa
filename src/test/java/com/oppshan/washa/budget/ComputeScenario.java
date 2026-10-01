package com.oppshan.washa.budget;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One month to compute, the month it's computed as of, and a readable name for the test report. {@link #random} builds
 * a scenario from a seed: a household of one to five currencies drawn from {@link #USD_VALUE}, exchange rates between
 * them rounded to one to ten significant digits, and a random mix of salaries, expenses, goals, and debts whose amounts
 * span zero to millions at zero to two decimals. {@link #edgeCases} pins down what random data rarely reaches. Every
 * goal and debt name carries a fresh UUID, so the cross-month sums the service runs against the shared test database
 * always start from zero and the expected figures depend on this month alone.
 */
record ComputeScenario(String name,
                       BudgetMonthView view,
                       YearMonth asOf) {

    /**
     * Rough US-dollar value of one unit of each currency, so generated rates and amounts keep realistic proportions
     * (a yen line runs to thousands, a dinar line to single digits). The figures only shape the data; nothing asserts
     * against them.
     */
    static final Map<String, Double> USD_VALUE = Map.ofEntries(
            Map.entry("JPY", 0.0066),
            Map.entry("PHP", 0.0176),
            Map.entry("USD", 1.0),
            Map.entry("EUR", 1.08),
            Map.entry("GBP", 1.27),
            Map.entry("SGD", 0.74),
            Map.entry("KRW", 0.00073),
            Map.entry("VND", 0.0000393),
            Map.entry("IDR", 0.0000618),
            Map.entry("KWD", 3.25),
            Map.entry("BHD", 2.65),
            Map.entry("CHF", 1.12),
            Map.entry("AUD", 0.66),
            Map.entry("CNY", 0.138),
            Map.entry("THB", 0.028),
            Map.entry("INR", 0.012),
            Map.entry("HKD", 0.128)
    );

    private static final List<String> CODES = USD_VALUE.keySet().stream().sorted().toList();

    private static final BudgetMonthView.TargetView OPEN =
            new BudgetMonthView.TargetView(GoalTargetType.OPEN, null, null, null, null, null, null);

    @Override
    public String toString() {
        return name;
    }

    /**
     * A generated scenario. The same seed always yields the same household, rates, and lines (names aside), so a
     * failing seed reproduces.
     */
    static ComputeScenario random(long seed) {
        final var random = new Random(seed);
        final var asOf = YearMonth.of(3000 + random.nextInt(5000), 1 + random.nextInt(12));

        final var shuffled = new ArrayList<>(CODES);
        Collections.shuffle(shuffled, random);
        final var householdSize = switch (random.nextInt(10)) {
            case 0 -> 1;
            case 1, 2, 3, 4, 5 -> 2;
            case 6, 7 -> 3;
            case 8 -> 4;
            default -> 5;
        };
        final var household = shuffled.subList(0, householdSize);
        final var outsiders = shuffled.subList(householdSize, shuffled.size());
        final var base = household.getFirst();

        final var rates = new LinkedHashMap<String, BigDecimal>();
        for (final var quote : household.subList(1, household.size())) {
            final var roll = random.nextInt(40);
            if (roll == 0) {
                continue; // a listed currency with no working rate counts one to one
            }

            rates.put(quote, roll == 1 ? BigDecimal.ONE : rate(random, base, quote));
        }

        final var pick = new CurrencyPicker(random, household, outsiders);
        final var tag = "s" + seed + "-";

        final var salaries = new ArrayList<BudgetMonthView.SalaryView>();
        final var salaryCount = random.nextInt(14) == 0 ? 0 : 1 + random.nextInt(3);
        for (var index = 0; index < salaryCount; index++) {
            final var currency = pick.next();
            final var components = new ArrayList<BudgetMonthView.ComponentView>();
            final var componentCount = 1 + random.nextInt(3);
            for (var part = 0; part < componentCount; part++) {
                components.add(new BudgetMonthView.ComponentView(
                        "Part " + part,
                        salaryAmount(random, currency),
                        random.nextBoolean(),
                        part == 0,
                        null,
                        false
                ));
            }

            salaries.add(new BudgetMonthView.SalaryView(tag + "salary-" + index, currency, "generic", components,
                    List.of(), List.of()));
        }

        final var expenses = new ArrayList<BudgetMonthView.ExpenseView>();
        final var expenseCount = random.nextInt(13);
        for (var index = 0; index < expenseCount; index++) {
            final var currency = pick.next();
            final var amount = random.nextInt(33) == 0 ? null : amount(random, currency);
            expenses.add(new BudgetMonthView.ExpenseView("Expense " + index, amount, currency, null));
        }

        if (random.nextInt(5) < 3) {
            expenses.add(random.nextInt(expenses.size() + 1),
                    new BudgetMonthView.ExpenseView("Tithe", null, base, "tithe"));
        }

        final var goals = new ArrayList<BudgetMonthView.GoalView>();
        final var goalCount = random.nextInt(7);
        for (var index = 0; index < goalCount; index++) {
            final var currency = pick.next();
            final var amount = amount(random, currency);
            final var target = switch (random.nextInt(3)) {
                case 0 -> OPEN;
                case 1 -> new BudgetMonthView.TargetView(GoalTargetType.AMOUNT,
                        amount(random, currency).multiply(BigDecimal.valueOf(1 + random.nextInt(40))),
                        null, null, null, null, null);
                default -> new BudgetMonthView.TargetView(GoalTargetType.RELATIVE, null, "net",
                        BigDecimal.valueOf(1 + random.nextInt(120), 1), null, null, null);
            };
            final var withdrawal = random.nextInt(5) == 0
                    ? amount.multiply(BigDecimal.valueOf(random.nextInt(21), 1))
                    : null;
            final var closed = random.nextInt(7) == 0;
            final var closedKey = closed && random.nextBoolean() ? asOf.toString() : null;
            goals.add(new BudgetMonthView.GoalView(tag + "goal-" + index + "-" + UUID.randomUUID(), amount, currency,
                    target, random.nextBoolean(), withdrawal, closed, closedKey));
        }

        final var debts = new ArrayList<BudgetMonthView.DebtView>();
        final var debtCount = random.nextInt(5);
        for (var index = 0; index < debtCount; index++) {
            final var currency = pick.next();
            final var name = tag + "debt-" + index + "-" + UUID.randomUUID();
            final var principal = amount(random, currency).multiply(BigDecimal.valueOf(20 + random.nextInt(400)));
            final var prepay = random.nextInt(5) < 2;
            final var prepayCurrency = random.nextBoolean() ? null : pick.next();
            final var prepayAmount = amount(random, prepayCurrency == null ? currency : prepayCurrency);
            if (random.nextInt(20) < 7) {
                final var monthly = random.nextInt(5) == 0
                        ? principal.multiply(BigDecimal.valueOf(2))
                        : principal.divide(BigDecimal.valueOf(2 + random.nextInt(60)), 2, RoundingMode.HALF_UP);
                debts.add(new BudgetMonthView.DebtView(name, principal, BigDecimal.ZERO, monthly, null, null, currency,
                        true, prepay, prepayAmount, prepayCurrency, List.of()));
            } else {
                final var annualRate = BigDecimal.valueOf(1 + random.nextInt(80), 1);
                final var termMonths = 12 * (5 + random.nextInt(31));
                final var monthly = principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(15 + random.nextInt(20), 1))
                        .setScale(2, RoundingMode.HALF_UP);
                debts.add(new BudgetMonthView.DebtView(name, principal, annualRate, monthly, termMonths,
                        DebtRepriceMode.PAYMENT, currency, false, prepay, prepayAmount, prepayCurrency, List.of()));
            }
        }

        final var currencies = household.stream()
                .map(code -> new BudgetMonthView.CurrencyView(code, code))
                .toList();
        final var name = "seed " + seed + ": " + String.join("/", household) + ", " + salaries.size() + " salaries, "
                + expenses.size() + " expenses, " + goals.size() + " goals, " + debts.size() + " debts";
        final var view = new BudgetMonthView(salaries, expenses, goals, debts, currencies, rates);
        return new ComputeScenario(name, view, asOf);
    }

    /**
     * Hand-built months for the cases random data rarely or never hits: nothing at all, no working rates, no income,
     * a foreign rate of exactly one, a listed currency without a rate, lines in a currency outside the list, extreme
     * magnitudes and tiny rates, repeating-decimal rates, half-unit display ties, a non-yen base, a repayment above
     * what's owed, closed and withdrawn goals, and a month far over budget.
     */
    static List<ComputeScenario> edgeCases() {
        final var asOf = YearMonth.of(2999, 12);
        final var edges = new ArrayList<ComputeScenario>();

        edges.add(new ComputeScenario("edge: an empty month",
                month(List.of(), List.of(), List.of(), List.of(), List.of("JPY"), Map.of()), asOf));

        edges.add(new ComputeScenario("edge: one currency and no working rates",
                month(
                        List.of(salary("JPY", "412345.67"), salary("JPY", "198000")),
                        List.of(tithe("JPY"), expense("JPY", "130000"), expense("JPY", "8350.5"), expense("JPY", "0")),
                        List.of(goal("JPY", "50000", true), goal("JPY", "12000.25", false)),
                        List.of(loan("JPY", "35000000", "1.15", "98765", true, "100000", null),
                                interestFree("JPY", "480000", "200000")),
                        List.of("JPY"),
                        Map.of()
                ), asOf));

        edges.add(new ComputeScenario("edge: spending with no income",
                month(
                        List.of(),
                        List.of(tithe("JPY"), expense("JPY", "1000"), expense("PHP", "250")),
                        List.of(goal("PHP", "800", true)),
                        List.of(interestFree("PHP", "10000", "500")),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        edges.add(new ComputeScenario("edge: a foreign rate of exactly one",
                month(
                        List.of(salary("USD", "6400"), salary("SGD", "3100.10")),
                        List.of(tithe("USD"), expense("SGD", "1999.99"), expense("USD", "0.01")),
                        List.of(goal("SGD", "500", true)),
                        List.of(loan("SGD", "450000", "2.6", "2100", true, "1000", "USD")),
                        List.of("USD", "SGD"),
                        Map.of("SGD", BigDecimal.ONE)
                ), asOf));

        edges.add(new ComputeScenario("edge: a listed currency without a working rate",
                month(
                        List.of(salary("JPY", "500000"), salary("USD", "2500")),
                        List.of(tithe("JPY"), expense("USD", "120.5"), expense("PHP", "15000")),
                        List.of(goal("USD", "300", false)),
                        List.of(),
                        List.of("JPY", "PHP", "USD"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        edges.add(new ComputeScenario("edge: lines in a currency outside the household list",
                month(
                        List.of(salary("JPY", "450000"), salary("EUR", "1800")),
                        List.of(tithe("JPY"), expense("EUR", "75.25"), expense("PHP", "3000"), expense("GBP", "10")),
                        List.of(goal("EUR", "200", true)),
                        List.of(loan("GBP", "90000", "4.1", "650", true, "50", "EUR")),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        edges.add(new ComputeScenario("edge: extreme magnitudes and tiny rates",
                month(
                        List.of(salary("VND", "987654321098.76"), salary("KWD", "0.01")),
                        List.of(tithe("VND"), expense("KWD", "0.001"), expense("VND", "123456789012.34"),
                                expense("IDR", "999999999999.99")),
                        List.of(goal("KWD", "1234567.891", true), goal("IDR", "0.01", false)),
                        List.of(loan("VND", "9876543210987", "7.9", "98765432109", true, "1", "KWD"),
                                interestFree("KWD", "0.5", "1")),
                        List.of("VND", "KWD", "IDR"),
                        Map.of("KWD", new BigDecimal("0.0000123"), "IDR", new BigDecimal("0.6359"))
                ), asOf));

        final var thirds = new ArrayList<BudgetMonthView.ExpenseView>();
        for (var index = 0; index < 7; index++) {
            thirds.add(expense(index % 2 == 0 ? "EUR" : "GBP", "1"));
            thirds.add(expense("CHF", "1"));
        }

        edges.add(new ComputeScenario("edge: repeating-decimal rates",
                month(
                        List.of(salary("EUR", "100"), salary("GBP", "100"), salary("CHF", "100")),
                        thirds,
                        List.of(goal("GBP", "1", true), goal("GBP", "1", true), goal("GBP", "1", false)),
                        List.of(loan("CHF", "1000", "3", "13", true, "1", "EUR")),
                        List.of("USD", "EUR", "GBP", "CHF"),
                        Map.of("EUR", new BigDecimal("3"), "GBP", new BigDecimal("7"), "CHF", new BigDecimal("12"))
                ), asOf));

        edges.add(new ComputeScenario("edge: half-unit display ties, positive and negative",
                month(
                        List.of(salary("JPY", "10")),
                        List.of(expense("PHP", "5"), expense("PHP", "1")),
                        List.of(goal("PHP", "3", true)),
                        List.of(),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.4"))
                ), asOf));

        edges.add(new ComputeScenario("edge: a base currency other than yen",
                month(
                        List.of(salary("PHP", "185000"), salary("JPY", "420000")),
                        List.of(tithe("PHP"), expense("JPY", "130000"), expense("USD", "29.99"),
                                expense("PHP", "8000")),
                        List.of(goal("JPY", "120000", true), goal("USD", "250", false)),
                        List.of(loan("PHP", "3500000", "6.5", "26500", true, "20000", "JPY"),
                                interestFree("JPY", "480000", "480000")),
                        List.of("PHP", "JPY", "USD"),
                        Map.of("JPY", new BigDecimal("2.6563"), "USD", new BigDecimal("0.017123"))
                ), asOf));

        edges.add(new ComputeScenario("edge: interest-free repayments at, above, and below what's owed",
                month(
                        List.of(salary("JPY", "600000")),
                        List.of(),
                        List.of(),
                        List.of(interestFree("JPY", "100000", "100000"), interestFree("JPY", "100000", "250000"),
                                interestFree("PHP", "30000", "0"), interestFree("PHP", "0", "5000")),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        edges.add(new ComputeScenario("edge: closed, withdrawn, and relative-target goals",
                month(
                        List.of(salary("JPY", "500000")),
                        List.of(tithe("JPY")),
                        List.of(
                                new BudgetMonthView.GoalView("closed-" + UUID.randomUUID(), new BigDecimal("40000"),
                                        "JPY", OPEN, true, null, true, asOf.toString()),
                                new BudgetMonthView.GoalView("withdrawn-" + UUID.randomUUID(), new BigDecimal("10000"),
                                        "PHP", OPEN, true, new BigDecimal("25000"), false, null),
                                new BudgetMonthView.GoalView("relative-" + UUID.randomUUID(), new BigDecimal("60000"),
                                        "JPY", new BudgetMonthView.TargetView(GoalTargetType.RELATIVE, null, "net",
                                        new BigDecimal("6"), null, null, null), true, null, false, null),
                                new BudgetMonthView.GoalView("amount-" + UUID.randomUUID(), new BigDecimal("8000"),
                                        "PHP", new BudgetMonthView.TargetView(GoalTargetType.AMOUNT,
                                        new BigDecimal("24000"), null, null, null, null, null), false, null, false,
                                        null)),
                        List.of(),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        edges.add(new ComputeScenario("edge: far over budget",
                month(
                        List.of(salary("JPY", "250000")),
                        List.of(tithe("JPY"), expense("JPY", "300000"), expense("PHP", "120000")),
                        List.of(goal("JPY", "50000", false)),
                        List.of(loan("JPY", "20000000", "1.2", "65000", true, "30000", "PHP")),
                        List.of("JPY", "PHP"),
                        Map.of("PHP", new BigDecimal("0.3765"))
                ), asOf));

        return edges;
    }

    /**
     * Units of {@code quote} per one {@code base} unit, jittered around the realistic cross rate and rounded to one to
     * ten significant digits, so some rates divide evenly and most don't.
     */
    private static BigDecimal rate(Random random,
                                   String base,
                                   String quote) {
        final var crossRate = USD_VALUE.get(base) / USD_VALUE.get(quote) * (0.8 + random.nextDouble() * 0.45);
        return BigDecimal.valueOf(crossRate).round(new MathContext(1 + random.nextInt(10), RoundingMode.HALF_UP));
    }

    /**
     * A line amount in {@code currency}: mostly up to about four thousand dollars' worth, sometimes zero, sometimes in
     * the millions, at zero to two decimals.
     */
    private static BigDecimal amount(Random random,
                                     String currency) {
        final var roll = random.nextInt(100);
        final double dollars;
        if (roll < 8) {
            dollars = 0;
        } else if (roll < 11) {
            dollars = random.nextDouble() * 5_000_000;
        } else {
            dollars = random.nextDouble() * 4000;
        }

        return BigDecimal.valueOf(dollars / USD_VALUE.get(currency)).setScale(random.nextInt(3), RoundingMode.HALF_UP);
    }

    /**
     * A salary component amount in {@code currency}: one to nine thousand dollars' worth, at zero to two decimals.
     */
    private static BigDecimal salaryAmount(Random random,
                                           String currency) {
        final var dollars = 1000 + random.nextDouble() * 8000;
        return BigDecimal.valueOf(dollars / USD_VALUE.get(currency)).setScale(random.nextInt(3), RoundingMode.HALF_UP);
    }

    private static BudgetMonthView month(List<BudgetMonthView.SalaryView> salaries,
                                         List<BudgetMonthView.ExpenseView> expenses,
                                         List<BudgetMonthView.GoalView> goals,
                                         List<BudgetMonthView.DebtView> debts,
                                         List<String> household,
                                         Map<String, BigDecimal> rates) {
        return new BudgetMonthView(
                salaries,
                expenses,
                goals,
                debts,
                household.stream().map(code -> new BudgetMonthView.CurrencyView(code, code)).toList(),
                rates
        );
    }

    private static BudgetMonthView.SalaryView salary(String currency,
                                                     String amount) {
        return new BudgetMonthView.SalaryView("salary-" + UUID.randomUUID(), currency, "generic",
                List.of(new BudgetMonthView.ComponentView("Basic", new BigDecimal(amount), true, true, null, false)),
                List.of(), List.of());
    }

    private static BudgetMonthView.ExpenseView expense(String currency,
                                                       String amount) {
        return new BudgetMonthView.ExpenseView("Expense", new BigDecimal(amount), currency, null);
    }

    private static BudgetMonthView.ExpenseView tithe(String baseCurrency) {
        return new BudgetMonthView.ExpenseView("Tithe", null, baseCurrency, "tithe");
    }

    private static BudgetMonthView.GoalView goal(String currency,
                                                 String amount,
                                                 boolean savings) {
        return new BudgetMonthView.GoalView("goal-" + UUID.randomUUID(), new BigDecimal(amount), currency, OPEN,
                savings, null, false, null);
    }

    private static BudgetMonthView.DebtView loan(String currency,
                                                 String principal,
                                                 String annualRate,
                                                 String monthly,
                                                 boolean prepay,
                                                 String prepayAmount,
                                                 String prepayCurrency) {
        return new BudgetMonthView.DebtView("loan-" + UUID.randomUUID(), new BigDecimal(principal),
                new BigDecimal(annualRate), new BigDecimal(monthly), 360, DebtRepriceMode.PAYMENT, currency, false,
                prepay, new BigDecimal(prepayAmount), prepayCurrency, List.of());
    }

    private static BudgetMonthView.DebtView interestFree(String currency,
                                                         String borrowed,
                                                         String repayment) {
        return new BudgetMonthView.DebtView("owed-" + UUID.randomUUID(), new BigDecimal(borrowed), BigDecimal.ZERO,
                new BigDecimal(repayment), null, null, currency, true, false, BigDecimal.ZERO, null, List.of());
    }

    /**
     * Picks a line's currency: mostly the base, often another listed currency, and now and then one outside the
     * household list (which has no rate, so it counts one to one).
     */
    private static final class CurrencyPicker {

        private final Random random;

        private final List<String> household;

        private final List<String> outsiders;

        private CurrencyPicker(Random random,
                               List<String> household,
                               List<String> outsiders) {
            this.random = random;
            this.household = household;
            this.outsiders = outsiders;
        }

        private String next() {
            final var roll = random.nextInt(100);
            if (roll < 4 && !outsiders.isEmpty()) {
                return outsiders.get(random.nextInt(outsiders.size()));
            }

            if (roll < 45) {
                return household.getFirst();
            }

            return household.get(random.nextInt(household.size()));
        }
    }
}
