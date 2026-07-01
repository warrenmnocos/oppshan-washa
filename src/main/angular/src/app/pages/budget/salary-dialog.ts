import {computed, Component, effect, input, linkedSignal, output, signal} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {Bracket, Currency, Deduction, Salary, SalaryPresetView, Variable} from '../../models/budget.models';
import {BracketOp} from '../../models/bracket-op';
import {BracketType} from '../../models/bracket-type';
import {DeductionBase} from '../../models/deduction-base';
import {DeductionType} from '../../models/deduction-type';
import {VariableType} from '../../models/variable-type';
import {CurrencyPicker} from './currency-picker';

/**
 * Edits one salary's full payroll config on a working copy: pay components, custom variables, and
 * deductions. Deduction kinds and bases mirror what {@code SalaryEngine} computes: pct, fixed,
 * formula, and the additive bracket kind with its per-condition rule sub-editor. Everything happens
 * on a cloned draft, so Save emits `saved(salary)` while Cancel emits `cancelled()` and throws the
 * draft away. Also loads and saves reusable payroll presets. Mirrors the prototype's income modal.
 */
@Component({
  selector: 'app-salary-dialog',
  standalone: true,
  imports: [TranslatePipe, CurrencyPicker],
  templateUrl: './salary-dialog.html',
  styleUrl: './salary-dialog.scss',
})
export class SalaryDialog {

  /** The income to edit; cloned into the draft so edits stay local until Save. */
  readonly salary = input.required<Salary>();
  /** The currencies offered by the currency picker (symbol + code). */
  readonly currencies = input.required<Currency[]>();
  /** Saved payroll presets available to load into the draft, built-in and user-created. */
  readonly presets = input<SalaryPresetView[]>([]);
  /** True when editing an existing income; false for an Add flow. Drives the title. */
  readonly editing = input<boolean>(true);
  /** Emits the committed salary when the user saves. */
  readonly saved = output<Salary>();
  /** Emits when the user cancels; the draft is discarded. */
  readonly cancelled = output<void>();
  /** Requests saving the current draft as a named preset. */
  readonly savePreset = output<{name: string; salary: Salary}>();
  /** Requests deleting a preset by uuid. */
  readonly deletePreset = output<string>();

  /** A deep clone of the input salary that reseeds whenever it changes; edits stay here until Save. */
  readonly draft = linkedSignal<Salary>(() => structuredClone(this.salary()));

  /** The preset uuid chosen in the dropdown; empty is the "Load a preset…" placeholder. */
  readonly selectedPresetUuid = signal('');
  /** A transient inline message, e.g. a blank-name rejection or a save confirmation. */
  readonly presetMessage = signal<string | null>(null);
  /** The full preset record behind the current dropdown selection, or null when none matches. */
  readonly selectedPreset = computed(() =>
      this.presets().find((preset) => preset.uuid === this.selectedPresetUuid()) ?? null);
  /** True only for a user-created preset; built-ins can't be deleted, so their Delete control hides. */
  readonly canDeletePreset = computed(() => {
    const preset = this.selectedPreset();
    return preset !== null && !preset.builtIn;
  });

  /**
   * The preset whose payroll regime matches the current draft, or null when the draft is bespoke
   * (mirrors the prototype's findMatchingPreset). When one matches, the save row becomes a status
   * line, since re-saving the same regime would be pointless.
   */
  readonly activePreset = computed(() => {
    const target = this.regimeCanon(this.draft());
    return this.presets().find((preset) => this.regimeCanon(preset.salary) === target) ?? null;
  });

  /**
   * Seeds the dropdown to the preset matching the draft's regime when the dialog opens or the presets
   * arrive, so it reflects the loaded template (the prototype's syncPresetUI). Only seeds when nothing
   * is selected yet, so a manual pick mid-edit isn't clobbered.
   */
  constructor() {
    effect(() => {
      const match = this.activePreset();
      if (match && this.selectedPresetUuid() === '') {
        this.selectedPresetUuid.set(match.uuid);
      }
    });
  }

