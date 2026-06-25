// TS models mirroring the backend BudgetMonthView DTO (export-envelope shape). JSON field names
// match the mockup: amt, cur, var, wd, afterYears, sym.

import {BracketOp} from './bracket-op';
import {BracketType} from './bracket-type';
import {DebtRepriceMode} from './debt-reprice-mode';
import {DeductionType} from './deduction-type';
import {GoalTargetType} from './goal-target-type';
import {VariableType} from './variable-type';

export interface Currency {
  code: string;
  sym: string;
}

export interface Bracket {
  var?: string;
  op?: BracketOp;
  val?: number;
  type?: BracketType;
  rate?: number;
  expr?: string;
}

export interface Component {
  label: string;
  amount: number;
  taxable: boolean;
  basic: boolean;
  var?: string;
  varAuto: boolean;
}

export interface Deduction {
  label: string;
  type: DeductionType;
  base?: string;
  baseVar?: string;
  rate?: number;
  cap?: number;
  floor?: number;
  amount?: number;
  expr?: string;
  fn?: string;
  pretax: boolean;
  var?: string;
  varAuto: boolean;
  brackets?: Bracket[];
}

export interface Variable {
  var: string;
  label?: string;
  type: VariableType;
  base?: string;
  baseVar?: string;
  rate?: number;
  cap?: number;
  floor?: number;
  amount?: number;
  expr?: string;
  varAuto: boolean;
  brackets?: Bracket[];
}

export interface Salary {
  name: string;
  currency: string;
  engine: string;
  components: Component[];
  deductions: Deduction[];
  variables: Variable[];
}

export interface SalaryPresetView {
  uuid: string;
  name: string;
  builtIn: boolean;
  salary: Salary;
}

export interface Expense {
  label: string;
  amt?: number;
  cur: string;
  auto?: string;
}

export type GoalTarget =
  | {type: GoalTargetType.Open}
  | {type: GoalTargetType.Amount; amount: number}
  | {type: GoalTargetType.Relative; base: string; mult: number}
  | {type: GoalTargetType.Time; due?: string; n?: number; unit?: string};

export interface Goal {
  label: string;
  amt: number;
  cur: string;
  target: GoalTarget;
  savings: boolean;
  wd: number;
  closed: boolean;
  closedKey?: string;
}

export interface RateStep {
  afterYears: number;
  rate: number;
}

export interface Debt {
  name: string;
  principal: number;
  annualRate: number;
  monthly: number;
  termMonths?: number;
  repriceMode?: DebtRepriceMode;
  cur: string;
  prepay: boolean;
  prepayAmt: number;
  prepayCur?: string;
  rateSteps: RateStep[];
}

export interface BudgetMonth {
  salaries: Salary[];
  expenses: Expense[];
  goals: Goal[];
  debts: Debt[];
  cur: Currency[];
}

export interface DebtProjection {
  name: string;
  months: number;
  totalInterest: number;
  prepayMonths: number;
  prepayInterest: number;
}

export interface GoalProgress {
  label: string;
  currency: string;
  balance: number;
  target: number | null;
  pct: number | null;
  savings: boolean;
  complete: boolean;
  closed: boolean;
}

/** One row of this month's goal activity: a withdrawal taken, or a goal closed this month. */
export interface Activity {
  label: string;
  currency: string;
  amount: number;
  kind: 'withdrawal' | 'closed';
}

/**
 * One prepayment-flagged debt's principal prepayment accumulated across this year's saved months.
 * `amount` is in the debt's own currency for direct display; `amountBase` is the same total reduced
 * to base currency, so the annual card can sum across debts of different currencies. Mirrors the
 * backend `ComputedView.PrepayYear` record 1:1.
 */
export interface PrepayYear {
  name: string;
  currency: string;
  amount: number;
  amountBase: number;
}

/**
 * The full deduction breakdown of one salary, in the salary's own currency: the gross subtotal,
 * each deduction line (positive amount, rendered as a negative), and the resulting net. The backend
 * builds these in income order; net equals this salary's entry in salaryNet before conversion.
 */
export interface SalaryBreakdown {
  name: string;
  currency: string;
  gross: number;
  deductions: {label: string; amount: number}[];
  net: number;
}

export interface Computed {
  moneyIn: number;
  moneyOut: number;
  free: number;
  tithe: number;
  otherExpenses: number;
  debt: number;
  savingsGoals: number;
  nonSavingsGoals: number;
  savingsRate: number;
  salaryNet: Record<string, number>;
  salaryBreakdown: SalaryBreakdown[];
  debts: DebtProjection[];
  goalProgress: GoalProgress[];
  savingsBalance: number;
  activity: Activity[];
  prepayYear: PrepayYear[];
}

export interface Me {
  uuid: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  email: string;
  photoUrl: string | null;
}

/** months sentinel from the backend DebtSimulator for a non-amortizing loan. */
export const NEVER_AMORTIZES = 2147483647;
