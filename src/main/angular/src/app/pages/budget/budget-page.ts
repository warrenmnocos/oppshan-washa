import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {BudgetStore} from '../../services/budget-store';
import {MoneyPipe} from '../../services/money.pipe';
import {ChartSlice, MoneyChart} from './money-chart';
import {SalaryDialog} from './salary-dialog';
import {GoalDialog} from './goal-dialog';
import {DebtDialog} from './debt-dialog';
import {BudgetMonth, Component as PayComponent, Debt, DebtProjection, Deduction, Expense, Goal, NEVER_AMORTIZES, Salary} from '../../models/budget.models';
import {DebtRepriceMode} from '../../models/debt-reprice-mode';
import {DeductionBase} from '../../models/deduction-base';
import {DeductionType} from '../../models/deduction-type';
import {GoalTargetType} from '../../models/goal-target-type';

/** A translated deduction-config note: an i18n key plus the interpolation params it expects. */
interface DeductionNote {
  key: string;
  params: Record<string, string | number>;
}

// Currency symbols for the common codes the market feed returns, so an added currency shows a
// real glyph rather than its bare code; anything not listed falls back to the code (see symbolFor).
const CURRENCY_SYMBOLS: Record<string, string> = {
  USD: '$', EUR: '€', GBP: '£', JPY: '¥', CNY: '¥', PHP: '₱', KRW: '₩', INR: '₹', THB: '฿',
  VND: '₫', IDR: 'Rp', MYR: 'RM', SGD: 'S$', HKD: 'HK$', TWD: 'NT$', AUD: 'A$', CAD: 'C$',
  NZD: 'NZ$', CHF: 'Fr', SEK: 'kr', NOK: 'kr', DKK: 'kr', RUB: '₽', BRL: 'R$', MXN: 'Mex$',
  ZAR: 'R', TRY: '₺', AED: 'د.إ', SAR: '﷼', QAR: 'ر.ق', ILS: '₪', PLN: 'zł', CZK: 'Kč',
  HUF: 'Ft', RON: 'lei', BDT: '৳', PKR: '₨', LKR: 'Rs', NPR: 'Rs', MMK: 'K', KHR: '៛',
  LAK: '₭', BND: 'B$', MOP: 'MOP$', MNT: '₮', KZT: '₸', UAH: '₴', NGN: '₦', EGP: 'E£',
  KES: 'KSh', GHS: '₵', COP: '$', CLP: '$', ARS: '$', PEN: 'S/', UYU: '$U',
};

// Allocation-chart segment colors (warm-anchored to washa's amber identity, cool accents for
// separation), in the fixed order: tithe, debt, other expenses, savings, goals, free cash.
const SEGMENT_COLORS = {
  tithe: '#8C6BB1',
  debt: '#BE4233',
  otherExpenses: '#D38A2E',
  savings: '#3F9E8C',
  goals: '#7A8450',
  free: '#B0651C',
};

/** One editable FX row against the base: current/reciprocal rate, slider bounds, live market quote. */
interface FxRow {
  code: string;
  sym: string;
  rate: number;
  reciprocal: number;
  step: number;
  min: number;
  max: number;
  market: number | null;
}

@Component({
  selector: 'app-budget-page',
  standalone: true,
  imports: [FormsModule, RouterLink, MoneyPipe, MoneyChart, SalaryDialog, GoalDialog, DebtDialog, TranslatePipe],
  templateUrl: './budget-page.html',
  styleUrl: './budget-page.scss',
})
export class BudgetPage implements OnInit {

  readonly store = inject(BudgetStore);
  private readonly translate = inject(TranslateService);

  readonly importError = signal<string | null>(null);
  readonly editingSalaryIndex = signal<number | null>(null);
  readonly editingGoalIndex = signal<number | null>(null);
  readonly editingDebtIndex = signal<number | null>(null);