  /** The deduction and variable enums plus their value lists, exposed for template enum comparisons (@switch / @if) and the kind/base dropdowns. */
  protected readonly DeductionType = DeductionType;
  protected readonly DeductionBase = DeductionBase;
  protected readonly VariableType = VariableType;
  readonly deductionTypes = Object.values(DeductionType);
  readonly deductionBases = Object.values(DeductionBase);
  /**
   * The variable-kind options, mirroring the deduction editor across the four kinds the engine
   * evaluates: fixed (a value), formula (an expression), pct (a base + rate), and the additive bracket
   * kind.
   */
  readonly variableTypes = Object.values(VariableType);

  /** Bracket sub-editor enums and value lists: the comparison operators and contribution types the engine evaluates. */
  protected readonly BracketOp = BracketOp;
  protected readonly BracketType = BracketType;
  readonly bracketOps = Object.values(BracketOp);
  readonly bracketTypes = Object.values(BracketType);
  /** Comparison symbols for each bracket op, backing bracketOpLabel(). */
  private readonly bracketOpLabels: Record<string, string> = {
    [BracketOp.Gt]: '>',
    [BracketOp.Gte]: '>=',
    [BracketOp.Lt]: '<',
    [BracketOp.Lte]: '<=',
    [BracketOp.Eq]: '=',
  };
  /** Readable contribution labels for each bracket type, backing bracketTypeLabel(). */
  private readonly bracketTypeLabels: Record<string, string> = {
    [BracketType.Fixed]: 'fixed amount',
    [BracketType.Formula]: 'formula',
    [BracketType.PctGross]: '% of gross',
    [BracketType.PctBasic]: '% of basic',
  };

  /**
   * Readable labels for the deduction/variable kind dropdown, mirroring the prototype's wording
   * ("graduated brackets" / "fixed amount" / "formula (custom)" / "percentage"). The percentage case
   * keeps a separate base dropdown, so "percentage" here pairs with the base label.
   */
  private readonly typeLabels: Record<string, string> = {
    [DeductionType.Pct]: 'percentage',
    [DeductionType.Fixed]: 'fixed amount',
    [DeductionType.Formula]: 'formula (custom)',
    [DeductionType.Brackets]: 'graduated brackets',
    [VariableType.Pct]: 'percentage',
    [VariableType.Fixed]: 'fixed amount',
    [VariableType.Formula]: 'formula (custom)',
    [VariableType.Brackets]: 'graduated brackets',
  };

  /** Readable labels for the percentage-base dropdown, mirroring the prototype's "% of gross" wording. */
  private readonly baseLabels: Record<string, string> = {
    [DeductionBase.Gross]: '% of gross',
    [DeductionBase.Basic]: '% of basic',
    [DeductionBase.Taxable]: '% of taxable',
    [DeductionBase.Annual]: '% of annual',
    [DeductionBase.Var]: '% of variable',
  };

  /** The four standard formula names always in scope (mirrors SalaryEngine); scopeNames() appends each component's and variable's var name. */
  private static readonly STANDARD_SCOPE = ['gross', 'basic', 'taxable', 'annual'];
  /** The fixed set of functions a formula can call, matching the engine; rendered as reference chips under each formula editor. */
  readonly formulaFunctions = ['min', 'max', 'floor', 'round', 'ceil', 'abs', 'trunc', 'clamp'];

  /**
   * The variable names a formula can reference: the standard scope (gross/basic/taxable/annual) plus
   * each pay component's and custom variable's var name. Rendered as code chips under each formula
   * editor so the user knows what is in scope; data, not translated.
   */
  scopeNames(): string[] {
    const salary = this.draft();
    const componentVars = salary.components.map((component) => component.var).filter((name): name is string => !!name);
    const variableVars = salary.variables.map((variable) => variable.var).filter((name) => !!name);

    return [...SalaryDialog.STANDARD_SCOPE, ...componentVars, ...variableVars];
  }

