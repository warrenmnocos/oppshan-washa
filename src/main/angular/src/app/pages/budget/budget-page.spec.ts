import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {BudgetPage} from './budget-page';
import {BudgetMonth, Computed, Debt, NEVER_AMORTIZES} from '../../models/budget.models';

function monthWithTithe(): BudgetMonth {
  return {
    salaries: [],
    expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}, {label: 'Rent', amt: 150000, cur: 'JPY'}],
    goals: [],
    debts: [],
    cur: [{code: 'JPY', sym: '¥'}],
  };
}

const COMPUTED: Computed = {
  moneyIn: 500000, moneyOut: 200000, free: 300000, tithe: 50000, otherExpenses: 150000, debt: 0,
  savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 60, salaryNet: {}, debts: [],
};

describe('BudgetPage', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function mount(): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges(); // ngOnInit -> load + fx
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne('/api/budget/compute').flush(COMPUTED);
    http.expectOne((request) => request.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    fixture.detectChanges();
    return fixture;
  }

  it('should render summary metrics from the computed result', () => {
    const text = (mount().nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('¥500,000'); // money in
    expect(text).toContain('¥300,000'); // free
  });

  it('should render the tithe row read-only (no remove control)', () => {
    const fixture = mount();
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.row'));
    const titheRow = rows.find((row) => row.textContent?.includes('10% of net take-home'));
    expect(titheRow).toBeTruthy();
    expect(titheRow!.querySelector('input[type=number]')).toBeNull();
    expect(titheRow!.querySelector('.cc-rm')).toBeNull();
    expect(titheRow!.textContent).toContain('¥50,000');
  });

  it('should render the savings rate from the computed result', () => {
    const text = (mount().nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('60%');
  });

  it('should label a non-amortizing debt clearly', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    vi.spyOn(page, 'debtProjection').mockReturnValue(
        {name: 'X', months: NEVER_AMORTIZES, totalInterest: 0, prepayMonths: NEVER_AMORTIZES, prepayInterest: 0});
    expect(page.debtMonthsLabel({name: 'X'} as Debt)).toBe('never amortizes');
  });

  it('should format a debt payoff term as years and months', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    vi.spyOn(page, 'debtProjection').mockReturnValue(
        {name: 'X', months: 30, totalInterest: 0, prepayMonths: 30, prepayInterest: 0});
    expect(page.debtMonthsLabel({name: 'X'} as Debt)).toBe('2y 6m');
  });

  it('should export a tokyo-budget envelope as a JSON download', () => {
    const fixture = mount();
    const clickSpy = vi.fn();
    vi.spyOn(document, 'createElement').mockReturnValue({
      set href(_v: string) {}, set download(_v: string) {}, click: clickSpy,
    } as unknown as HTMLAnchorElement);
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:x');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});

    fixture.componentInstance.exportJson();
    expect(clickSpy).toHaveBeenCalled();
  });
});