  // Drag-to-reorder state for the currency list: the row being dragged and the row currently hovered
  // as a drop target. Both drive the .dragging / .droptarget visual feedback; null when idle.
  readonly draggingCurrency = signal<number | null>(null);
  readonly dropTargetCurrency = signal<number | null>(null);

  readonly month = this.store.month;
  readonly computed = this.store.computed;

  // Fixed-length placeholder arrays so @for can lay out shimmer rows/metrics during the initial
  // load (store.loading()), sized to roughly match the real content so the layout doesn't jump.
  readonly skeletonMetrics = Array.from({length: 5});
  readonly skeletonRows = Array.from({length: 3});

  readonly baseCurrency = computed(() => this.month().cur[0] ?? {code: 'JPY', sym: '¥'});

  // The home (second) currency a base figure is shown against, when one is listed (mockup homeCode).
  private readonly homeCurrency = computed(() => this.month().cur[1] ?? null);

  // Stateless formatter reused for the "≈" conversion captions (same glyphs as the money pipe).
  private readonly money = new MoneyPipe();

  /** The currency record for a code, so the money pipe shows its symbol; falls back to the code. */
  currencyFor(code: string) {
    return this.month().cur.find((currency) => currency.code === code) ?? code;
  }

  /**
   * Display-only "≈ <base>" caption for a per-row amount in its own currency: converts a non-base
   * amount back to the base for at-a-glance parity, mirroring the prototype's convText. Renders
   * nothing when the amount is already in the base currency (no point restating it), when no stored
   * rate is known for that currency, or when the amount isn't a finite number. This never feeds any
   * stored/emitted value — the backend stays authoritative for every money figure.
   */
  convB(amount: number | null | undefined,
        currency: string): string {
    const base = this.baseCurrency();
    if (currency === base.code) {
      return '';
    }

    const rate = this.store.fxRates()[currency];
    if (rate === undefined || !isFinite(rate) || rate <= 0) {
      return '';
    }

    const value = amount ?? 0;
    if (!isFinite(value)) {
      return '';
    }

    // Rates are units of quote per one base unit, so a quote amount divides back to base.
    return `≈ ${this.money.transform(value / rate, base)}`;
  }

  /**
   * Display-only "≈ <home>" caption for a base-currency figure (the in/out/free totals and metrics,
   * which the backend computes in base): converts to the listed home currency, mirroring the
   * prototype's peso(x * fxNow()). Renders nothing when there's no second currency or no stored rate
   * for it, or when the amount isn't finite. Display-only — it never alters a computed figure.
   */
  convHome(baseAmount: number | null | undefined): string {
    const home = this.homeCurrency();
    if (home === null) {
      return '';
    }

    const rate = this.store.fxRates()[home.code];
    if (rate === undefined || !isFinite(rate) || rate <= 0) {
      return '';
    }

    const value = baseAmount ?? 0;
    if (!isFinite(value)) {
      return '';
    }

    // Rates are units of quote per one base unit, so a base amount multiplies into the home currency.
    return `≈ ${this.money.transform(value * rate, home)}`;
  }

  // Allocation of net income across the month, matching the baseline's six segments. The backend
  // computes each total in base currency; the chart only filters out empty slices and colors them.
  readonly chartSlices = computed<ChartSlice[]>(() => {
    const result = this.computed();
    return [
      {label: 'Tithe', value: result.tithe, color: SEGMENT_COLORS.tithe},
      {label: 'Debt financing', value: result.debt, color: SEGMENT_COLORS.debt},
      {label: 'Other expenses', value: result.otherExpenses, color: SEGMENT_COLORS.otherExpenses},
      {label: 'Savings & investing', value: result.savingsGoals, color: SEGMENT_COLORS.savings},
      {label: 'Goals', value: result.nonSavingsGoals, color: SEGMENT_COLORS.goals},
      {label: 'Free cash', value: Math.max(0, result.free), color: SEGMENT_COLORS.free},
    ].filter((slice) => slice.value > 0);
  });

