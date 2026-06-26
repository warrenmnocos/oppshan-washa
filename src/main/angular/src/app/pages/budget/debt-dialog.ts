import {Component, input, linkedSignal, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency, Debt} from '../../models/budget.models';
import {DebtRepriceMode} from '../../models/debt-reprice-mode';
import {CurrencyPicker} from './currency-picker';

/**
 * Edits one debt on a working copy: principal, annual rate, monthly payment, term, currency, the
 * reprice mode (re-amortize the payment vs. extend the term when the rate changes), scheduled rate
 * steps (a new rate after N years), and an optional annual principal prepayment. These are the
 * fields {@code DebtSimulator} reads to project payoff. Edits commit only on save.
 */
@Component({
  selector: 'app-debt-dialog',
  standalone: true,
  imports: [TranslatePipe, CurrencyPicker],
  templateUrl: './debt-dialog.html',
  styleUrl: './debt-dialog.scss',
})
export class DebtDialog {

  readonly debt = input.required<Debt>();
  readonly currencies = input.required<Currency[]>();
  // True when editing an existing debt; the page passes false for the Add flow (drives the title).
  readonly editing = input<boolean>(true);
  readonly saved = output<Debt>();
  readonly cancelled = output<void>();

  readonly draft = linkedSignal<Debt>(() => structuredClone(this.debt()));

  protected readonly DebtRepriceMode = DebtRepriceMode; // for template comparisons

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

  /** The term as whole years for the dialog field (the model stores months; the prototype edits years). */
  termYears(): number | null {
    const months = this.draft().termMonths;
    return months == null ? null : Math.round(months / 12);
  }

  /** Set the term from the years field, converting back to the stored months (the prototype's *12). */
  setTermYears(years: number): void {
    this.patch((debt) => debt.termMonths = years > 0 ? Math.round(years) * 12 : 0);
  }

  setRepriceMode(mode: DebtRepriceMode): void {
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

  // Prepayment is a Yes/No flag here; the amount and currency are edited inline on the Money-out
  // prepayment sub-row (the prototype's split — the dialog only carries the toggle).
  setPrepay(prepay: boolean): void {
    this.patch((debt) => debt.prepay = prepay);
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
