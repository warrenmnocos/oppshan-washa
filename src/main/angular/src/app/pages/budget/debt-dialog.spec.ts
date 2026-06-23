import {ComponentFixture, TestBed} from '@angular/core/testing';
import {DebtDialog} from './debt-dialog';
import {Debt} from '../../models/budget.models';

function debt(): Debt {
  return {
    name: 'Home mortgage', principal: 5000000, annualRate: 6.5, monthly: 38000, termMonths: 240,
    repriceMode: 'payment', cur: 'PHP', prepay: false, prepayAmt: 0, rateSteps: [],
  };
}

describe('DebtDialog', () => {

  function mount(): ComponentFixture<DebtDialog> {
    const fixture = TestBed.createComponent(DebtDialog);
    fixture.componentRef.setInput('debt', debt());
    fixture.componentRef.setInput('currencies', [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]);
    fixture.detectChanges();
    return fixture;
  }

  it('should add and edit rate steps on the draft without mutating the input', () => {
    const fixture = mount();
    const original = debt();
    fixture.componentInstance.addRateStep();
    fixture.componentInstance.setRateStep(0, 'afterYears', 3);
    fixture.componentInstance.setRateStep(0, 'rate', 5.75);
    expect(original.rateSteps.length).toBe(0);
    const steps = fixture.componentInstance.draft().rateSteps;
    expect(steps.length).toBe(1);
    expect(steps[0]).toEqual({afterYears: 3, rate: 5.75});
  });

  it('should toggle reprice mode', () => {
    const fixture = mount();
    fixture.componentInstance.setRepriceMode('term');
    expect(fixture.componentInstance.draft().repriceMode).toBe('term');
  });

  it('should enable prepayment', () => {
    const fixture = mount();
    fixture.componentInstance.setPrepay(true);
    fixture.componentInstance.setPrepayAmount(10000);
    expect(fixture.componentInstance.draft().prepay).toBe(true);
    expect(fixture.componentInstance.draft().prepayAmt).toBe(10000);
  });

  it('should emit the edited debt on save with a defaulted name', () => {
    const fixture = mount();
    const emitted: Debt[] = [];
    fixture.componentInstance.saved.subscribe((d) => emitted.push(d));
    fixture.componentInstance.setName('  ');
    fixture.componentInstance.save();
    expect(emitted[0].name).toBe('Debt');
  });
});