  /**
   * The working month as a formatted label ("June 2026"), derived from the store's YYYY-MM key for
   * the floatbar and the print-only header line. Parsing day 1 in UTC and formatting in UTC avoids a
   * timezone roll-back (a local-midnight Date for the 1st can land on the prior month west of UTC).
   * Falls back to the raw key if it isn't a well-formed YYYY-MM.
   */
  readonly monthLabel = computed(() => {
    const key = this.store.monthKey();
    const match = /^(\d{4})-(\d{2})$/.exec(key);
    if (!match) {
      return key;
    }

    const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, 1));
    return date.toLocaleDateString('en-US', {month: 'long', year: 'numeric', timeZone: 'UTC'});
  });

  /** The working month's calendar year (the YYYY of the month key) for the annual-prepayment caption. */
  readonly currentYear = computed(() => {
    const match = /^(\d{4})-\d{2}$/.exec(this.store.monthKey());
    return match ? match[1] : String(new Date().getFullYear());
  });

  ngOnInit(): void {
    this.store.load();
    this.store.loadPresets();
    this.store.fetchCurrencyCatalog(); // names the add-currency picker (falls back to bare codes)
    this.refreshFx(); // loads stored rates and kicks off the client-side live market fetch
  }

  // ---------- income ----------

  addSalary(): void {
    this.store.mutate((month) => month.salaries.push({
      name: 'New income', currency: this.baseCurrency().code, engine: 'generic',
      components: [{label: 'Basic salary', amount: 0, taxable: true, basic: true, varAuto: false}],
      deductions: [], variables: [],
    }));
  }

  removeSalary(index: number): void {
    this.store.mutate((month) => month.salaries.splice(index, 1));
  }

  editSalary(index: number): void {
    this.editingSalaryIndex.set(index);
  }

  applySalary(salary: Salary): void {
    const index = this.editingSalaryIndex();
    if (index !== null) {
      this.store.mutate((month) => month.salaries[index] = salary);
    }
    this.editingSalaryIndex.set(null);
  }

  closeSalaryDialog(): void {
    this.editingSalaryIndex.set(null);
  }

  saveSalaryPreset(preset: {name: string; salary: Salary}): void {
    this.store.savePreset(preset.name, preset.salary);
  }

  deleteSalaryPreset(uuid: string): void {
    this.store.deletePreset(uuid);
  }

  editedSalary(): Salary | null {
    const index = this.editingSalaryIndex();
    return index === null ? null : this.month().salaries[index] ?? null;
  }

  salaryNet(salary: Salary): number {
    return this.computed().salaryNet[salary.name] ?? 0;
  }

  /** The gross→deductions→net breakdown for a salary — aligned by index (income order). */
  salaryBreakdown(index: number) {
    return this.computed().salaryBreakdown[index] ?? null;
  }

  /**
   * The pay components to itemize above a salary's gross subtotal — but only for a salary with 2+
   * components. A single-component salary has component === gross, so listing it is redundant (the
   * prototype skips it too, rendering per-component rows only when comps.length > 1). Each amount is
   * in the salary's own currency, display-only; editing happens in the salary dialog.
   */
  salaryComponents(salary: Salary): PayComponent[] {
    return salary.components.length > 1 ? salary.components : [];
  }

  /**
   * The "how this deduction is computed" note for the deduction at breakdown position `index`,
   * mirroring the prototype's dedNote but i18n'd: returns the translate key + interpolation params
   * the template feeds the pipe. Sourced from the deduction CONFIG (salary.deductions[index]) joined
   * to the breakdown's deductions[index] by position. The join is by index because the engine emits
   * lines in ordinal order (SalaryEngine sorts deductions by ordinal) and the config list the UI
   * holds is itself ordinal-ordered (BudgetMapper rebuilds it via ordered(..., getOrdinal)); both
   * derive from the same array positions, so they line up. Returns null when no config row lines up
   * (length mismatch) so a stray line renders no note rather than a wrong one.
   */
  deductionNote(salary: Salary,
                index: number): DeductionNote | null {
    const deduction = salary.deductions[index];
    if (!deduction) {
      return null;
    }

    if (deduction.type === DeductionType.Brackets) {
      const count = deduction.brackets?.length ?? 0;
      return {key: count === 1 ? 'budget.income.note.bracketsOne' : 'budget.income.note.brackets', params: {n: count}};
    }

    if (deduction.type === DeductionType.Formula) {
      return {key: 'budget.income.note.formula', params: {}};
    }

    if (deduction.type === DeductionType.Fixed) {
      return {key: 'budget.income.note.fixed', params: {}};
    }

    // Pct: "{rate}% of {base}", with a ", capped" suffix when a cap is set.
    const base = this.deductionBaseLabel(deduction);
    return {
      key: deduction.cap != null ? 'budget.income.note.pctCapped' : 'budget.income.note.pct',
      params: {rate: deduction.rate ?? 0, base},
    };
  }

  /**
   * The base a percentage deduction applies to, as a short already-translated word for interpolation
   * into the pct note: gross/basic/taxable/annual, or the named variable for a var-based deduction.
   * The translate pipe can't be nested inside another translate call, so resolve this leaf here.
   */
  private deductionBaseLabel(deduction: Deduction): string {
    if (deduction.base === DeductionBase.Var) {
      return deduction.baseVar ?? this.translate.instant('budget.income.base.var');
    }

    const base = deduction.base ?? DeductionBase.Gross;
    return this.translate.instant(`budget.income.base.${base.split('.').pop()}`);
  }

  salaryBasicAmount(salary: Salary): number {
    const basic = salary.components.find((component) => component.basic) ?? salary.components[0];
    return basic ? basic.amount : 0;
  }

  setSalaryName(index: number, name: string): void {
    this.store.mutate((month) => month.salaries[index].name = name);
  }

  setSalaryBasic(index: number, amount: number): void {
    this.store.mutate((month) => {
      const components = month.salaries[index].components;
      const basic = components.find((component) => component.basic) ?? components[0];
      if (basic) {
        basic.amount = amount;
      }
    });
  }

  setSalaryCurrency(index: number, currency: string): void {
    this.store.mutate((month) => month.salaries[index].currency = currency);
  }

  // ---------- expenses ----------

  addExpense(): void {
    this.store.mutate((month) => month.expenses.push({label: 'New expense', amt: 0, cur: this.baseCurrency().code}));
  }

  removeExpense(index: number): void {
    this.store.mutate((month) => month.expenses.splice(index, 1));
  }

  isTithe(expense: Expense): boolean {
    return expense.auto === 'tithe';
  }

  setExpense(index: number, field: 'label' | 'amt' | 'cur', value: string): void {
    this.store.mutate((month) => {
      const expense = month.expenses[index];
      if (field === 'amt') {
        expense.amt = Number(value) || 0;
      } else if (field === 'cur') {
        expense.cur = value;
      } else {
        expense.label = value;
      }
    });
  }

  // ---------- goals ----------

  addGoal(): void {
    this.store.mutate((month) => month.goals.push({
      label: 'New goal', amt: 0, cur: this.baseCurrency().code, target: {type: GoalTargetType.Open},
      savings: true, wd: 0, closed: false,
    }));
  }

  /** The progress row for a goal — aligned by index (the backend builds it in goal order). */
  goalProgress(index: number) {
    return this.computed().goalProgress[index] ?? null;
  }

  /** A goal still holding funds can't be removed; the balance must be withdrawn first. */
  canRemoveGoal(index: number): boolean {
    const progress = this.goalProgress(index);
    return !progress || progress.balance <= 0;
  }

  removeGoal(index: number): void {
    if (!this.canRemoveGoal(index)) {
      return;
    }

    this.store.mutate((month) => month.goals.splice(index, 1));
  }

  editGoal(index: number): void {
    this.editingGoalIndex.set(index);
  }

  applyGoal(goal: Goal): void {
    const index = this.editingGoalIndex();
    if (index !== null) {
      this.store.mutate((month) => month.goals[index] = goal);
    }
    this.editingGoalIndex.set(null);
  }

  closeGoalDialog(): void {
    this.editingGoalIndex.set(null);
  }

  editedGoal(): Goal | null {
    const index = this.editingGoalIndex();
    return index === null ? null : this.month().goals[index] ?? null;
  }

  /** The balance the goal being edited currently holds (its own currency), bounding withdrawals. */
  editedGoalBalance(): number {
    const index = this.editingGoalIndex();
    return index === null ? 0 : this.goalProgress(index)?.balance ?? 0;
  }

  setGoal(index: number, field: 'label' | 'amt' | 'cur', value: string): void {
    this.store.mutate((month) => {
      const goal = month.goals[index];
      if (field === 'amt') {
        goal.amt = Number(value) || 0;
      } else if (field === 'cur') {
        goal.cur = value;
      } else {
        goal.label = value;
      }
    });
  }

  goalTargetLabel(goal: Goal): string {
    const target = goal.target;
    if (target.type === GoalTargetType.Amount) {
      return `target ${Math.round(target.amount).toLocaleString()}`;
    }

    if (target.type === GoalTargetType.Relative) {
      return `${target.mult}× ${target.base} net`;
    }

    if (target.type === GoalTargetType.Time) {
      return target.due ? `due ${target.due}` : `in ${target.n ?? 0} ${target.unit ?? 'months'}`;
    }

    return 'open goal';
  }

  // ---------- debts ----------

  addDebt(): void {
    this.store.mutate((month) => month.debts.push({
      name: 'New debt', principal: 0, annualRate: 0, monthly: 0, cur: this.baseCurrency().code,
      repriceMode: DebtRepriceMode.Payment, prepay: false, prepayAmt: 0, rateSteps: [],
    }));
  }

  removeDebt(index: number): void {
    this.store.mutate((month) => month.debts.splice(index, 1));
  }

  editDebt(index: number): void {
    this.editingDebtIndex.set(index);
  }

  applyDebt(debt: Debt): void {
    const index = this.editingDebtIndex();
    if (index !== null) {
      this.store.mutate((month) => month.debts[index] = debt);
    }
    this.editingDebtIndex.set(null);
  }

  closeDebtDialog(): void {
    this.editingDebtIndex.set(null);
  }

  editedDebt(): Debt | null {
    const index = this.editingDebtIndex();
    return index === null ? null : this.month().debts[index] ?? null;
  }

  setDebt(index: number, field: keyof Debt, value: string): void {
    this.store.mutate((month) => {
      const debt = month.debts[index];
      const numeric: (keyof Debt)[] = ['principal', 'annualRate', 'monthly', 'termMonths'];
      if (numeric.includes(field)) {
        (debt[field] as unknown as number) = Number(value) || 0;
      } else {
        (debt[field] as unknown as string) = value;
      }
    });
  }

  debtProjection(debt: Debt): DebtProjection | undefined {
    return this.computed().debts.find((projection) => projection.name === debt.name);
  }

  /**
   * A short rate summary for a debt: the annual rate, then each scheduled rate step as
   * "→ {rate}% after {n}y", mirroring the prototype's debtRateSummary. The "after {{n}}y" fragment
   * is resolved through the translate service so it stays i18n-friendly (the rate numbers and the
   * "%" / "→" separators are plain text). Steps are filtered to those with a positive afterYears
   * and ordered by it, matching the simulator's reading order.
   */
  debtRateSummary(debt: Debt): string {
    const steps = (debt.rateSteps ?? [])
      .filter((step) => step.afterYears > 0)
      .sort((a, b) => a.afterYears - b.afterYears);
    return steps.reduce(
      (summary, step) => `${summary} → ${step.rate}% ${this.translate.instant('budget.prepayYear.afterYears', {n: step.afterYears})}`,
      `${debt.annualRate}%`);
  }

  /** The working debt that an annual-prepayment entry refers to, matched by name (the backend join key). */
  private prepayDebt(name: string): Debt | undefined {
    return this.month().debts.find((debt) => debt.name === name);
  }

  /** The matched debt's principal for an annual-prepayment entry's sub-label (0 if the debt is gone). */
  prepayPrincipal(name: string): number {
    return this.prepayDebt(name)?.principal ?? 0;
  }

  /**
   * The currency to format an annual-prepayment entry's principal in — the matched debt's own
   * currency, so the money pipe shows the right symbol; falls back to the entry's currency.
   */
  prepayPrincipalCurrency(name: string,
                          fallback: string): string {
    return this.prepayDebt(name)?.cur ?? fallback;
  }

  /** A short rate summary for an annual-prepayment entry, via the matched debt (empty if it is gone). */
  prepayRateSummary(name: string): string {
    const debt = this.prepayDebt(name);
    return debt ? this.debtRateSummary(debt) : '';
  }

  /** Total annual principal prepayment across all flagged debts, in base currency (backend figures). */
  prepayYearTotalBase(): number {
    return this.computed().prepayYear.reduce((total, entry) => total + entry.amountBase, 0);
  }

  /** The debt's prepayment currency for the inline sub-row toggle (defaults to the debt's own currency). */
  prepayCurrencyOf(debt: Debt): string {
    return debt.prepayCur ?? debt.cur;
  }

  /** Set a debt's inline prepayment amount through the store (mutate-based; backend recomputes). */
  setDebtPrepayAmount(index: number,
                      value: number): void {
    this.store.setDebtPrepayAmount(index, value);
  }

  /** Set a debt's inline prepayment currency through the store (mutate-based; backend recomputes). */
  setDebtPrepayCurrency(index: number,
                        code: string): void {
    this.store.setDebtPrepayCurrency(index, code);
  }

  debtMonthsLabel(debt: Debt): string {
    const projection = this.debtProjection(debt);
    return projection ? this.formatMonths(projection.months) : '—';
  }

  /** When a debt has an annual prepayment, the months and interest it saves vs. the baseline. */
  debtPrepaySaved(debt: Debt): {payoff: string; monthsSaved: number; interestSaved: number} | null {
    const projection = this.debtProjection(debt);
    if (!debt.prepay || !projection || projection.months === NEVER_AMORTIZES) {
      return null;
    }
    const monthsSaved = projection.months - projection.prepayMonths;
    const interestSaved = projection.totalInterest - projection.prepayInterest;
    if (monthsSaved <= 0 && interestSaved <= 0) {
      return null;
    }
    return {payoff: this.formatMonths(projection.prepayMonths), monthsSaved, interestSaved};
  }

  private formatMonths(months: number): string {
    if (months === NEVER_AMORTIZES) {
      return 'never amortizes';
    }
    const years = Math.floor(months / 12);
    const rest = months % 12;
    return years > 0 ? `${years}y ${rest}m` : `${rest}m`;
  }

  // ---------- currencies ----------

  /**
   * Currencies the market feed has a rate for that aren't already in the month, sorted by code —
   * the options offered by the add-currency dropdown. Empty until a market fetch lands (or when
   * everything available is already added), which disables the control.
   */
  addableCurrencies(): string[] {
    const present = new Set(this.month().cur.map((currency) => currency.code));
    return Object.keys(this.store.marketRates())
      .filter((code) => !present.has(code))
      .sort();
  }

  /**
   * The add-currency dropdown options: each addable code paired with a "CODE — Name" label drawn
   * from the catalog when it's loaded, falling back to the bare code (label === code) when the
   * catalog fetch failed or hasn't landed.
   */
  addableCurrencyOptions(): {code: string; label: string}[] {
    const names = this.store.currencyNames();
    return this.addableCurrencies().map((code) => {
      const name = names[code];
      return {code, label: name ? `${code} — ${name}` : code};
    });
  }

  /** A best-effort symbol for a code: the known glyph if we have one, else the code itself. */
  private symbolFor(code: string): string {
    return CURRENCY_SYMBOLS[code] ?? code;
  }

  /** Append a market currency (with its symbol) and seed its stored rate from the market quote. */
  addCurrency(code: string): void {
    if (!code) {
      return; // placeholder option
    }

    const rate = this.store.marketRates()[code];
    if (rate === undefined) {
      return; // no market rate for this code — nothing to seed
    }

    this.store.mutate((month) => month.cur.push({code, sym: this.symbolFor(code)}));
    this.store.setFxRate(this.baseCurrency().code, code, rate);
  }

  /**
   * A currency can't be removed while it's referenced (any salary/expense/goal/debt currency, or a
   * debt's prepayment currency) or while it's the base (first) currency.
   */
  currencyInUse(code: string): boolean {
    if (code === this.baseCurrency().code) {
      return true;
    }

    const month = this.month();
    return month.salaries.some((salary) => salary.currency === code)
      || month.expenses.some((expense) => expense.cur === code)
      || month.goals.some((goal) => goal.cur === code)
      || month.debts.some((debt) => debt.cur === code || debt.prepayCur === code);
  }

  /** The remove control is enabled only for an unused, non-last currency. */
  canRemoveCurrency(index: number): boolean {
    if (this.month().cur.length <= 1) {
      return false; // always keep a base currency
    }

    return !this.currencyInUse(this.month().cur[index].code);
  }

  removeCurrency(index: number): void {
    if (!this.canRemoveCurrency(index)) {
      return;
    }

    this.store.mutate((month) => month.cur.splice(index, 1));

    if (index === 0) {
      this.refreshFx(); // the base changed
    }
  }

  /** Move a currency one slot up/down (keyboard/button fallback); delegates to reorderCurrency. */
  moveCurrency(index: number,
               delta: number): void {
    this.reorderCurrency(index, index + delta);
  }

  /**
   * Move the currency at `from` to slot `to` (the drag-and-drop and up/down primitive). Reaching
   * slot 0 makes it the base, so rates are refreshed against the new base — same as the prototype's
   * reorderCur (and the existing setCurrencyCode/removeCurrency base-change handling).
   */
  reorderCurrency(from: number,
                  to: number): void {
    const length = this.month().cur.length;
    if (from === to || from < 0 || to < 0 || from >= length || to >= length) {
      return;
    }

    this.store.mutate((month) => {
      const [moved] = month.cur.splice(from, 1);
      month.cur.splice(to, 0, moved);
    });

    if (from === 0 || to === 0) {
      this.refreshFx();
    }
  }

  /** Begin dragging a currency row (records the source index; sets the move drag effect). */
  onCurrencyDragStart(index: number,
                      event: DragEvent): void {
    this.draggingCurrency.set(index);
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
      event.dataTransfer.setData('text/plain', String(index));
    }
  }

  /** Hovering a row while dragging marks it the drop target (and allows the drop). */
  onCurrencyDragOver(index: number,
                     event: DragEvent): void {
    if (this.draggingCurrency() === null) {
      return;
    }

    event.preventDefault();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }

    this.dropTargetCurrency.set(index);
  }

  /** Dropping on a row reorders the dragged currency into that slot, then clears the drag state. */
  onCurrencyDrop(index: number,
                 event: DragEvent): void {
    event.preventDefault();
    const from = this.draggingCurrency();
    this.clearCurrencyDrag();
    if (from !== null) {
      this.reorderCurrency(from, index);
    }
  }

  /** Clear the drag state (drag end or after a drop), removing the .dragging/.droptarget feedback. */
  clearCurrencyDrag(): void {
    this.draggingCurrency.set(null);
    this.dropTargetCurrency.set(null);
  }

  setCurrencyCode(index: number,
                  code: string): void {
    this.store.mutate((month) => month.cur[index].code = code);

    if (index === 0) {
      this.refreshFx(); // the base code changed
    }
  }

  setCurrencySymbol(index: number,
                    symbol: string): void {
    this.store.mutate((month) => month.cur[index].sym = symbol);
  }

  // ---------- fx ----------

  /** Reload stored rates against the current base and re-fetch live market quotes. */
  refreshFx(): void {
    const base = this.baseCurrency().code;
    this.store.refreshFx(base);
    this.store.fetchMarketRates(base);
  }

  /**
   * One editable row per non-base currency: the stored rate (units per one base, defaulting to its
   * market quote then 0 when unset), the reciprocal for the "1 quote = N base" caption, slider
   * bounds, and the live market rate if one was fetched.
   */
  fxEntries(): FxRow[] {
    const base = this.baseCurrency().code;
    const stored = this.store.fxRates();
    const market = this.store.marketRates();
    return this.month().cur.slice(1).map((currency) => {
      const rate = stored[currency.code] ?? market[currency.code] ?? 0;
      const step = this.sliderStep(rate);
      return {
        code: currency.code,
        sym: currency.sym,
        rate,
        reciprocal: rate > 0 ? 1 / rate : 0,
        step,
        min: rate > 0 ? Math.max(step, Math.floor(rate * 0.25 / step) * step) : step,
        max: rate > 0 ? rate * 4 : step * 100,
        market: market[currency.code] ?? null,
      };
    });
  }

  /** Persist a slider/number edit for a quote currency (ignores non-positive input). */
  setRate(quote: string,
          value: string): void {
    const rate = Number(value);
    if (!isFinite(rate) || rate <= 0) {
      return;
    }

    this.store.setFxRate(this.baseCurrency().code, quote, rate);
  }

  /** Apply the fetched market rate for a quote, persisting it. */
  useMarket(quote: string): void {
    this.store.useMarketRate(this.baseCurrency().code, quote);
  }

  /** Display-only rate formatting (variable precision); not a money figure, so not the money pipe. */
  formatRate(value: number): string {
    if (!isFinite(value) || value <= 0) {
      return '0';
    }

    if (value >= 100) {
      return value.toFixed(1);
    }

    if (value >= 1) {
      return value.toFixed(3);
    }

    return value.toPrecision(3);
  }

  /** Slider granularity tuned to the rate's magnitude (mirrors the prototype's sliderStep). */
  private sliderStep(rate: number): number {
    if (!isFinite(rate) || rate <= 0) {
      return 0.001;
    }

    if (rate >= 100) {
      return 0.1;
    }

    if (rate >= 1) {
      return 0.001;
    }

    return Math.pow(10, Math.floor(Math.log10(rate)) - 2);
  }

  // ---------- io ----------

  exportJson(): void {
    const payload = {
      app: 'tokyo-budget', version: 1, month: this.store.monthKey(),
      exportedAt: new Date().toISOString(), data: this.month(),
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], {type: 'application/json'});
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `washa-budget-${this.store.monthKey()}.json`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  importJson(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    file.text().then((text) => {
      try {
        const parsed = JSON.parse(text);
        const data: BudgetMonth = parsed?.data ?? parsed;
        if (!data || !Array.isArray(data.salaries)) {
          throw new Error('not a budget export');
        }
        this.store.setMonth(data);
        this.importError.set(null);
      } catch {
        this.importError.set('That file is not a valid budget export.');
      }
    });
    input.value = '';
  }

  printMonth(): void {
    window.print();
  }
}
