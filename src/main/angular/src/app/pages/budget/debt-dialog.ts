import {Component, input, linkedSignal, output} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Currency, Debt} from '../../models/budget.models';
import {DebtRepriceMode} from '../../models/debt-reprice-mode';
import {CurrencyPicker} from './currency-picker';

/**
 * Edits one debt on a working copy: principal, annual rate, monthly payment, term, currency, the
 * reprice mode (re-amortize the payment vs. extend the term when the rate changes), scheduled rate
 * steps (a new rate after N years), and an optional annual principal prepayment. These are the fields
 * {@code DebtSimulator} reads to project payoff. Edits happen on a cloned draft, so Save emits
 * `saved(debt)` while Cancel emits `cancelled()` and discards it.
 */
@Component({
  selector: 'app-debt-dialog',
  standalone: true,
  imports: [TranslatePipe, CurrencyPicker],
  templateUrl: './debt-dialog.html',
  styleUrl: './debt-dialog.scss',
})
export class DebtDialog {

  /** The debt to edit; cloned into the draft so edits stay local until Save. */
  readonly debt = input.required<Debt>();
  /** The currencies offered by the currency picker. */
  readonly currencies = input.required<Currency[]>();
  /** True when editing an existing debt; false for an Add flow. Drives the title. */
  readonly editing = input<boolean>(true);
  /** Emits the committed debt when the user saves. */
  readonly saved = output<Debt>();
  /** Emits when the user cancels; the draft is discarded. */
  readonly cancelled = output<void>();

  /** A deep clone of the input debt that reseeds whenever it changes; edits stay here until Save. */
  readonly draft = linkedSignal<Debt>(() => structuredClone(this.debt()));

  /** Exposed for template enum comparisons. */
  protected readonly DebtRepriceMode = DebtRepriceMode;

  /** Update the draft debt's name. */
  setName(name: string): void {
    this.patch((debt) => debt.name = name);
  }

  /** Update the draft debt's currency code. */
  setCurrency(currency: string): void {
    this.patch((debt) => debt.cur = currency);
  }

  /** Set one of the numeric debt fields, coercing a blank or NaN entry to 0. */
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

  /** Set whether a rate change re-amortizes the payment or extends the term. */
  setRepriceMode(mode: DebtRepriceMode): void {
    this.patch((debt) => debt.repriceMode = mode);
  }

  /** Append a scheduled rate step (a new rate after N years) to the draft. */
  addRateStep(): void {
    this.patch((debt) => debt.rateSteps.push({afterYears: 1, rate: 0}));
  }

  /** Remove the rate step at the given index. */
  removeRateStep(index: number): void {
    this.patch((debt) => debt.rateSteps.splice(index, 1));
  }

  /** Set a field on the rate step at the given index, coercing a blank or NaN entry to 0. */
  setRateStep(index: number,
              field: 'afterYears' | 'rate',
              value: number): void {
    this.patch((debt) => debt.rateSteps[index][field] = value || 0);
  }

  /**
   * Toggle the annual principal prepayment on or off. It's a Yes/No flag here; the amount and currency
   * are edited inline on the Money-out prepayment sub-row (the prototype's split).
   */
  setPrepay(prepay: boolean): void {
    this.patch((debt) => debt.prepay = prepay);
  }

  /** Commit the draft: trim the name (falling back to "Debt") and emit `saved`. */
  save(): void {
    const debt = structuredClone(this.draft());
    debt.name = debt.name.trim() || 'Debt';
    this.saved.emit(debt);
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

  /** Apply a mutation to a fresh clone of the draft and set it back, so signal consumers see a new reference. */
  private patch(change: (debt: Debt) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
