// TS models mirroring the backend BudgetMonthView DTO (export-envelope shape). JSON field names
// match the mockup: amt, cur, var, wd, afterYears, sym.

export interface Currency {
  code: string;
  sym: string;
}

export interface Bracket {
  var?: string;
  op?: string;
  val?: number;
  type?: string;
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
  kind: string;
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
  kind: string;
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

export interface Expense {
  label: string;
  amt?: number;
  cur: string;
  auto?: string;
}

export type GoalTarget =
  | {type: 'open'}
  | {type: 'amount'; amount: number}
  | {type: 'relative'; base: string; mult: number};

export interface Goal {
  label: string;
  amt: number;
  cur: string;
  target: GoalTarget;
  savings: boolean;
  wd: number;
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
  repriceMode?: 'payment' | 'term';
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
  debts: DebtProjection[];
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
