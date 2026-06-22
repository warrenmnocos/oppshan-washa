import {ComponentFixture, TestBed} from '@angular/core/testing';
import {MoneyChart} from './money-chart';

describe('MoneyChart', () => {

  function fixtureWith(slices: {label: string; value: number; color: string}[]): ComponentFixture<MoneyChart> {
    const fixture = TestBed.createComponent(MoneyChart);
    fixture.componentRef.setInput('slices', slices);
    fixture.detectChanges();
    return fixture;
  }

  it('should render one path and one legend row per positive slice', () => {
    const fixture = fixtureWith([
      {label: 'Rent', value: 150000, color: '#0E6E59'},
      {label: 'Free', value: 50000, color: '#1D9E75'},
    ]);
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('path.pieseg')).toHaveLength(2);
    expect(element.querySelectorAll('.chartLegend li')).toHaveLength(2);
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
});
