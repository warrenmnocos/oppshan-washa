import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {SalaryDialog} from './salary-dialog';
import {Salary} from '../../models/budget.models';

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
});