  /** Short display label for a namespaced enum value, e.g. 'deductionType.pct' → 'pct'. */
  optionLabel(value: string): string {
    return value.split('.').pop() ?? value;
  }

  /** Readable label for a deduction/variable kind, e.g. 'deductionType.pct' → 'percentage'. */
  typeLabel(value: string): string {
    return this.typeLabels[value] ?? this.optionLabel(value);
  }

  /** Readable label for a percentage base, e.g. 'deductionBase.gross' → '% of gross'. */
  baseLabel(value: string): string {
    return this.baseLabels[value] ?? this.optionLabel(value);
  }

  /** The comparison symbol for a bracket op, e.g. 'bracketOp.gte' → '>='. */
  bracketOpLabel(op: string): string {
    return this.bracketOpLabels[op] ?? op;
  }

  /** The readable contribution label for a bracket type, e.g. 'bracketType.pctgross' → '% of gross'. */
  bracketTypeLabel(type: string): string {
    return this.bracketTypeLabels[type] ?? type;
  }

  /** Update the draft income's name. */
  setName(name: string): void {
    this.patch((salary) => salary.name = name);
  }

  /** Update the draft income's currency code. */
  setCurrency(currency: string): void {
    this.patch((salary) => salary.currency = currency);
  }

  /** The name typed into the save-as-preset field; cleared after a successful save. */
  readonly presetName = signal('');

  /**
   * The built-in preset names the backend ships, mapped to readable i18n labels. The wire value stays
   * the key (the dropdown option's value is the uuid; only the visible text is humanized). The labels
   * mirror the prototype's salPresetOptions, em dashes and all (proper-name UI labels, not narrative
   * prose). A user-created preset isn't in this map and falls back to its own name.
   */
  private static readonly BUILT_IN_LABELS: Record<string, string> = {
    blank: 'budget.preset.blank',
    Japan: 'budget.preset.jp',
    'Japan No Resident Tax': 'budget.preset.jp0',
    Philippines: 'budget.preset.ph',
  };

  /**
   * The i18n key to render for a preset's dropdown label: a built-in's humanized label keyed by its
   * name, or the preset's own (user-given) name verbatim for a custom preset. Returned as a string the
   * template feeds the translate pipe: a built-in resolves to its translation, while a raw user name
   * has no key and the pipe echoes it unchanged.
   */
  presetLabel(preset: SalaryPresetView): string {
    if (preset.builtIn) {
      return SalaryDialog.BUILT_IN_LABELS[preset.name] ?? preset.name;
    }

    return preset.name;
  }

  /** Track the name typed into the save-as-preset field. */
  setPresetName(name: string): void {
    this.presetName.set(name);
  }

  /** Load a preset's payroll regime into the draft, keeping the user's own income name. */
  applyPreset(uuid: string): void {
    this.selectedPresetUuid.set(uuid);
    this.presetMessage.set(null);
    const preset = this.presets().find((candidate) => candidate.uuid === uuid);
    if (!preset) {
      return;
    }

    const clone = structuredClone(preset.salary);
    this.patch((salary) => {
      salary.currency = clone.currency;
      salary.engine = clone.engine;
      salary.components = clone.components;
      salary.variables = clone.variables;
      salary.deductions = clone.deductions;
    });
  }

  /** Emit the current draft as a named preset; rejects a blank name with an inline message. */
  onSavePreset(): void {
    const name = this.presetName().trim();
    if (!name) {
      this.presetMessage.set('budget.salary.preset.nameRequired');
      return;
    }

    this.savePreset.emit({name, salary: structuredClone(this.draft())});
    this.presetName.set('');
    this.presetMessage.set('budget.salary.preset.saved');
  }

