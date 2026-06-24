import {Component, input, linkedSignal, output} from '@angular/core';
import {Currency, Debt} from '../../models/budget.models';

/**
 * Edits one debt on a working copy: principal, annual rate, monthly payment, term, currency, the
 * reprice mode (re-amortize the payment vs. extend the term when the rate changes), scheduled rate
 * steps (a new rate after N years), and an optional annual principal prepayment. These are the
 * fields {@code DebtSimulator} reads to project payoff. Edits commit only on save.
 */
@Component({
  selector: 'app-debt-dialog',
  standalone: true,
  templateUrl: './debt-dialog.html',
  styleUrl: './debt-dialog.scss',
})
export class DebtDialog {

  readonly debt = input.required<Debt>();
  readonly currencies = input.required<Currency[]>();
  readonly saved = output<Debt>();
  readonly cancelled = output<void>();

  readonly draft = linkedSignal<Debt>(() => structuredClone(this.debt()));

  setName(name: string): void {
    this.patch((debt) => debt.name = name);
  }

  setCurrency(currency: string): void {
    this.patch((debt) => debt.cur = currency);
  }

  setNumber(field: 'principal' | 'annualRate' | 'monthly' | 'termMonths',
            value: number): void {
    this.patch((debt) => (debt[field] as unknown as number) = value || 0);
  }

  setRepriceMode(mode: 'payment' | 'term'): void {
    this.patch((debt) => debt.repriceMode = mode);
  }

  // ----- rate steps -----

  addRateStep(): void {
    this.patch((debt) => debt.rateSteps.push({afterYears: 1, rate: 0}));
  }

  removeRateStep(index: number): void {
    this.patch((debt) => debt.rateSteps.splice(index, 1));
  }

  setRateStep(index: number,
              field: 'afterYears' | 'rate',
              value: number): void {
    this.patch((debt) => debt.rateSteps[index][field] = value || 0);
  }

  // ----- prepayment -----

  setPrepay(prepay: boolean): void {
    this.patch((debt) => debt.prepay = prepay);
  }

  setPrepayAmount(amount: number): void {
    this.patch((debt) => debt.prepayAmt = amount || 0);
  }

  setPrepayCurrency(currency: string): void {
    this.patch((debt) => debt.prepayCur = currency);
  }

  save(): void {
    const debt = structuredClone(this.draft());
    debt.name = debt.name.trim() || 'Debt';
    this.saved.emit(debt);
  }

  cancel(): void {
    this.cancelled.emit();
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cancel();
    }
  }

  private patch(change: (debt: Debt) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
