/**
 * TS mirrors of the backend budget DTOs (the `*View` records under `BudgetMonthView` and
 * `ComputedView`). These are plain data: no hydration, no methods. Field names match the Java records
 * 1:1, including the terse JSON names the mockup's export uses (`amt`, `cur`, `var`, `wd`,
 * `afterYears`, `sym`), so the same JSON round-trips over the wire and through the export/import file.
 * Money is a plain `number` here where the backend keeps `BigDecimal`.
 */

import {BracketOp} from './bracket-op';
import {BracketType} from './bracket-type';
import {DebtRepriceMode} from './debt-reprice-mode';
import {DeductionType} from './deduction-type';
import {GoalTargetType} from './goal-target-type';
import {VariableType} from './variable-type';

/** One currency in the household's list. Mirrors the backend `CurrencyView`. */
export interface Currency {
  code: string;
  /** Display glyph for `code` (e.g. `¥`, `₱`). JSON name `sym`, matching the record. */
  sym: string;
}

/**
 * One row of a bracket table: a tiered/progressive rule where rows are additive. The row compares a
 * left-hand scope value against `val` using `op`; when the test holds it contributes per `type`.
 * Every field is optional because a half-built row is still a valid draft. Mirrors `BracketView`.
 */
export interface Bracket {
  /** Scope variable the row tests (its left-hand value); defaults to `taxable` when unset. */
  var?: string;
  /** Comparison against `val`. See {@link BracketOp}. */
  op?: BracketOp;
  /** The threshold `var` is tested against. */
  val?: number;
  /** How the row contributes when its test holds (fixed / formula / % of gross / % of basic). See {@link BracketType}. */
  type?: BracketType;
  /** Percentage or flat figure the contribution uses, per `type`. */
  rate?: number;
  /** Formula string, evaluated when `type` is the formula kind. */
  expr?: string;
}

/** One gross-pay line of a salary. Mirrors `ComponentView`. */
export interface Component {
  label: string;
  amount: number;
  /** Counts this line toward the taxable base. */
  taxable: boolean;
  /** Counts this line toward the "basic pay" figure some deductions use as their base. */
  basic: boolean;
  /** Publishes this line's amount into the formula scope under this name, so a later variable, formula, or bracket can read it. */
  var?: string;
  /** True when `var` is auto-managed rather than user-entered. */
  varAuto: boolean;
}

/**
 * One salary deduction. `type` decides how it computes and which input it reads: a `rate` against
 * `base`/`baseVar` (Pct), a flat `amount` (Fixed), an `expr` (Formula), or the `brackets` table
 * (Brackets). Mirrors `DeductionView`.
 */
export interface Deduction {
  label: string;
  /** Selects the computation and which of the fields below it reads. See {@link DeductionType}. */
  type: DeductionType;
  /** Percentage base when `type` is Pct; values are the `DeductionBase` tokens (gross / basic / taxable / annual / var). */
  base?: string;
  /** Names the scope variable to read when `base` is the `var` kind. */
  baseVar?: string;
  /** Percentage applied to `base`, for Pct. */
  rate?: number;
  /** Clamps the computed result at the top. */
  cap?: number;
  /** Clamps the computed result at the bottom. JSON name `floor` (the record's `floorAmount`). */
  floor?: number;
  /** Flat value, for Fixed. */
  amount?: number;
  /** Formula string, for Formula. */
  expr?: string;
  /** Optional named built-in reference (e.g. a Japan tax preset) carried from the mockup; `type` drives the math, not this. */
  fn?: string;
  /** Lowers the taxable base for later deductions (social-insurance style). */
  pretax: boolean;
  /** Publishes the computed amount into scope under this name, like a component's `var`. */
  var?: string;
  /** True when `var` is auto-managed rather than user-entered. */
  varAuto: boolean;
  /** The bracket table, for Brackets. */
  brackets?: Bracket[];
}

/**
 * One salary custom variable: a named intermediate value computed the same way a deduction is, then
 * published into the formula scope under `var` so later deductions, formulas, and brackets can read
 * it. Its `base`/`baseVar`/`rate`/`cap`/`floor`/`amount`/`expr`/`brackets` fields carry the same
 * meaning as on {@link Deduction} ({@link VariableType} mirrors {@link DeductionType}); it has no `fn`
 * or `pretax`, since a variable isn't itself a tax line. Mirrors `VariableView`.
 */
export interface Variable {
  /** The scope name this value publishes under. Required, since it's the variable's identity. */
  var: string;
  label?: string;
  type: VariableType;
  /** Percentage base; values are the `DeductionBase` tokens. See {@link Deduction.base}. */
  base?: string;
  baseVar?: string;
  rate?: number;
  cap?: number;
  floor?: number;
  amount?: number;
  expr?: string;
  /** True when `var` is auto-managed rather than user-entered. */
  varAuto: boolean;
  brackets?: Bracket[];
}

