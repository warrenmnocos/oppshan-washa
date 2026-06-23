import {Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {BudgetStore} from '../../services/budget-store';
import {BudgetApiService} from '../../services/budget-api.service';
import {MoneyPipe} from '../../services/money.pipe';
import {ChartSlice, MoneyChart} from './money-chart';
import {SalaryDialog} from './salary-dialog';
import {BudgetMonth, Debt, Expense, Goal, NEVER_AMORTIZES, Salary} from '../../models/budget.models';

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

@Component({
  selector: 'app-budget-page',
  standalone: true,
  imports: [FormsModule, RouterLink, MoneyPipe, MoneyChart, SalaryDialog],
  templateUrl: './budget-page.html',
  styleUrl: './budget-page.scss',
})
export class BudgetPage implements OnInit {

  private readonly api = inject(BudgetApiService);
  readonly store = inject(BudgetStore);

  readonly fxRates = signal<Record<string, number>>({});
  readonly importError = signal<string | null>(null);
  readonly editingSalaryIndex = signal<number | null>(null);

  readonly month = this.store.month;
  readonly computed = this.store.computed;

  readonly baseCurrency = computed(() => this.month().cur[0] ?? {code: 'JPY', sym: '¥'});

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
    this.refreshFx();
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

  editedSalary(): Salary | null {
    const index = this.editingSalaryIndex();
    return index === null ? null : this.month().salaries[index] ?? null;
  }

  salaryNet(salary: Salary): number {
    return this.computed().salaryNet[salary.name] ?? 0;
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
      label: 'New goal', amt: 0, cur: this.baseCurrency().code, target: {type: 'open'}, savings: true, wd: 0,
    }));
  }

  removeGoal(index: number): void {
    this.store.mutate((month) => month.goals.splice(index, 1));
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
    switch (goal.target.type) {
      case 'amount':
        return `target ${Math.round(goal.target.amount).toLocaleString()}`;
      case 'relative':
        return `${goal.target.mult}× ${goal.target.base} net`;
      default:
        return 'open goal';
    }
  }

  // ---------- debts ----------

  addDebt(): void {
    this.store.mutate((month) => month.debts.push({
      name: 'New debt', principal: 0, annualRate: 0, monthly: 0, cur: this.baseCurrency().code,
      repriceMode: 'payment', prepay: false, prepayAmt: 0, rateSteps: [],
    }));
  }

  removeDebt(index: number): void {
    this.store.mutate((month) => month.debts.splice(index, 1));
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

  debtProjection(debt: Debt): {months: number; totalInterest: number} | undefined {
    return this.computed().debts.find((projection) => projection.name === debt.name);
  }

  debtMonthsLabel(debt: Debt): string {
    const projection = this.debtProjection(debt);
    if (!projection) {
      return '—';
    }
    if (projection.months === NEVER_AMORTIZES) {
      return 'never amortizes';
    }
    const years = Math.floor(projection.months / 12);
    const months = projection.months % 12;
    return years > 0 ? `${years}y ${months}m` : `${months}m`;
  }

  // ---------- fx ----------

  refreshFx(): void {
    this.api.fx(this.baseCurrency().code).subscribe({next: (rates) => this.fxRates.set(rates)});
  }

  fxEntries(): {code: string; rate: number}[] {
    return Object.entries(this.fxRates()).map(([code, rate]) => ({code, rate}));
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