  /** Emit a delete request for the selected preset, then clear the selection. Built-ins have no Delete control. */
  onDeletePreset(): void {
    const uuid = this.selectedPresetUuid();
    if (!uuid) {
      return;
    }

    this.deletePreset.emit(uuid);
    this.selectedPresetUuid.set('');
    this.presetMessage.set(null);
  }

  /** Append a pay component with default values to the draft. */
  addComponent(): void {
    this.patch((salary) => salary.components.push(
        {label: 'Component', amount: 0, taxable: true, basic: false, varAuto: false}));
  }

  /** Remove the pay component at the given index. */
  removeComponent(index: number): void {
    this.patch((salary) => salary.components.splice(index, 1));
  }

  /** Rename the pay component at the given index. */
  setComponentLabel(index: number,
                    label: string): void {
    this.patch((salary) => salary.components[index].label = label);
  }

  /** Set the pay component's amount, coercing a blank or NaN entry to 0. */
  setComponentAmount(index: number,
                     amount: number): void {
    this.patch((salary) => salary.components[index].amount = amount || 0);
  }

  /**
   * Set the component's variable name, reusable in deduction and formula scopes (mirrors the
   * prototype's per-component var field). A manual edit clears varAuto so an auto-named var isn't
   * re-derived.
   */
  setComponentVar(index: number,
                  name: string): void {
    this.patch((salary) => {
      salary.components[index].var = name;
      salary.components[index].varAuto = false;
    });
  }

  /** Toggle whether the component counts toward taxable pay. */
  toggleComponentTaxable(index: number,
                         taxable: boolean): void {
    this.patch((salary) => salary.components[index].taxable = taxable);
  }

  /** Toggle whether the component counts toward basic pay. */
  toggleComponentBasic(index: number,
                       basic: boolean): void {
    this.patch((salary) => salary.components[index].basic = basic);
  }

  /** Append a custom variable (defaulting to the fixed kind) to the draft. */
  addVariable(): void {
    this.patch((salary) => salary.variables.push(
        {var: '', type: VariableType.Fixed, amount: 0, varAuto: false}));
  }

  /** Remove the custom variable at the given index. */
  removeVariable(index: number): void {
    this.patch((salary) => salary.variables.splice(index, 1));
  }

  /** Set one field on a variable, coercing amount and rate to numbers and leaving the rest as strings. */
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

  /** Append a bracket rule to the variable, initializing its bracket list if absent. */
  addVariableBracket(variableIndex: number): void {
    this.patch((salary) => {
      const variable = salary.variables[variableIndex];
      variable.brackets = variable.brackets ?? [];
      variable.brackets.push(this.newBracket());
    });
  }

  /** Remove one bracket rule from the variable. */
  removeVariableBracket(variableIndex: number,
                        bracketIndex: number): void {
    this.patch((salary) => salary.variables[variableIndex].brackets?.splice(bracketIndex, 1));
  }

  /** Set one field on a variable's bracket rule, if that bracket still exists. */
  setVariableBracketField(variableIndex: number,
                          bracketIndex: number,
                          field: keyof Bracket,
                          value: string): void {
    this.patch((salary) => {
      const bracket = salary.variables[variableIndex].brackets?.[bracketIndex];
      if (bracket) {
        this.assignBracketField(bracket, field, value);
      }
    });
  }

  /** Append a deduction (defaulting to a percentage of gross) to the draft. */
  addDeduction(): void {
    this.patch((salary) => salary.deductions.push(
        {label: 'Deduction', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 0, pretax: false, varAuto: false}));
  }

  /** Remove the deduction at the given index. */
  removeDeduction(index: number): void {
    this.patch((salary) => salary.deductions.splice(index, 1));
  }

  /** Toggle whether the deduction applies pre-tax. */
  toggleDeductionPretax(index: number,
                        pretax: boolean): void {
    this.patch((salary) => salary.deductions[index].pretax = pretax);
  }