/**
 * One salary configuration: identity (`name`, `currency`), an engine-template id, and the ordered
 * pay `components`, `deductions`, and custom `variables`. List position is the persisted order, and
 * deductions evaluate in that order. Mirrors `SalaryView`.
 */
export interface Salary {
  name: string;
  currency: string;
  /** Names the salary engine template; the backend defaults it to `generic` when unset. */
  engine: string;
  components: Component[];
  deductions: Deduction[];
  variables: Variable[];
}

/**
 * One saved salary preset. It reuses {@link Salary} as its payload, so a preset carries the same
 * shape a month's income does. Mirrors the backend `SalaryPresetView`.
 */
export interface SalaryPresetView {
  uuid: string;
  name: string;
  /** True for a seeded, built-in preset (which can't be deleted). */
  builtIn: boolean;
  salary: Salary;
}

/** One expense line. Mirrors `ExpenseView`. */
export interface Expense {
  label: string;
  /** Amount. Absent on a derived line (see `auto`), where the value is computed rather than entered. */
  amt?: number;
  /** Currency code. */
  cur: string;
  /** Marks a derived line; `tithe` is the auto-tithe expense (10% of net), which carries no entered `amt`. */
  auto?: string;
}

/**
 * A goal's target, as a discriminated union keyed on {@link GoalTargetType}: `Open` has no target;
 * `Amount` is a fixed `amount`; `Relative` is `mult` times a base figure (`base` names it, and the
 * mockup always uses net); `Time` is a deadline, either an explicit `due` date or a span of `n`
 * `unit`s (days / weeks / months / years) from the goal's start. Mirrors the backend `TargetView`
 * (JSON: `due` = `dueDate`, `n` = `periodCount`).
 */
export type GoalTarget =
  | {type: GoalTargetType.Open}
  | {type: GoalTargetType.Amount; amount: number}
  | {type: GoalTargetType.Relative; base: string; mult: number}
  | {type: GoalTargetType.Time; due?: string; n?: number; unit?: string};

/** One savings or spending goal. Mirrors `GoalView`. */
export interface Goal {
  label: string;
  /** This month's contribution to the goal. */
  amt: number;
  /** Currency code. */
  cur: string;
  target: GoalTarget;
  /** True for a savings-type goal (the ones summed into the running savings balance). */
  savings: boolean;
  /** Withdrawal taken from the goal this month. */
  wd: number;
  closed: boolean;
  /** The `YYYY-MM` month the goal was closed in, so the closure is attributed to that month. */
  closedKey?: string;
}

/** One scheduled rate change on a debt: after `afterYears` years its rate becomes `rate`. Mirrors `RateStepView`. */
export interface RateStep {
  afterYears: number;
  rate: number;
}

/** One debt/loan. `principal`, `annualRate`, and `monthly` drive the amortization. Mirrors `DebtView`. */
export interface Debt {
  name: string;
  principal: number;
  /** Annual interest rate. */
  annualRate: number;
  /** Scheduled monthly payment. */
  monthly: number;
  /** Optional loan term, in months. */
  termMonths?: number;
  /** How the payment or term reacts when the rate changes. See {@link DebtRepriceMode}. */
  repriceMode?: DebtRepriceMode;
  /** Currency code. */
  cur: string;
  /** Flags an annual principal prepayment. */
  prepay: boolean;
  /** The prepayment amount (used when `prepay`). */
  prepayAmt: number;
  /** Prepayment currency, which may differ from the debt's own `cur`. */
  prepayCur?: string;
  /** Scheduled rate changes over the loan's life. */
  rateSteps: RateStep[];
}

/**
 * One month's full budget input: its salaries, expenses, goals, debts, and the household currency
 * list. This is the shape that round-trips through JSON export/import. Mirrors the persisted
 * `BudgetMonthView` (the backend's live-recompute variant also carries working fx rates; this subset
 * leaves them out).
 */
export interface BudgetMonth {
  salaries: Salary[];
  expenses: Expense[];
  goals: Goal[];
  debts: Debt[];
  /** The household's currency list. */
  cur: Currency[];
}

/**
 * Payoff projection for one debt. `months`/`totalInterest` are the baseline (no extra prepayment);
 * `prepayMonths`/`prepayInterest` re-run the simulation with the debt's annual principal prepayment
 * (they equal the baseline when prepayment is off). The gap between the two pairs is the time and
 * interest the prepayment saves. Mirrors the backend `DebtProjection`.
 */
