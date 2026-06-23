import {ComponentFixture, TestBed} from '@angular/core/testing';
import {GoalDialog} from './goal-dialog';
import {Goal} from '../../models/budget.models';

function goal(): Goal {
  return {label: 'Emergency fund', amt: 50000, cur: 'JPY', target: {type: 'open'}, savings: true, wd: 0};
}

describe('GoalDialog', () => {

  function mount(): ComponentFixture<GoalDialog> {
    const fixture = TestBed.createComponent(GoalDialog);
    fixture.componentRef.setInput('goal', goal());
    fixture.componentRef.setInput('currencies', [{code: 'JPY', sym: '¥'}]);
    fixture.detectChanges();
    return fixture;
  }

  it('should switch target type to amount with a default and not mutate the input', () => {
    const fixture = mount();
    const original = fixture.componentInstance.goal();
    fixture.componentInstance.setTargetType('amount');
    fixture.componentInstance.setTargetAmount(360000);
    expect(original.target.type).toBe('open');
    const target = fixture.componentInstance.draft().target;
    expect(target.type).toBe('amount');
    expect(target.type === 'amount' && target.amount).toBe(360000);
  });

  it('should build a relative target with base all and a default multiple', () => {
    const fixture = mount();
    fixture.componentInstance.setTargetType('relative');
    const target = fixture.componentInstance.draft().target;
    expect(target.type).toBe('relative');
    expect(target.type === 'relative' && target.base).toBe('all');
    expect(target.type === 'relative' && target.mult).toBe(6);
  });

  it('should toggle the savings flag', () => {
    const fixture = mount();
    fixture.componentInstance.setSavings(false);
    expect(fixture.componentInstance.draft().savings).toBe(false);
  });

  it('should emit the edited goal on save with a defaulted name', () => {
    const fixture = mount();
    const emitted: Goal[] = [];
    fixture.componentInstance.saved.subscribe((g) => emitted.push(g));
    fixture.componentInstance.setLabel('   ');
    fixture.componentInstance.save();
    expect(emitted[0].label).toBe('Goal');
  });
});
