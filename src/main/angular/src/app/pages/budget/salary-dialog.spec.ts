import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {SalaryDialog} from './salary-dialog';
import {Salary, SalaryPresetView} from '../../models/budget.models';
import {BracketOp} from '../../models/bracket-op';
import {DeductionBase} from '../../models/deduction-base';
import {DeductionType} from '../../models/deduction-type';
import {VariableType} from '../../models/variable-type';

function salary(): Salary {
  return {
    name: 'Alice',
    currency: 'JPY',
    engine: 'generic',
    components: [{label: 'Basic', amount: 500000, taxable: true, basic: true, varAuto: false}],
    deductions: [],
    variables: [],
  };
}

function presets(): SalaryPresetView[] {
  return [
    {
      uuid: 'builtin-jp',
      name: 'Japan — salaried',
      builtIn: true,
      salary: {
        name: '',
        currency: 'JPY',
        engine: 'generic',
        components: [{label: 'Base', amount: 0, taxable: true, basic: true, varAuto: false}],
        deductions: [{label: 'Health', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 5, pretax: true, varAuto: false}],
        variables: [],
      },
    },
    {
      uuid: 'custom-ph',
      name: 'My Philippines',
      builtIn: false,
      salary: {
        name: '', currency: 'PHP', engine: 'generic', components: [], deductions: [], variables: [],
      },
    },
  ];
}

