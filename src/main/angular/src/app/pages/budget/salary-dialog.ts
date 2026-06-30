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
 * Edits one salary's full payroll config — pay components, custom variables, and deductions — on a
 * working copy that is only committed when the user saves (matching the baseline's income modal).
 * Deduction kinds and bases mirror what {@code SalaryEngine} computes (pct / fixed / formula, and the
 * additive bracket kind with its per-condition rule sub-editor). The dialog mutates a cloned draft so
 * a cancel discards everything; the parent applies the emitted salary through the store on save.
 */
@Component({
  selector: 'app-salary-dialog',
  standalone: true,
  imports: [TranslatePipe, CurrencyPicker],
  templateUrl: './salary-dialog.html',
  styleUrl: './salary-dialog.scss',
})
export class SalaryDialog {

  readonly salary = input.required<Salary>();
  readonly currencies = input.required<Currency[]>();
  readonly presets = input<SalaryPresetView[]>([]);
  // True when editing an existing income; the page passes false for the Add flow (drives the title).
  readonly editing = input<boolean>(true);
  readonly saved = output<Salary>();
  readonly cancelled = output<void>();
  readonly savePreset = output<{name: string; salary: Salary}>();
  readonly deletePreset = output<string>();

  // A deep clone that resets whenever the edited salary changes; edits stay local until Save.
  readonly draft = linkedSignal<Salary>(() => structuredClone(this.salary()));

  // The preset chosen in the dropdown (empty = the "Load a preset…" placeholder) and a transient
  // inline message (e.g. a blank-name rejection). The Delete control shows only for a custom preset.
  readonly selectedPresetUuid = signal('');
  readonly presetMessage = signal<string | null>(null);
  readonly selectedPreset = computed(() =>
      this.presets().find((preset) => preset.uuid === this.selectedPresetUuid()) ?? null);
  readonly canDeletePreset = computed(() => {
    const preset = this.selectedPreset();
    return preset !== null && !preset.builtIn;
  });

  // The preset whose payroll regime matches the current draft, if any (mirrors the prototype's
  // findMatchingPreset). When one matches it is the "active" preset: the save row is replaced by a
  // status line, since saving a duplicate regime would be pointless. Null when the draft is bespoke.
  readonly activePreset = computed(() => {
    const target = this.regimeCanon(this.draft());
    return this.presets().find((preset) => this.regimeCanon(preset.salary) === target) ?? null;
  });

  constructor() {
    // On open (and whenever the presets land), select the preset matching the draft's regime so the
    // dropdown reflects the loaded payroll template, matching the prototype's syncPresetUI. Only
    // seeds the selection when nothing is chosen yet, so a manual pick during editing isn't clobbered.
    effect(() => {
      const match = this.activePreset();
      if (match && this.selectedPresetUuid() === '') {
        this.selectedPresetUuid.set(match.uuid);
      }
    });
  }

  // Exposed for the template (enum comparisons in @switch / @if) and the dropdowns.
  protected readonly DeductionType = DeductionType;
  protected readonly DeductionBase = DeductionBase;
  protected readonly VariableType = VariableType;
  readonly deductionTypes = Object.values(DeductionType);
  readonly deductionBases = Object.values(DeductionBase);
  // The variable editor mirrors the deduction editor across all four kinds the engine evaluates:
  // fixed (a value), formula (an expression), pct (a base + rate), and the additive bracket kind.
  readonly variableTypes = Object.values(VariableType);

  // Bracket sub-editor: the comparison operators and contribution types the engine evaluates.
  protected readonly BracketOp = BracketOp;
  protected readonly BracketType = BracketType;
  readonly bracketOps = Object.values(BracketOp);
  readonly bracketTypes = Object.values(BracketType);
  private readonly bracketOpLabels: Record<string, string> = {
    [BracketOp.Gt]: '>',
    [BracketOp.Gte]: '>=',
    [BracketOp.Lt]: '<',
    [BracketOp.Lte]: '<=',
    [BracketOp.Eq]: '=',
  };
  private readonly bracketTypeLabels: Record<string, string> = {
    [BracketType.Fixed]: 'fixed amount',
    [BracketType.Formula]: 'formula',
    [BracketType.PctGross]: '% of gross',
    [BracketType.PctBasic]: '% of basic',
  };

  // Readable labels for the deduction/variable kind dropdown, mirroring the prototype's wording
  // ("graduated brackets" / "fixed amount" / "formula (custom)" / "percentage"). The app keeps a
  // separate base dropdown for the percentage case, so "percentage" here pairs with the base label.
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

  // Readable labels for the percentage-base dropdown, mirroring the prototype's "% of gross" wording.
  private readonly baseLabels: Record<string, string> = {
    [DeductionBase.Gross]: '% of gross',
    [DeductionBase.Basic]: '% of basic',
    [DeductionBase.Taxable]: '% of taxable',
    [DeductionBase.Annual]: '% of annual',
    [DeductionBase.Var]: '% of variable',
  };

  // The formula scope mirrors SalaryEngine: the four standard names always in scope, plus every pay
  // component's and custom variable's var name. The function list is fixed (also matching the engine).
  private static readonly STANDARD_SCOPE = ['gross', 'basic', 'taxable', 'annual'];
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

  setName(name: string): void {
    this.patch((salary) => salary.name = name);
  }

  setCurrency(currency: string): void {
    this.patch((salary) => salary.currency = currency);
  }

  // ----- presets -----

  readonly presetName = signal('');

  // The built-in preset keys the backend ships, mapped to readable i18n labels (the wire value stays
  // the key — the dropdown option's value is the uuid, only the visible text is humanized). The
  // labels mirror the prototype's salPresetOptions, em dashes and all (proper-name UI labels, not
  // narrative prose). A user-created preset isn't in this map and falls back to its own name.
  private static readonly BUILT_IN_LABELS: Record<string, string> = {
    blank: 'budget.preset.blank',
    jp: 'budget.preset.jp',
    jp0: 'budget.preset.jp0',
    ph: 'budget.preset.ph',
  };

  /**
   * The i18n key to render for a preset's dropdown label: a built-in's humanized label keyed by its
   * name, or the preset's own (user-given) name verbatim for a custom preset. Returned as a string
   * the template feeds the translate pipe — a built-in resolves to its translation; a raw user name
   * has no key and the pipe echoes it unchanged.
   */
  presetLabel(preset: SalaryPresetView): string {
    if (preset.builtIn) {
      return SalaryDialog.BUILT_IN_LABELS[preset.name] ?? preset.name;
    }

    return preset.name;
  }

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

  /** Save the current draft as a named preset; the page persists it and reloads the list. */
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

  /** Delete the selected custom preset; built-ins have no Delete control. */
  onDeletePreset(): void {
    const uuid = this.selectedPresetUuid();
    if (!uuid) {
      return;
    }

    this.deletePreset.emit(uuid);
    this.selectedPresetUuid.set('');
    this.presetMessage.set(null);
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

  // The component's variable name, reusable in deduction/formula scopes (mirrors the prototype's
  // per-component var field). A manual edit clears varAuto so an auto-named var isn't re-derived.
  setComponentVar(index: number,
                  name: string): void {
    this.patch((salary) => {
      salary.components[index].var = name;
      salary.components[index].varAuto = false;
    });
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

  // ----- variable brackets -----

  addVariableBracket(variableIndex: number): void {
    this.patch((salary) => {
      const variable = salary.variables[variableIndex];
      variable.brackets = variable.brackets ?? [];
      variable.brackets.push(this.newBracket());
    });
  }

  removeVariableBracket(variableIndex: number,
                        bracketIndex: number): void {
    this.patch((salary) => salary.variables[variableIndex].brackets?.splice(bracketIndex, 1));
  }

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

  // The deduction's own variable name, reusable in later deduction/formula scopes (mirrors the
  // prototype's per-deduction var field). A manual edit clears varAuto.
  setDeductionVar(index: number,
                  name: string): void {
    this.patch((salary) => {
      salary.deductions[index].var = name;
      salary.deductions[index].varAuto = false;
    });
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

  // ----- deduction brackets -----

  addDeductionBracket(deductionIndex: number): void {
    this.patch((salary) => {
      const deduction = salary.deductions[deductionIndex];
      deduction.brackets = deduction.brackets ?? [];
      deduction.brackets.push(this.newBracket());
    });
  }

  removeDeductionBracket(deductionIndex: number,
                         bracketIndex: number): void {
    this.patch((salary) => salary.deductions[deductionIndex].brackets?.splice(bracketIndex, 1));
  }

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

  private newBracket(): Bracket {
    return {var: '', op: BracketOp.Gt, val: 0, type: BracketType.Fixed, rate: 0};
  }

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
   * A stable fingerprint of a salary's payroll regime — its variables and deductions only — for
   * comparing a draft to a preset (mirrors the prototype's regimeCanon). Pay-component makeup and the
   * income name don't define the regime, so they're excluded; volatile/UI-only fields (varAuto) are
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
