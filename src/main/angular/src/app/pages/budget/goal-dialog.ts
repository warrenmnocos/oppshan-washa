import {Component, computed, input, linkedSignal, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency, Goal} from '../../models/budget.models';
import {GoalTargetType} from '../../models/goal-target-type';
import {MoneyPipe} from '../../services/money.pipe';
import {CurrencyPicker} from './currency-picker';

/** How a TIME target's deadline is entered: a fixed due date, or a count of units from now. */
type TimeMode = 'date' | 'period';

/**
 * Edits one goal on a working copy: name, currency, monthly contribution, target type (open, a fixed
 * amount, a multiple of net income, or by a target time), the "treat as savings" flag, a withdrawal,
 * and closing the goal. Target types and the relative base mirror what the backend persists
 * (GoalTargetType). A TIME target's deadline is either a due date or a period from now; the mode is
 * local UI state inferred from which fields the draft carries. Withdrawals are clamped to the balance
 * the goal currently holds, and closing stamps the current month key. Everything happens on a cloned
 * draft, so Save emits `saved(goal)` while Cancel emits `cancelled()` and discards it.
 */
@Component({
  selector: 'app-goal-dialog',
  standalone: true,
  imports: [TranslatePipe, CurrencyPicker],
  templateUrl: './goal-dialog.html',
  styleUrl: './goal-dialog.scss',
})
export class GoalDialog {

  /** The goal to edit; cloned into the draft so edits stay local until Save. */
  readonly goal = input.required<Goal>();
  /** The currencies offered by the currency picker. */
  readonly currencies = input.required<Currency[]>();
  /** True when editing an existing goal; gates Close goal and the withdrawals editor. */
  readonly editing = input<boolean>(true);
  /** The current month key (e.g. 2026-06); stamped onto closedKey when the goal is closed. */
  readonly currentMonthKey = input<string>('');
  /** The balance this goal currently holds in its own currency; bounds the withdrawal. */
  readonly balance = input<number>(0);
  /**
   * A display-only "≈" cross-rate caption for the balance (e.g. "≈ ₱Y"), shown in the withdrawal hint
   * beside the balance. Mirrors the prototype's "Balance ¥X ≈ ₱Y ...".
   */
  readonly balanceConv = input<string>('');
  /** Emits the committed goal when the user saves. */
  readonly saved = output<Goal>();
  /** Emits when the user cancels; the draft is discarded. */
  readonly cancelled = output<void>();

  /** A deep clone of the input goal that reseeds whenever it changes; edits stay here until Save. */
  readonly draft = linkedSignal<Goal>(() => structuredClone(this.goal()));

  /** The TIME deadline mode, seeded from the goal: a period (n + unit) when it carries one, else a date. */
  readonly timeMode = linkedSignal<TimeMode>(() => {
    const target = this.goal().target;
    return target.type === GoalTargetType.Time && target.n != null && !target.due ? 'period' : 'date';
  });

  /** Exposed for template enum comparisons. */
  protected readonly GoalTargetType = GoalTargetType;

  /** The balance floored at 0, so a negative balance can't push the withdrawal cap below zero. */
  readonly availableBalance = computed(() => Math.max(0, this.balance()));

  /** The currency record for the draft's code, so the money pipe renders the symbol (not the code). */
  readonly draftCurrency = computed(() =>
      this.currencies().find((currency) => currency.code === this.draft().cur) ?? this.draft().cur);

  /** Formats amounts for the withdrawal hint; a local pipe instance since it's used outside a template. */
  private readonly money = new MoneyPipe();

  /** The available balance formatted in the goal's currency, for the withdrawal-hint line. */
  readonly balanceLabel = computed(() => this.money.transform(this.availableBalance(), this.draftCurrency()));

  /** Update the draft goal's name. */
  setLabel(label: string): void {
    this.patch((goal) => goal.label = label);
  }

  /** Update the draft goal's currency code. */
  setCurrency(currency: string): void {
    this.patch((goal) => goal.cur = currency);
  }