describe('SalaryDialog', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  function mount(): ComponentFixture<SalaryDialog> {
    const fixture = TestBed.createComponent(SalaryDialog);
    fixture.componentRef.setInput('salary', salary());
    fixture.componentRef.setInput('currencies', [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]);
    fixture.detectChanges();
    return fixture;
  }

  it('should edit a deep clone so the input is never mutated', () => {
    const fixture = mount();
    const original = fixture.componentInstance.salary();
    fixture.componentInstance.setName('Bob');
    fixture.componentInstance.addComponent();
    expect(original.name).toBe('Alice');
    expect(original.components.length).toBe(1);
    expect(fixture.componentInstance.draft().name).toBe('Bob');
    expect(fixture.componentInstance.draft().components.length).toBe(2);
  });

  it('should add and remove deductions on the draft', () => {
    const fixture = mount();
    fixture.componentInstance.addDeduction();
    expect(fixture.componentInstance.draft().deductions.length).toBe(1);
    fixture.componentInstance.removeDeduction(0);
    expect(fixture.componentInstance.draft().deductions.length).toBe(0);
  });

  it('should emit the edited salary on save with a trimmed, defaulted name', () => {
    const fixture = mount();
    const emitted: Salary[] = [];
    fixture.componentInstance.saved.subscribe((s) => emitted.push(s));
    fixture.componentInstance.setName('  ');
    fixture.componentInstance.save();
    expect(emitted.length).toBe(1);
    expect(emitted[0].name).toBe('Income');
  });

  it('should emit cancelled without saving', () => {
    const fixture = mount();
    let cancelled = false;
    fixture.componentInstance.cancelled.subscribe(() => cancelled = true);
    fixture.componentInstance.cancel();
    expect(cancelled).toBe(true);
  });

  it('should apply a preset regime to the draft while keeping the income name', () => {
    const fixture = mount();
    fixture.componentRef.setInput('presets', presets());
    const dialog = fixture.componentInstance;

    dialog.applyPreset('builtin-jp');

    expect(dialog.draft().name).toBe('Alice'); // user's name preserved
    expect(dialog.draft().currency).toBe('JPY');
    expect(dialog.draft().deductions).toHaveLength(1);
    expect(dialog.draft().deductions[0].label).toBe('Health');
    expect(dialog.selectedPresetUuid()).toBe('builtin-jp');
    // The applied regime is a clone — mutating the draft must not touch the preset input.
    dialog.removeDeduction(0);
    expect(presets()[0].salary.deductions).toHaveLength(1);
  });

  it('should show the Delete control only for a custom preset', () => {
    const fixture = mount();
    fixture.componentRef.setInput('presets', presets());
    const dialog = fixture.componentInstance;

    dialog.applyPreset('builtin-jp');
    expect(dialog.canDeletePreset()).toBe(false);

    dialog.applyPreset('custom-ph');
    expect(dialog.canDeletePreset()).toBe(true);
  });

  it('should require a non-empty name before emitting savePreset', () => {
    const fixture = mount();
    fixture.componentRef.setInput('presets', presets());
    const dialog = fixture.componentInstance;
    const emitted: {name: string; salary: Salary}[] = [];
    dialog.savePreset.subscribe((event) => emitted.push(event));

    dialog.setPresetName('   ');
    dialog.onSavePreset();
    expect(emitted).toHaveLength(0);
    expect(dialog.presetMessage()).toBe('budget.salary.preset.nameRequired');

    dialog.setPresetName('Weekend gig');
    dialog.onSavePreset();
    expect(emitted).toHaveLength(1);
    expect(emitted[0].name).toBe('Weekend gig');
    expect(emitted[0].salary.name).toBe('Alice');
    expect(dialog.presetName()).toBe(''); // cleared after emit
  });

  it('should emit deletePreset with the selected uuid', () => {
    const fixture = mount();
    fixture.componentRef.setInput('presets', presets());
    const dialog = fixture.componentInstance;
    const emitted: string[] = [];
    dialog.deletePreset.subscribe((uuid) => emitted.push(uuid));

    dialog.applyPreset('custom-ph');
    dialog.onDeletePreset();

    expect(emitted).toEqual(['custom-ph']);
    expect(dialog.selectedPresetUuid()).toBe('');
  });

  it('should support pct and bracket variable types with their own bracket rows', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;

    dialog.addVariable();
    dialog.setVariableField(0, 'type', VariableType.Pct);
    dialog.setVariableField(0, 'base', DeductionBase.Gross);
    dialog.setVariableField(0, 'rate', '7');
    expect(dialog.draft().variables[0].type).toBe(VariableType.Pct);
    expect(dialog.draft().variables[0].base).toBe(DeductionBase.Gross);
    expect(dialog.draft().variables[0].rate).toBe(7);

    dialog.setVariableField(0, 'type', VariableType.Brackets);
    dialog.addVariableBracket(0);
    dialog.setVariableBracketField(0, 0, 'op', BracketOp.Gte);
    dialog.setVariableBracketField(0, 0, 'val', '1000');
    expect(dialog.draft().variables[0].brackets).toHaveLength(1);
    expect(dialog.draft().variables[0].brackets![0].op).toBe(BracketOp.Gte);
    expect(dialog.draft().variables[0].brackets![0].val).toBe(1000);

    dialog.removeVariableBracket(0, 0);
    expect(dialog.draft().variables[0].brackets).toHaveLength(0);
  });

  it('should render a multi-line textarea for a deduction formula and round-trip a multi-line value into expr', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;

    dialog.addDeduction();
    dialog.setDeductionField(0, 'type', DeductionType.Formula);
    fixture.detectChanges();

    const editor: HTMLTextAreaElement | null = fixture.nativeElement.querySelector('.editlist textarea.ei-expr');
    expect(editor).not.toBeNull();
    expect(editor!.tagName).toBe('TEXTAREA');
    // No single-line formula input survives.
    expect(fixture.nativeElement.querySelector('input.ei-expr-inline')).toBeNull();

    const multiline = 'base = taxable * 0.2\nmax(0, base - 50000)';
    editor!.value = multiline;
    editor!.dispatchEvent(new Event('input'));

    expect(dialog.draft().deductions[0].expr).toBe(multiline);
    expect(dialog.draft().deductions[0].expr).toContain('\n');
  });

  it('should render a multi-line textarea for a variable formula and round-trip a multi-line value into expr', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;

    dialog.addVariable();
    dialog.setVariableField(0, 'type', VariableType.Formula);
    fixture.detectChanges();

    const editor: HTMLTextAreaElement | null = fixture.nativeElement.querySelector('.editlist textarea.ei-expr');
    expect(editor).not.toBeNull();
    expect(editor!.tagName).toBe('TEXTAREA');

    const multiline = 'a = annual / 12\nfloor(a, 1000)';
    editor!.value = multiline;
    editor!.dispatchEvent(new Event('input'));

    expect(dialog.draft().variables[0].expr).toBe(multiline);
    expect(dialog.draft().variables[0].expr).toContain('\n');
  });

  it('should list the standard scope, component, and variable names plus the function list under a formula editor', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;

    // Give the pay component a var name and add a named custom variable so both show up in scope.
    dialog.draft().components[0].var = 'base';
    dialog.addVariable();
    dialog.setVariableField(0, 'var', 'bonus');
    dialog.setVariableField(0, 'type', VariableType.Formula);
    fixture.detectChanges();

    expect(dialog.scopeNames()).toEqual(['gross', 'basic', 'taxable', 'annual', 'base', 'bonus']);

    const codes = fixture.nativeElement.querySelectorAll('.expr-help code') as NodeListOf<HTMLElement>;
    const chips = Array.from(codes).map((code) => code.textContent);
    // Standard scope + the component/variable names are rendered as chips…
    for (const name of ['gross', 'basic', 'taxable', 'annual', 'base', 'bonus']) {
      expect(chips).toContain(name);
    }
    // …alongside the function list (a sample of the eight functions the engine supports).
    for (const fn of ['min', 'max', 'floor', 'round', 'ceil', 'clamp']) {
      expect(chips).toContain(fn);
    }
  });
});