  /**
   * Set the deduction's own variable name, reusable in later deduction and formula scopes (mirrors the
   * prototype's per-deduction var field). A manual edit clears varAuto.
   */
  setDeductionVar(index: number,
                  name: string): void {
    this.patch((salary) => {
      salary.deductions[index].var = name;
      salary.deductions[index].varAuto = false;
    });
  }

  /** Set one field on a deduction, coercing the numeric fields (rate/cap/floor/amount) and leaving the rest as strings. */
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

  /** Append a bracket rule to the deduction, initializing its bracket list if absent. */
  addDeductionBracket(deductionIndex: number): void {
    this.patch((salary) => {
      const deduction = salary.deductions[deductionIndex];
      deduction.brackets = deduction.brackets ?? [];
      deduction.brackets.push(this.newBracket());
    });
  }

  /** Remove one bracket rule from the deduction. */
  removeDeductionBracket(deductionIndex: number,
                         bracketIndex: number): void {
    this.patch((salary) => salary.deductions[deductionIndex].brackets?.splice(bracketIndex, 1));
  }

  /** Set one field on a deduction's bracket rule, if that bracket still exists. */
  setDeductionBracketField(deductionIndex: number,
                           bracketIndex: number,
                           field: keyof Bracket,
                           value: string): void {
    this.patch((salary) => {
      const bracket = salary.deductions[deductionIndex].brackets?.[bracketIndex];
      if (bracket) {
        this.assignBracketField(bracket, field, value);
      }
    });
  }

  /** Commit the draft: trim the name (falling back to "Income") and emit `saved`. */
  save(): void {
    const salary = structuredClone(this.draft());
    salary.name = salary.name.trim() || 'Income';
    this.saved.emit(salary);
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
  private patch(change: (salary: Salary) => void): void {
    const next = structuredClone(this.draft());
    change(next);
    this.draft.set(next);
  }

  /** A blank bracket rule: greater-than 0, contributing a fixed amount. */
  private newBracket(): Bracket {
    return {var: '', op: BracketOp.Gt, val: 0, type: BracketType.Fixed, rate: 0};
  }

  /** Assign one field on the given bracket, coercing val and rate to numbers and the rest to strings. */
  private assignBracketField(bracket: Bracket,
                             field: keyof Bracket,
                             value: string): void {
    if (field === 'val' || field === 'rate') {
      (bracket[field] as unknown as number) = Number(value) || 0;
    } else {
      (bracket[field] as unknown as string) = value;
    }
  }

  /**
   * A stable fingerprint of a salary's payroll regime (its variables and deductions only) for
   * comparing a draft to a preset (mirrors the prototype's regimeCanon). Pay-component makeup and the
   * income name don't define the regime, so they're excluded; volatile UI-only fields (varAuto) are
   * stripped and object keys are sorted deeply so two equivalent regimes serialize identically.
   */
  private regimeCanon(salary: Salary): string {
    return JSON.stringify(this.sortDeep(this.stripVolatile({
      variables: salary.variables ?? [],
      deductions: salary.deductions ?? [],
    })));
  }

  /** Recursively drop UI-only keys (varAuto) that don't define a regime, so they don't skew the canon. */
  private stripVolatile(value: unknown): unknown {
    if (Array.isArray(value)) {
      return value.map((item) => this.stripVolatile(item));
    }

    if (value && typeof value === 'object') {
      const result: Record<string, unknown> = {};
      for (const [key, entry] of Object.entries(value)) {
        if (key === 'varAuto') {
          continue;
        }

        result[key] = this.stripVolatile(entry);
      }

      return result;
    }

    return value;
  }

  /** Recursively sort object keys so structurally-equal regimes serialize to the same string. */
  private sortDeep(value: unknown): unknown {
    if (Array.isArray(value)) {
      return value.map((item) => this.sortDeep(item));
    }

    if (value && typeof value === 'object') {
      const result: Record<string, unknown> = {};
      for (const key of Object.keys(value).sort()) {
        result[key] = this.sortDeep((value as Record<string, unknown>)[key]);
      }

      return result;
    }

    return value;
  }
}
