import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {GoalDialog} from './goal-dialog';
import {Goal} from '../../models/budget.models';
import {GoalTargetType} from '../../models/goal-target-type';

function goal(): Goal {
  return {label: 'Emergency fund', amt: 50000, cur: 'JPY', target: {type: GoalTargetType.Open}, savings: true, wd: 0, closed: false};
}

describe('GoalDialog', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  function mount(inputs: {balance?: number; monthKey?: string} = {}): ComponentFixture<GoalDialog> {
    const fixture = TestBed.createComponent(GoalDialog);
    fixture.componentRef.setInput('goal', goal());
    fixture.componentRef.setInput('currencies', [{code: 'JPY', sym: '¥'}]);
    fixture.componentRef.setInput('balance', inputs.balance ?? 0);
    fixture.componentRef.setInput('currentMonthKey', inputs.monthKey ?? '2026-06');
    fixture.detectChanges();
    return fixture;
  }

  it('should switch target type to amount with a default and not mutate the input', () => {
    const fixture = mount();
    const original = fixture.componentInstance.goal();
    fixture.componentInstance.setTargetType(GoalTargetType.Amount);
    fixture.componentInstance.setTargetAmount(360000);
    expect(original.target.type).toBe(GoalTargetType.Open);
    const target = fixture.componentInstance.draft().target;
    expect(target.type).toBe(GoalTargetType.Amount);
    expect(target.type === GoalTargetType.Amount && target.amount).toBe(360000);
  });

  it('should build a relative target with base all and a default multiple', () => {
    const fixture = mount();
    fixture.componentInstance.setTargetType(GoalTargetType.Relative);
    const target = fixture.componentInstance.draft().target;
    expect(target.type).toBe(GoalTargetType.Relative);
    expect(target.type === GoalTargetType.Relative && target.base).toBe('all');
    expect(target.type === GoalTargetType.Relative && target.mult).toBe(6);
  });

  it('should toggle the savings flag', () => {
    const fixture = mount();
    fixture.componentInstance.setSavings(false);
    expect(fixture.componentInstance.draft().savings).toBe(false);
  });

  it('should edit a time target due date in date mode', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;
    dialog.setTargetType(GoalTargetType.Time);
    dialog.setTimeMode('date');
    dialog.setTargetDue('2027-03-31');
    const target = dialog.draft().target;
    expect(target.type).toBe(GoalTargetType.Time);
    expect(target.type === GoalTargetType.Time && target.due).toBe('2027-03-31');
  });

  it('should edit a time target period count and unit, clearing the due date', () => {
    const fixture = mount();
    const dialog = fixture.componentInstance;
    dialog.setTargetType(GoalTargetType.Time);
    dialog.setTimeMode('date');
    dialog.setTargetDue('2027-03-31');
    dialog.setTimeMode('period');
    dialog.setTargetPeriodCount(18);
    dialog.setTargetUnit('months');
    const target = dialog.draft().target;
    expect(target.type === GoalTargetType.Time && target.n).toBe(18);
    expect(target.type === GoalTargetType.Time && target.unit).toBe('months');
    expect(target.type === GoalTargetType.Time && target.due).toBeUndefined();
  });

  it('should close the goal, set the closed flag and stamp the current month key', () => {
    const fixture = mount({monthKey: '2026-06'});
    const emitted: Goal[] = [];
    fixture.componentInstance.saved.subscribe((g) => emitted.push(g));
    fixture.componentInstance.closeGoal();
    expect(emitted[0].closed).toBe(true);
    expect(emitted[0].closedKey).toBe('2026-06');
  });

  it('should set the withdrawal to the full balance on withdraw all', () => {
    const fixture = mount({balance: 80000});
    fixture.componentInstance.withdrawAll();
    expect(fixture.componentInstance.draft().wd).toBe(80000);
  });

  it('should clamp the withdrawal to the available balance', () => {
    const fixture = mount({balance: 30000});
    fixture.componentInstance.setWithdrawal(999999);
    expect(fixture.componentInstance.draft().wd).toBe(30000);
    fixture.componentInstance.setWithdrawal(-100);
    expect(fixture.componentInstance.draft().wd).toBe(0);
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
