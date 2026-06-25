import {Component, computed, input, linkedSignal, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency, Goal} from '../../models/budget.models';
import {GoalTargetType} from '../../models/goal-target-type';
import {MoneyPipe} from '../../services/money.pipe';
import {CurrencyPicker} from './currency-picker';

/** How a TIME target's deadline is entered: a fixed due date, or a count of units from now. */
type TimeMode = 'date' | 'period';

/**
 * Edits one goal on a working copy: name, currency, monthly contribution, target type (open / a
 * fixed amount / a multiple of net income / by a target time), the "treat as savings" flag, a
 * withdrawal, and closing the goal. Target types and the relative base mirror what the backend
 * persists (GoalTargetType). A TIME target's deadline is either a due date or a period from now;
 * the mode is local UI state inferred from which fields the draft carries. Withdrawals are clamped
 * to the balance the goal currently holds, and closing stamps the current month key. Edits commit
 * only on save; the parent applies the emitted goal through the store.
 */
@Component({
  selector: 'app-goal-dialog',
  standalone: true,
  imports: [TranslatePipe, MoneyPipe, CurrencyPicker],
  templateUrl: './goal-dialog.html',
  styleUrl: './goal-dialog.scss',
})
export class GoalDialog {

  readonly goal = input.required<Goal>();
  readonly currencies = input.required<Currency[]>();
  // True when editing an existing goal — gates Close goal and the withdrawals editor.
  readonly editing = input<boolean>(true);
  // The current month key (e.g. 2026-06); stamped onto closedKey when the goal is closed.
  readonly currentMonthKey = input<string>('');
  // The balance this goal currently holds in its own currency; bounds the withdrawal.
  readonly balance = input<number>(0);
  readonly saved = output<Goal>();
  readonly cancelled = output<void>();

  readonly draft = linkedSignal<Goal>(() => structuredClone(this.goal()));

  // The TIME deadline mode, seeded from the goal: a period (n + unit) if it carries one, else a date.
  readonly timeMode = linkedSignal<TimeMode>(() => {
    const target = this.goal().target;
    return target.type === GoalTargetType.Time && target.n != null && !target.due ? 'period' : 'date';
  });

  protected readonly GoalTargetType = GoalTargetType; // for template comparisons

  readonly availableBalance = computed(() => Math.max(0, this.balance()));

  /** The currency record for the draft's code, so the money pipe renders the symbol (not the code). */
  readonly draftCurrency = computed(() =>
      this.currencies().find((currency) => currency.code === this.draft().cur) ?? this.draft().cur);

  setLabel(label: string): void {
    this.patch((goal) => goal.label = label);
  }

  setCurrency(currency: string): void {
    this.patch((goal) => goal.cur = currency);
  }

  setAmount(amount: number): void {
    this.patch((goal) => goal.amt = amount || 0);
  }

  setSavings(savings: boolean): void {
    this.patch((goal) => goal.savings = savings);
  }

  setWithdrawal(withdrawal: number): void {
    this.patch((goal) => goal.wd = this.clampWithdrawal(withdrawal || 0));
  }

  /** Pull every held funds out: set the withdrawal to the full available balance. */
  withdrawAll(): void {
    this.patch((goal) => goal.wd = this.availableBalance());
  }

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

  setTargetAmount(amount: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Amount) {
        goal.target.amount = amount || 0;
      }
    });
  }

  setTargetMult(mult: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Relative) {
        goal.target.mult = mult || 0;
      }
    });
  }

  /** Switch the TIME deadline between a due date and a period; clearing the other mode's fields. */
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

  setTargetDue(due: string): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.due = due;
      }
    });
  }

  setTargetPeriodCount(count: number): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.n = count || 0;
      }
    });
  }

  setTargetUnit(unit: string): void {
    this.patch((goal) => {
      if (goal.target.type === GoalTargetType.Time) {
        goal.target.unit = unit;
      }
    });
  }

  /** Close the goal: it stops contributing but keeps its balance and stays in the activity log. */
  closeGoal(): void {
    this.patch((goal) => {
      goal.closed = true;
      goal.closedKey = this.currentMonthKey();
    });
    this.save();
  }

  save(): void {
    const goal = structuredClone(this.draft());
    goal.label = goal.label.trim() || 'Goal';
    goal.wd = this.clampWithdrawal(goal.wd || 0);
    this.saved.emit(goal);
  }

  cancel(): void {
    this.cancelled.emit();
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cancel();
    }
  }

  private clampWithdrawal(withdrawal: number): number {
    return Math.max(0, Math.min(withdrawal, this.availableBalance()));
  }

  private patch(change: (goal: Goal) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
