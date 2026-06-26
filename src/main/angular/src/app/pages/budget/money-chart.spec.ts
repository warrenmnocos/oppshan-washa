import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideTranslateService} from '@ngx-translate/core';
import {MoneyChart} from './money-chart';
import {ChartType} from '../../models/chart-type';

describe('MoneyChart', () => {

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideTranslateService({lang: 'en'})]});
  });

  function fixtureWith(slices: {label: string; value: number; color: string}[],
                       inputs: {savingsRate?: number; moneyIn?: number} = {}): ComponentFixture<MoneyChart> {
    const fixture = TestBed.createComponent(MoneyChart);
    fixture.componentRef.setInput('slices', slices);
    if (inputs.savingsRate !== undefined) {
      fixture.componentRef.setInput('savingsRate', inputs.savingsRate);
    }

    if (inputs.moneyIn !== undefined) {
      fixture.componentRef.setInput('moneyIn', inputs.moneyIn);
    }

    fixture.detectChanges();
    return fixture;
  }

  it('should default to the bar chart and render a bar per slice with the legend', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 150000, color: '#0E6E59'},
      {label: 'Free', value: 50000, color: '#1D9E75'},
    ]);
    const element = fixture.nativeElement as HTMLElement;
    // Bars is the default, matching the prototype (its Bars tab is pressed on load).
    expect(fixture.componentInstance.chartType()).toBe(ChartType.Bars);
    expect(element.querySelectorAll('.bars .barrow')).toHaveLength(2);
    // The compact legend (the prototype's .legend grid) renders one .lg entry per slice.
    expect(element.querySelectorAll('.legend .lg')).toHaveLength(2);
  });

  it('should render the savings-rate percentage in the donut center', () => {
    const fixture = fixtureWith([{label: 'Rent', value: 150000, color: '#0E6E59'}], {savingsRate: 42});
    fixture.componentInstance.setChartType(ChartType.Pie);
    fixture.detectChanges();
    const center = (fixture.nativeElement as HTMLElement).querySelector('.piecenter');
    expect(center?.textContent).toContain('42%');
  });

  it('should render one bar per slice when the bars tab is selected', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 150000, color: '#0E6E59'},
      {label: 'Free', value: 50000, color: '#1D9E75'},
    ]);
    fixture.componentInstance.setChartType(ChartType.Bars);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.bars .barrow')).toHaveLength(2);
    expect(element.querySelectorAll('path.pieseg')).toHaveLength(0);
  });

  it('should scale each bar to the widest slice', () => {
    const fixture = fixtureWith([
      {label: 'Big', value: 200, color: '#0E6E59'},
      {label: 'Half', value: 100, color: '#1D9E75'},
    ]);
    const widths = fixture.componentInstance.bars().map((bar) => bar.width);
    expect(widths[0]).toBeCloseTo(100);
    expect(widths[1]).toBeCloseTo(50);
  });

  it('should render one stacked-bar segment per slice when the flow tab is selected', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 75, color: '#0E6E59'},
      {label: 'Free', value: 25, color: '#1D9E75'},
    ]);
    fixture.componentInstance.setChartType(ChartType.Flow);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.flow .bar > span')).toHaveLength(2);
    const widths = fixture.componentInstance.flow().map((segment) => segment.width);
    expect(widths[0]).toBeCloseTo(75);
    expect(widths[1]).toBeCloseTo(25);
  });

  it('should drop non-positive slices', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 100000, color: '#0E6E59'},
      {label: 'Zero', value: 0, color: '#1D9E75'},
    ]);
    expect(fixture.componentInstance.rendered()).toHaveLength(1);
  });

  it('should compute each slice share of the total', () => {
    const fixture = fixtureWith([
      {label: 'A', value: 75, color: '#0E6E59'},
      {label: 'B', value: 25, color: '#1D9E75'},
    ]);
    const shares = fixture.componentInstance.rendered().map((slice) => slice.share);
    expect(shares[0]).toBeCloseTo(0.75);
    expect(shares[1]).toBeCloseTo(0.25);
  });

  it('should show the empty hint when there are no positive slices', () => {
    const fixture = fixtureWith([]);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.hint')).not.toBeNull();
    expect(element.querySelectorAll('path.pieseg')).toHaveLength(0);
  });

  it('should show the over-budget treatment when the segments exceed money-in', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 120000, color: '#0E6E59'},
      {label: 'Tithe', value: 30000, color: '#1D9E75'},
    ], {moneyIn: 100000});
    const element = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.overBudget()).toBe(true);
    expect(element.querySelector('.overbudget')).not.toBeNull();
    expect(element.querySelectorAll('path.pieseg')).toHaveLength(0);
    // The legend still renders alongside the over-budget message.
    expect(element.querySelectorAll('.legend .lg')).toHaveLength(2);
  });

  it('should not flag over budget when the segments fit within money-in', () => {
    const fixture = fixtureWith([{label: 'Rent', value: 80000, color: '#0E6E59'}], {moneyIn: 100000});
    expect(fixture.componentInstance.overBudget()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).querySelector('.overbudget')).toBeNull();
  });
});
