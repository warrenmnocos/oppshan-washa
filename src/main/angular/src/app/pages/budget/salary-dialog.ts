import {Component, input, linkedSignal, output} from '@angular/core';
import {Currency, Deduction, Salary, Variable} from '../../models/budget.models';
import {DeductionBase} from '../../models/deduction-base';
import {DeductionType} from '../../models/deduction-type';
import {VariableType} from '../../models/variable-type';

/**
 * Edits one salary's full payroll config — pay components, custom variables, and deductions — on a
 * working copy that is only committed when the user saves (matching the baseline's income modal).
 * Deduction kinds and bases mirror what {@code SalaryEngine} computes (pct / fixed / formula, and a
 * read-only note for the additive bracket kind, whose per-condition editor is a follow-up). The
 * dialog mutates a cloned draft so a cancel discards everything; the parent applies the emitted
 * salary through the store on save.
 */
@Component({
  selector: 'app-salary-dialog',
  standalone: true,
  templateUrl: './salary-dialog.html',
  styleUrl: './salary-dialog.scss',
})
export class SalaryDialog {

  readonly salary = input.required<Salary>();
  readonly currencies = input.required<Currency[]>();
  readonly saved = output<Salary>();
  readonly cancelled = output<void>();

  // A deep clone that resets whenever the edited salary changes; edits stay local until Save.
  readonly draft = linkedSignal<Salary>(() => structuredClone(this.salary()));

  // Exposed for the template (enum comparisons in @switch / @if) and the dropdowns.
  protected readonly DeductionType = DeductionType;
  protected readonly DeductionBase = DeductionBase;
  protected readonly VariableType = VariableType;
  readonly deductionTypes = Object.values(DeductionType);
  readonly deductionBases = Object.values(DeductionBase);
  // The variable editor only renders fixed (a value) and formula (an expression); pct/brackets exist
  // on VariableType for the engine but aren't offered here yet.
  readonly variableTypes = [VariableType.Fixed, VariableType.Formula];

  setName(name: string): void {
    this.patch((salary) => salary.name = name);
  }

  setCurrency(currency: string): void {
    this.patch((salary) => salary.currency = currency);
  }

  // ----- components -----

  addComponent(): void {
    this.patch((salary) => salary.components.push(
        {label: 'Component', amount: 0, taxable: true, basic: false, varAuto: false}));
  }

  removeComponent(index: number): void {
    this.patch((salary) => salary.components.splice(index, 1));
  }

  setComponentLabel(index: number,
                    label: string): void {
    this.patch((salary) => salary.components[index].label = label);
  }

  setComponentAmount(index: number,
                     amount: number): void {
    this.patch((salary) => salary.components[index].amount = amount || 0);
  }

  toggleComponentTaxable(index: number,
                         taxable: boolean): void {
    this.patch((salary) => salary.components[index].taxable = taxable);
  }

  toggleComponentBasic(index: number,
                       basic: boolean): void {
    this.patch((salary) => salary.components[index].basic = basic);
  }

  // ----- variables -----

  addVariable(): void {
    this.patch((salary) => salary.variables.push(
        {var: '', type: VariableType.Fixed, amount: 0, varAuto: false}));
  }

  removeVariable(index: number): void {
    this.patch((salary) => salary.variables.splice(index, 1));
  }

  setVariableField(index: number,
                   field: keyof Variable,
                   value: string): void {
    this.patch((salary) => {
      const variable = salary.variables[index];
      if (field === 'amount' || field === 'rate') {
        (variable[field] as unknown as number) = Number(value) || 0;
      } else {
        (variable[field] as unknown as string) = value;
      }
    });
  }

  // ----- deductions -----

  addDeduction(): void {
    this.patch((salary) => salary.deductions.push(
        {label: 'Deduction', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 0, pretax: false, varAuto: false}));
  }

  removeDeduction(index: number): void {
    this.patch((salary) => salary.deductions.splice(index, 1));
  }

  toggleDeductionPretax(index: number,
                        pretax: boolean): void {
    this.patch((salary) => salary.deductions[index].pretax = pretax);
  }

  setDeductionField(index: number,
                    field: keyof Deduction,
                    value: string): void {
    this.patch((salary) => {
      const deduction = salary.deductions[index];
      const numericFields: (keyof Deduction)[] = ['rate', 'cap', 'floor', 'amount'];
      if (numericFields.includes(field)) {
        (deduction[field] as unknown as number) = Number(value) || 0;
      } else {
        (deduction[field] as unknown as string) = value;
      }
    });
  }

  // ----- lifecycle -----

  save(): void {
    const salary = structuredClone(this.draft());
    salary.name = salary.name.trim() || 'Income';
    this.saved.emit(salary);
  }

  cancel(): void {
    this.cancelled.emit();
  }

  onBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.cancel();
    }
  }

  private patch(change: (salary: Salary) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }
}
