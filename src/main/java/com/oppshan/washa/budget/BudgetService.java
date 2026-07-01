package com.oppshan.washa.budget;

import com.oppshan.washa.budget.engine.Breakdown;
import com.oppshan.washa.budget.engine.CurrencyConverter;
import com.oppshan.washa.budget.engine.DebtSimulator;
import com.oppshan.washa.budget.engine.SalaryEngine;
import com.oppshan.washa.budget.engine.TitheCalculator;
import com.oppshan.washa.user.UserAccount;
import com.oppshan.washa.user.UserAccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Budget computations and month CRUD over the persisted graph. Covers the combined household net
 * (each salary's net, reduced to base currency and summed), the live tithe, derived cumulative goal
 * progress, and the load/save/compute orchestration. Cumulative figures are summed from month rows,
 * never stored.
 */
@Transactional
@ApplicationScoped
public class BudgetService {

    /**
     * Throwaway YearMonth for {@code compute()}'s transient (never-persisted) month entity; the
     * compute never queries on the entity's own month. It doubles as the default {@code asOf} for the
     * no-arg {@code compute(view)}: a month back in 2000 means "nothing persisted before it," so a
     * fresh draft starts every goal's prior balance at zero.
     */
    private static final YearMonth COMPUTE_PLACEHOLDER = YearMonth.of(2000, 1);

    /** Percentage scale factor for {@code savingsRate}, which reports a percent to one decimal. */
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final SalaryEngine salaryEngine;
    private final DebtSimulator debtSimulator;
    private final FxRateRepository fxRateRepository;
    private final GoalRepository goalRepository;
    private final BudgetMonthRepository budgetMonthRepository;
    private final DebtRepository debtRepository;
    private final CurrencySettingRepository currencySettingRepository;
    private final UserAccountRepository userAccountRepository;
    private final BudgetMapper budgetMapper;

    /** Injects the salary and debt engines, the budget repositories, and the month mapper. */
    @Inject
    public BudgetService(SalaryEngine salaryEngine,
                         DebtSimulator debtSimulator,
                         FxRateRepository fxRateRepository,
                         GoalRepository goalRepository,
                         BudgetMonthRepository budgetMonthRepository,
                         DebtRepository debtRepository,
                         CurrencySettingRepository currencySettingRepository,
                         UserAccountRepository userAccountRepository,
                         BudgetMapper budgetMapper) {
        this.salaryEngine = salaryEngine;
        this.debtSimulator = debtSimulator;
        this.fxRateRepository = fxRateRepository;
        this.goalRepository = goalRepository;
        this.budgetMonthRepository = budgetMonthRepository;
        this.debtRepository = debtRepository;
        this.currencySettingRepository = currencySettingRepository;
        this.userAccountRepository = userAccountRepository;
        this.budgetMapper = budgetMapper;
    }

    /**
     * Loads a month as the export-shaped view, or an empty month (with currencies) if absent.
     * Attaching the found row hands back a managed copy so its lazy graph loads inside the transaction.
     */
    @Valid
    @NotNull
    public BudgetMonthView getMonth(@NotNull YearMonth yearMonth) {
        final var currencies = currencySettingRepository.findAll().toList();
        return budgetMonthRepository.findByYearMonth(yearMonth)
                .map(budgetMonthRepository::attachWithSession)
                .map(month -> budgetMapper.toView(month, currencies))
                .orElseGet(() -> new BudgetMonthView(List.of(), List.of(), List.of(), List.of(),
                        budgetMapper.toView(new BudgetMonth().setBaseCurrency("JPY"), currencies).cur()));
    }

    /**
     * Upserts a month from the view (replace-on-conflict), stamping who last modified it. It deletes
     * any existing row for the month and flushes before inserting the freshly-mapped one, so the save
     * replaces rather than collides.
     *
     * <p>Currencies are a single global household list (mirroring the prototype's {@code CUR}), read
     * from {@code CurrencySetting} on load. It persists the working list so adding, removing,
     * reordering, or re-symboling a currency survives a reload; without that the list is recreated
     * from an unchanged (empty) table on the next load and the edits vanish.
     */
    public void saveMonth(@NotNull YearMonth yearMonth,
                          @Valid @NotNull BudgetMonthView view,
                          UUID modifiedBy) {
        budgetMonthRepository.findByYearMonth(yearMonth)
                .map(budgetMonthRepository::attachWithSession)
                .ifPresent(budgetMonthRepository::deleteWithSession);
        budgetMonthRepository.flushWithSession();

        final var modifier = modifiedBy == null ? null : userAccountRepository.findById(modifiedBy).orElse(null);
        final var month = budgetMapper.toEntity(yearMonth, view).setLastModifiedBy(modifier);
        budgetMonthRepository.insertWithSession(month);

        syncCurrencies(view.cur());
    }

    /** Upserts each currency by code in display order and drops any no longer listed. */
    private void syncCurrencies(List<BudgetMonthView.CurrencyView> currencies) {
        final var listedCodes = currencies.stream().map(BudgetMonthView.CurrencyView::code).toList();
        for (final var setting : currencySettingRepository.findAll().toList()) {
            if (!listedCodes.contains(setting.getCode())) {
                currencySettingRepository.deleteWithSession(currencySettingRepository.attachWithSession(setting));
            }
        }

        for (var ordinal = 0; ordinal < currencies.size(); ordinal++) {
            final var currency = currencies.get(ordinal);
            final var position = ordinal;
            currencySettingRepository.findById(currency.code()).ifPresentOrElse(
                    setting -> currencySettingRepository.updateWithSession(setting.setSymbol(currency.symbol()).setOrdinal(position)),
                    () -> currencySettingRepository.insertWithSession(
                            new CurrencySetting().setCode(currency.code()).setSymbol(currency.symbol()).setOrdinal(position)));
        }
    }

    /** Live figures for an unsaved month view (no persistence). */
    @Valid
    @NotNull
    public ComputedView compute(@Valid @NotNull BudgetMonthView view) {
        return compute(view, COMPUTE_PLACEHOLDER);
    }

    /**
     * Live figures for an unsaved month view, treating {@code asOf} as the month being planned: a
     * goal's accumulated balance sums its contributions from every persisted month strictly before
     * {@code asOf} and adds this view's net contribution. No persistence.
     *
     * <p>The pipeline, stage by stage:
     * <ul>
     *   <li><b>Salaries.</b> Each income's net is reduced to base and summed into money-in. The
     *       breakdown stays in the salary's own currency (gross/lines/net exactly as the engine
     *       produced them, no conversion); only salaryNet and the money-out totals are reduced to
     *       base.</li>
     *   <li><b>Tithe.</b> Derived from net (10%). The auto tithe expense line carries no entered
     *       amount, so its derived value is what it contributes to money-out, but only when that line
     *       is present (matching the baseline, where money-out is the sum of the expense lines).</li>
     *   <li><b>Goals.</b> The accumulated balance (base) is contributions across prior months plus
     *       this month's net (contribution − withdrawal), floored at zero like the mockup's goalBalance;
     *       the contribution only lands when the goal is active (not closed, not complete). An
     *       amount/relative target is reached when the prior balance (this month's withdrawal already
     *       applied) meets it; a TIME target is done once {@code asOf} has reached the due date. The
     *       reported pct is balance / target for an amount/relative goal, the elapsed-time share for a
     *       TIME goal, and null for an open (or target-less) goal.</li>
     *   <li><b>Debts.</b> Prepayment recorded on the same debt (by name) in this year's other saved
     *       months is reduced to base at the current rates so each debt's prepayment-to-date this year
     *       can be totalled (the prototype's debtYearPrepayJpy). This month's annual prepayment is held
     *       in the debt's own currency (the simulation works in that currency); adding it to the same
     *       debt's prepayment in the year's other saved months (matched by name) gives the debt's
     *       prepayment to date this year.</li>
     *   <li><b>Money out.</b> Everything allocated: expenses (incl. the tithe line), all goals, and
     *       debt (amortization + prepayment).</li>
     * </ul>
     */
    @Valid
    @NotNull
    public ComputedView compute(@Valid @NotNull BudgetMonthView view,
                                @NotNull YearMonth asOf) {
        final var month = budgetMapper.toEntity(COMPUTE_PLACEHOLDER, view);
        final var converter = converterFor(month, view.fxRates());

        final var salaryNet = new LinkedHashMap<String, BigDecimal>();
        final var salaryBreakdown = new ArrayList<ComputedView.SalaryBreakdown>();
        var moneyIn = BigDecimal.ZERO;
        for (final var income : month.getIncomes()) {
            final Breakdown breakdown = salaryEngine.compute(income);
            final var netInBase = converter.toBase(breakdown.net(), income.getCurrency());
            salaryNet.put(income.getName(), netInBase);
            moneyIn = moneyIn.add(netInBase);

            final var deductionLines = breakdown.lines().stream()
                    .map(line -> new ComputedView.DeductionLineView(line.label(), line.amount()))
                    .toList();
            salaryBreakdown.add(new ComputedView.SalaryBreakdown(income.getName(), income.getCurrency(),
                    breakdown.gross(), deductionLines, breakdown.net()));
        }

        final var tithe = TitheCalculator.tithe(moneyIn);
        var otherExpenses = BigDecimal.ZERO;
        var titheAllocated = BigDecimal.ZERO;
        for (final var expense : month.getExpenses()) {
            if ("tithe".equals(expense.getAuto())) {
                titheAllocated = tithe;
            } else {
                otherExpenses = otherExpenses.add(converter.toBase(nullToZero(expense.getAmount()), expense.getCurrency()));
            }
        }

        var savingsGoals = BigDecimal.ZERO;
        var nonSavingsGoals = BigDecimal.ZERO;
        var savingsBalance = BigDecimal.ZERO;
        final var goalProgress = new ArrayList<ComputedView.GoalProgress>();
        final var activity = new ArrayList<ComputedView.Activity>();
        for (final var goal : month.getGoals()) {
            final var contribution = converter.toBase(nullToZero(goal.getAmount()), goal.getCurrency());
            final var withdrawal = converter.toBase(nullToZero(goal.getWithdrawal()), goal.getCurrency());

            final var priorInGoalCurrency = goalRepository.sumContributionsBefore(goal.getLabel(), goal.getCurrency(), asOf);
            final var prior = converter.toBase(priorInGoalCurrency, goal.getCurrency());

            final var amountTarget = goalTarget(goal, converter, moneyIn);
            final var heldBeforeContribution = prior.subtract(withdrawal).max(BigDecimal.ZERO);
            final var timeProgress = timeProgress(goal, asOf);
            final var amountReached = amountTarget != null && amountTarget.signum() > 0
                    && heldBeforeContribution.compareTo(amountTarget) >= 0;
            final var timeDone = goal.getTargetType() == GoalTargetType.TIME && timeProgress != null
                    && timeProgress.compareTo(BigDecimal.ONE) >= 0;
            final var complete = amountReached || timeDone;
            final var active = !goal.isClosed() && !complete;

            final var liveContribution = active ? contribution : BigDecimal.ZERO;
            if (active) {
                if (goal.isSavings()) {
                    savingsGoals = savingsGoals.add(contribution);
                } else {
                    nonSavingsGoals = nonSavingsGoals.add(contribution);
                }
            }

            final var balance = prior.add(liveContribution).subtract(withdrawal).max(BigDecimal.ZERO);

            final BigDecimal pct;
            if (goal.getTargetType() == GoalTargetType.TIME) {
                pct = timeProgress;
            } else if (amountTarget == null || amountTarget.signum() <= 0) {
                pct = null;
            } else {
                pct = balance.divide(amountTarget, 4, RoundingMode.HALF_UP).min(BigDecimal.ONE);
            }

            goalProgress.add(new ComputedView.GoalProgress(goal.getLabel(), goal.getCurrency(),
                    balance, amountTarget, pct, goal.isSavings(), complete, goal.isClosed()));

            if (goal.isSavings() && !goal.isClosed()) {
                savingsBalance = savingsBalance.add(balance);
            }

            if (withdrawal.signum() > 0) {
                activity.add(new ComputedView.Activity(goal.getLabel(), goal.getCurrency(), withdrawal, "withdrawal"));
            }

            if (goal.isClosed() && asOf.toString().equals(goal.getClosedKey())) {
                activity.add(new ComputedView.Activity(goal.getLabel(), goal.getCurrency(), balance, "closed"));
            }
        }

        var debtAmortization = BigDecimal.ZERO;
        var debtPrepayment = BigDecimal.ZERO;
        final var debtProjections = new ArrayList<ComputedView.DebtProjection>();
        final var prepayYear = new ArrayList<ComputedView.PrepayYear>();

        final var priorPrepayByName = new HashMap<String, BigDecimal>();
        for (final var priorDebt : debtRepository.findPrepaidInYearExcept(
                YearMonth.of(asOf.getYear(), 1),
                YearMonth.of(asOf.getYear(), 12),
                asOf)) {
            final var priorCurrency = priorDebt.getPrepayCurrency() == null ? priorDebt.getCurrency() : priorDebt.getPrepayCurrency();
            priorPrepayByName.merge(priorDebt.getName(),
                    converter.toBase(nullToZero(priorDebt.getPrepayAmount()), priorCurrency), BigDecimal::add);
        }

        for (final var debt : month.getDebts()) {
            debtAmortization = debtAmortization.add(converter.toBase(nullToZero(debt.getMonthly()), debt.getCurrency()));

            var annualPrepayInDebtCurrency = BigDecimal.ZERO;
            if (debt.isPrepay()) {
                final var prepayCurrency = debt.getPrepayCurrency() == null ? debt.getCurrency() : debt.getPrepayCurrency();
                final var amountInBase = converter.toBase(nullToZero(debt.getPrepayAmount()), prepayCurrency);
                debtPrepayment = debtPrepayment.add(amountInBase);
                annualPrepayInDebtCurrency = amountInBase.multiply(converter.rateOf(debt.getCurrency()));

                final var yearToDateBase = amountInBase.add(priorPrepayByName.getOrDefault(debt.getName(), BigDecimal.ZERO));
                prepayYear.add(new ComputedView.PrepayYear(debt.getName(), debt.getCurrency(),
                        yearToDateBase.multiply(converter.rateOf(debt.getCurrency())), yearToDateBase));
            }

            final var baseline = debtSimulator.simulate(debt, BigDecimal.ZERO);
            final var withPrepay = annualPrepayInDebtCurrency.signum() > 0
                    ? debtSimulator.simulate(debt, annualPrepayInDebtCurrency)
                    : baseline;
            debtProjections.add(new ComputedView.DebtProjection(debt.getName(),
                    baseline.months(), baseline.totalInterest(),
                    withPrepay.months(), withPrepay.totalInterest()));
        }
        final var debt = debtAmortization.add(debtPrepayment);

        final var moneyOut = otherExpenses.add(titheAllocated).add(savingsGoals).add(nonSavingsGoals).add(debt);
        final var free = moneyIn.subtract(moneyOut);
        final var savingsRate = savingsRate(moneyIn, otherExpenses, titheAllocated, nonSavingsGoals, debtAmortization);

        return new ComputedView(moneyIn, moneyOut, free, tithe, otherExpenses, debt,
                savingsGoals, nonSavingsGoals, savingsRate, salaryNet, salaryBreakdown,
                debtProjections, goalProgress, savingsBalance, activity, prepayYear);
    }

    /**
     * A goal's amount target, reduced to base currency: the fixed {@code targetAmount} for an AMOUNT
     * goal, or {@code targetMult × net} for a RELATIVE one (the mockup's {@code goalTargetJpy}, where
     * the relative base is the combined household net). Returns null when there's no amount target to
     * speak of: OPEN and TIME goals, and an AMOUNT/RELATIVE goal whose figure is unset.
     */
    private static BigDecimal goalTarget(Goal goal,
                                         CurrencyConverter converter,
                                         BigDecimal net) {
        return switch (goal.getTargetType()) {
            case AMOUNT -> goal.getTargetAmount() == null
                    ? null
                    : converter.toBase(goal.getTargetAmount(), goal.getCurrency());
            case RELATIVE -> goal.getTargetMult() == null
                    ? null
                    : goal.getTargetMult().multiply(net);
            case OPEN, TIME -> null;
        };
    }

    /**
     * Elapsed-time progress for a TIME goal, clamped to {@code [0, 1]} (the mockup's
     * {@code goalTimeProgress}): the share of the span from the goal's start to its due date that
     * {@code asOf} has reached. The start is the first day of the goal's earliest persisted month (its
     * creation, the mockup's {@code goalStartDate}), falling back to the first day of {@code asOf} when
     * nothing is persisted yet. Returns null for a non-TIME goal or one with no resolvable deadline.
     */
    private BigDecimal timeProgress(Goal goal,
                                    YearMonth asOf) {
        if (goal.getTargetType() != GoalTargetType.TIME) {
            return null;
        }

        final var start = goalRepository.earliestMonthOf(goal.getLabel(), goal.getCurrency())
                .orElse(asOf)
                .atDay(1);
        final var due = dueDateOf(goal, start);
        if (due == null) {
            return null;
        }

        final var now = asOf.atDay(1);
        final var total = ChronoUnit.DAYS.between(start, due);
        if (total <= 0) {
            return now.isBefore(due) ? BigDecimal.ZERO : BigDecimal.ONE;
        }

        final var elapsed = ChronoUnit.DAYS.between(start, now);
        return BigDecimal.valueOf(elapsed)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);
    }

    /**
     * A TIME goal's due date: an explicit {@code targetDueDate} wins; otherwise {@code start} plus the
     * period count of the named unit (days/weeks/months/years, defaulting to months), matching the
     * mockup's {@code goalDueDate}. Null when neither a due date nor a period count is set.
     */
    private static LocalDate dueDateOf(Goal goal,
                                       LocalDate start) {
        if (goal.getTargetDueDate() != null) {
            return goal.getTargetDueDate();
        }

        if (goal.getTargetPeriodCount() == null) {
            return null;
        }

        final var count = goal.getTargetPeriodCount();
        final var unit = goal.getTargetPeriodUnit() == null ? "months" : goal.getTargetPeriodUnit();
        return switch (unit) {
            case "days" -> start.plusDays(count);
            case "weeks" -> start.plusWeeks(count);
            case "years" -> start.plusYears(count);
            default -> start.plusMonths(count);
        };
    }

    /**
     * Share of net income saved or left free, as a percentage to one decimal:
     * {@code (net − expenses − tithe − nonSavingsGoals − debtAmortization) / net}. Savings-flagged
     * goals stay in the numerator (money moved into savings still counts as saved), and so does debt
     * prepayment: it pays down principal, which is itself saving, so only amortization is subtracted.
     * Returns zero when there's no net income, so the divide never sees a zero denominator.
     */
    private static BigDecimal savingsRate(BigDecimal moneyIn,
                                          BigDecimal otherExpenses,
                                          BigDecimal tithe,
                                          BigDecimal nonSavingsGoals,
                                          BigDecimal debtAmortization) {
        if (moneyIn.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        final var saved = moneyIn.subtract(otherExpenses).subtract(tithe).subtract(nonSavingsGoals).subtract(debtAmortization);
        return saved.multiply(HUNDRED).divide(moneyIn, 1, RoundingMode.HALF_UP);
    }

    /** Null-to-zero: a missing amount folds to {@code BigDecimal.ZERO} so the arithmetic never sees null. */
    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Combined household net for a (loaded) month, in base currency. */
    @NotNull
    public BigDecimal combinedNet(@NotNull BudgetMonth month) {
        final var converter = converterFor(month, null);
        var total = BigDecimal.ZERO;
        for (final var income : month.getIncomes()) {
            final Breakdown breakdown = salaryEngine.compute(income);
            total = total.add(converter.toBase(breakdown.net(), income.getCurrency()));
        }

        return total;
    }

    /** Tithe for a month: 10% of combined net. */
    @NotNull
    public BigDecimal tithe(@NotNull BudgetMonth month) {
        return TitheCalculator.tithe(combinedNet(month));
    }

    /** A goal's cumulative progress before a month, in the goal's currency. */
    @NotNull
    public BigDecimal cumulativeGoalProgressBefore(@NotNull String label,
                                                   @NotNull String currency,
                                                   @NotNull YearMonth before) {
        return goalRepository.sumContributionsBefore(label, currency, before);
    }

    /**
     * Builds the currency converter for a month. A live recompute passes the working (unsaved) rates
     * so the figures track edits without persisting; a saved or loaded month passes none, so this
     * falls back to the stored {@code fx_rate} rows for the month's base currency.
     */
    private CurrencyConverter converterFor(BudgetMonth month,
                                           Map<String, BigDecimal> workingRates) {
        if (workingRates != null && !workingRates.isEmpty()) {
            return new CurrencyConverter(month.getBaseCurrency(), new HashMap<>(workingRates));
        }

        final var ratesByCode = new HashMap<String, BigDecimal>();
        for (final var fxRate : fxRateRepository.findByBaseCurrency(month.getBaseCurrency())) {
            ratesByCode.put(fxRate.getId().getQuoteCurrency(), fxRate.getRate());
        }

        return new CurrencyConverter(month.getBaseCurrency(), ratesByCode);
    }
}
