import {Component, input, linkedSignal, output} from '@angular/core';
import {Currency, Goal, GoalTarget} from '../../models/budget.models';

type TargetType = GoalTarget['type'];

/**
 * Edits one goal on a working copy: name, currency, monthly contribution, target type (open / a
 * fixed amount / a multiple of net income), the "treat as savings" flag, and a withdrawal. Target
 * types and the relative base mirror what the backend persists (Goal.TargetType). Time-based targets
 * and closing a goal are a follow-up — both need new persisted columns. Edits commit only on save.
 */
@Component({
  selector: 'app-goal-dialog',
  standalone: true,
  templateUrl: './goal-dialog.html',
  styleUrl: './goal-dialog.scss',
})
export class GoalDialog {

  readonly goal = input.required<Goal>();
  readonly currencies = input.required<Currency[]>();
  readonly saved = output<Goal>();
  readonly cancelled = output<void>();

  readonly draft = linkedSignal<Goal>(() => structuredClone(this.goal()));

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
    this.patch((goal) => goal.wd = withdrawal || 0);
  }

  setTargetType(type: TargetType): void {
    this.patch((goal) => {
      const current = goal.target;
      if (type === 'amount') {
        goal.target = {type: 'amount', amount: current.type === 'amount' ? current.amount : 0};
      } else if (type === 'relative') {
        goal.target = {
          type: 'relative',
          base: current.type === 'relative' ? current.base : 'all',
          mult: current.type === 'relative' ? current.mult : 6,
        };
      } else {
        goal.target = {type: 'open'};
      }
    });
  }

  setTargetAmount(amount: number): void {
    this.patch((goal) => {
      if (goal.target.type === 'amount') {
        goal.target.amount = amount || 0;
      }
    });
  }

  setTargetMult(mult: number): void {
    this.patch((goal) => {
      if (goal.target.type === 'relative') {
        goal.target.mult = mult || 0;
      }
    });
  }

  save(): void {
    const goal = structuredClone(this.draft());
    goal.label = goal.label.trim() || 'Goal';
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

  private patch(change: (goal: Goal) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