  /** Set the monthly contribution, coercing a blank or NaN entry to 0. */
  setAmount(amount: number): void {
    this.patch((goal) => goal.amt = amount || 0);
  }

  /** Toggle whether the goal counts as savings. */
  setSavings(savings: boolean): void {
    this.patch((goal) => goal.savings = savings);
  }

  /** Set the withdrawal, clamped to the available balance. */
  setWithdrawal(withdrawal: number): void {
    this.patch((goal) => goal.wd = this.clampWithdrawal(withdrawal || 0));
  }

  /** Pull all held funds out: set the withdrawal to the full available balance. */
  withdrawAll(): void {
    this.patch((goal) => goal.wd = this.availableBalance());
  }

  /** Switch the target type, carrying over the matching fields or seeding defaults for the new type. */
  setTargetType(type: GoalTargetType): void {
    this.patch((goal) => {
      const current = goal.target;
      if (type === GoalTargetType.Amount) {
        goal.target = {type: GoalTargetType.Amount, amount: current.type === GoalTargetType.Amount ? current.amount : 0};
      } else if (type === GoalTargetType.Relative) {
        goal.target = {
          type: GoalTargetType.Relative,
          base: current.type === GoalTargetType.Relative ? current.base : 'all',
          mult: current.type === GoalTargetType.Relative ? current.mult : 6,
        };
      } else if (type === GoalTargetType.Time) {
        goal.target = current.type === GoalTargetType.Time ? current : {type: GoalTargetType.Time};
      } else {
        goal.target = {type: GoalTargetType.Open};
      }
    });
  }

  /** Set the fixed target amount, if the target is an AMOUNT type. */
  setTargetAmount(amount: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Amount) {
        goal.target.amount = amount || 0;
      }
    });
  }

  /** Set the income multiple, if the target is a RELATIVE type. */
  setTargetMult(mult: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Relative) {
        goal.target.mult = mult || 0;
      }
    });
  }

  /** Switch the TIME deadline between a due date and a period, clearing the other mode's fields. */
  setTimeMode(mode: TimeMode): void {
    this.timeMode.set(mode);
    this.patch((goal) => {
      if (goal.target.type !== GoalTargetType.Time) {
        return;
      }

      if (mode === 'date') {
        delete goal.target.n;
        delete goal.target.unit;
      } else {
        delete goal.target.due;
        goal.target.unit = goal.target.unit ?? 'months';
      }
    });
  }

  /** Set the due date, if the target is a TIME type. */
  setTargetDue(due: string): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.due = due;
      }
    });
  }

  /** Set the period count (n), if the target is a TIME type. */
  setTargetPeriodCount(count: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.n = count || 0;
      }
    });
  }

  /** Set the period unit (e.g. months), if the target is a TIME type. */
  setTargetUnit(unit: string): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.unit = unit;
      }
    });
  }

  /** Close the goal and save immediately: it stops contributing but keeps its balance and stays in the activity log. */
  closeGoal(): void {
    this.patch((goal) => {
      goal.closed = true;
      goal.closedKey = this.currentMonthKey();
    });
    this.save();
  }

  /** Commit the draft: trim the name (falling back to "Goal"), re-clamp the withdrawal, and emit `saved`. */
  save(): void {
    const goal = structuredClone(this.draft());
    goal.label = goal.label.trim() || 'Goal';
    goal.wd = this.clampWithdrawal(goal.wd || 0);
    this.saved.emit(goal);
  }

  /** Discard the draft and emit `cancelled`. */
  cancel(): void {
    this.cancelled.emit();
  }

  /** Cancel when the click lands on the backdrop itself, not a child of the dialog card. */
  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cancel();
    }
  }

  /** Clamp a withdrawal to the range [0, available balance]. */
  private clampWithdrawal(withdrawal: number): number {
    return Math.max(0, Math.min(withdrawal, this.availableBalance()));
  }

  /** Apply a mutation to a fresh clone of the draft and set it back, so signal consumers see a new reference. */
  private patch(change: (goal: Goal) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