export interface DebtProjection {
  name: string;
  /** Baseline months to payoff, or {@link NEVER_AMORTIZES} when the payment never covers interest. */
  months: number;
  totalInterest: number;
  /** Months to payoff with prepayment, or {@link NEVER_AMORTIZES}. */
  prepayMonths: number;
  prepayInterest: number;
}

/**
 * The accumulated standing of one goal, in base currency. `balance` is cumulative contributions less
 * withdrawals, floored at zero. `target` and `pct` are null for an open goal; a Time goal also has a
 * null `target` but a `pct` that tracks elapsed time toward its due date. Mirrors the backend
 * `GoalProgress`.
 */
export interface GoalProgress {
  label: string;
  currency: string;
  /** Cumulative net contributions to date, floored at zero. */
  balance: number;
  /** The goal's target figure, or null for an open or time-based goal. */
  target: number | null;
  /** Progress in the range 0 to 1, or null for an open goal. */
  pct: number | null;
  savings: boolean;
  /** True once a targeted goal reaches its target, or a Time goal's due date has passed. */
  complete: boolean;
  closed: boolean;
}

/**
 * One row of this month's goal activity: a withdrawal taken, or a goal closed this month. `kind`
 * discriminates the two; `amount` (base currency) is the withdrawn amount for a withdrawal and the
 * remaining balance for a closure. Mirrors the backend `Activity`.
 */
export interface Activity {
  label: string;
  currency: string;
  amount: number;
  /** `withdrawal` = a withdrawal this month; `closed` = the goal was closed this month. */
  kind: 'withdrawal' | 'closed';
}

/**
 * One prepayment-flagged debt's principal prepayment accumulated across this year's saved months.
 * `amount` is in the debt's own currency for direct display; `amountBase` is the same total reduced
 * to base currency, so a set of debts in different currencies can be summed. Mirrors the backend
 * `PrepayYear`.
 */
export interface PrepayYear {
  name: string;
  currency: string;
  amount: number;
  /** `amount` reduced to base currency, so mixed-currency prepayments can be summed. */
  amountBase: number;
}

/**
 * The full deduction breakdown of one salary, in the salary's own currency: the gross subtotal, each
 * deduction line (a positive amount, shown as a negative), and the resulting net. Lines are in the
 * deductions' evaluation order, and `net` equals this salary's entry in `Computed.salaryNet` before
 * conversion to base. Mirrors the backend `SalaryBreakdown`.
 */
export interface SalaryBreakdown {
  name: string;
  currency: string;
  gross: number;
  deductions: {label: string; amount: number}[];
  net: number;
}

/**
 * The backend's live computed figures for a month, all in base currency unless noted. `moneyOut` sums
 * every allocation (expenses including the derived tithe, all goal contributions, and debt as
 * amortization plus prepayment), so `free` is what's left once the month is fully planned. The
 * category totals below break `moneyOut` down. Mirrors `ComputedView`.
 */
export interface Computed {
  /** Total net income. */
  moneyIn: number;
  /** Every allocation summed (expenses + tithe + goals + debt). */
  moneyOut: number;
  /** Cash left after `moneyOut`: the fully-planned month's remainder. */
  free: number;
  /** The auto-tithe expense (10% of net). */
  tithe: number;
  /** Expenses other than the tithe. */
  otherExpenses: number;
  /** Debt allocation (amortization plus prepayment). */
  debt: number;
  /** Contributions to savings-flagged goals. */
  savingsGoals: number;
  /** Contributions to non-savings goals. */
  nonSavingsGoals: number;
  /** Share of net income saved or left free. */
  savingsRate: number;
  /** Salary name → net amount, each in that salary's own currency (not base). */
  salaryNet: Record<string, number>;
  salaryBreakdown: SalaryBreakdown[];
  debts: DebtProjection[];
  goalProgress: GoalProgress[];
  /** Running savings total held across every non-closed savings-flagged goal. */
  savingsBalance: number;
  activity: Activity[];
  prepayYear: PrepayYear[];
}

/**
 * The signed-in household person: identity (`uuid`) plus display details. `firstName`, `lastName`,
 * and `photoUrl` may be null; `displayName` is derived server-side and is always a non-empty label.
 * Mirrors the backend `UserAccountView`.
 */
export interface Me {
  uuid: string;
  firstName: string | null;
  lastName: string | null;
  /** Derived server-side; a non-empty label even when the name parts are null. */
  displayName: string;
  email: string;
  photoUrl: string | null;
}

/**
 * Sentinel `months` value the backend's debt simulator reports for a loan that never amortizes (its
 * payment can't cover the interest). It's `Integer.MAX_VALUE` (2^31 - 1), carried verbatim in a
 * {@link DebtProjection}'s `months`/`prepayMonths` in place of a real payoff count.
 */
export const NEVER_AMORTIZES = 2147483647;
