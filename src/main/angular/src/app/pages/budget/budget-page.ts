import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {BudgetStore} from '../../services/budget-store';
import {MoneyPipe} from '../../services/money.pipe';
import {ChartSlice, MoneyChart} from './money-chart';
import {SalaryDialog} from './salary-dialog';
import {GoalDialog} from './goal-dialog';
import {DebtDialog} from './debt-dialog';
import {BudgetMonth, Debt, DebtProjection, Expense, Goal, NEVER_AMORTIZES, Salary} from '../../models/budget.models';
import {DebtRepriceMode} from '../../models/debt-reprice-mode';
import {GoalTargetType} from '../../models/goal-target-type';

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

  readonly importError = signal<string | null>(null);
  readonly editingSalaryIndex = signal<number | null>(null);
  readonly editingGoalIndex = signal<number | null>(null);
  readonly editingDebtIndex = signal<number | null>(null);

  readonly month = this.store.month;
  readonly computed = this.store.computed;

  readonly baseCurrency = computed(() => this.month().cur[0] ?? {code: 'JPY', sym: '¥'});

  /** The currency record for a code, so the money pipe shows its symbol; falls back to the code. */
  currencyFor(code: string) {
    return this.month().cur.find((currency) => currency.code === code) ?? code;
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

  ngOnInit(): void {
    this.store.load();
    this.store.loadPresets();
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

  addCurrency(): void {
    this.store.mutate((month) => month.cur.push({code: 'USD', sym: '$'}));
  }

  removeCurrency(index: number): void {
    if (this.month().cur.length <= 1) {
      return; // always keep a base currency
    }

    this.store.mutate((month) => month.cur.splice(index, 1));

    if (index === 0) {
      this.refreshFx(); // the base changed
    }
  }

  /** Move a currency one slot up/down; reaching slot 0 makes it the base, so refresh rates. */
  moveCurrency(index: number,
               delta: number): void {
    const target = index + delta;
    if (target < 0 || target >= this.month().cur.length) {
      return;
    }

    this.store.mutate((month) => {
      const [moved] = month.cur.splice(index, 1);
      month.cur.splice(target, 0, moved);
    });

    if (index === 0 || target === 0) {
      this.refreshFx();
    }
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
