import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
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
  goalProgress: [], savingsBalance: 0, activity: [],
};

// The compute round-trip carries the as-of month key (?month=YYYY-MM); match on the path.
const isCompute = (request: {url: string}) => request.url.startsWith('/api/budget/compute');

describe('BudgetPage', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({lang: 'en'})],
    });
    http = TestBed.inject(HttpTestingController);
  });

  // The export test spies on document.createElement; restore so the stub anchor doesn't leak into a
  // later TestBed.createComponent (which would fail with "rootElement.setAttribute is not a function").
  afterEach(() => vi.restoreAllMocks());

  function mount(): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges(); // ngOnInit -> load + presets + fx (stored) + live market fetch
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((request) => request.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    // The page also fetches live market rates client-side on mount (currency-api).
    http.expectOne((request) => request.url.includes('currency-api')).flush({jpy: {php: 0.36}});
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
    // No i18n JSON is loaded in unit tests, so the translate pipe echoes the caption key.
    const titheRow = rows.find((row) => row.textContent?.includes('budget.page.titheCaption'));
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

  it('should issue a PUT and update the fx state when a rate is edited', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    // Add a non-base currency so an editable rate row exists.
    page.store.setMonth({...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]});
    http.expectOne(isCompute).flush(COMPUTED);

    page.setRate('PHP', '0.42');
    const request = http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
    expect(request.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.42});
    request.flush({PHP: 0.42});

    expect(page.store.fxRates()).toEqual({PHP: 0.42});
    const row = page.fxEntries().find((entry) => entry.code === 'PHP');
    expect(row?.rate).toBe(0.42);
  });

  it('should apply a fetched market rate via use-market', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    page.store.setMonth({...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]});
    http.expectOne(isCompute).flush(COMPUTED);
    // Market rates already fetched on mount ({PHP: 0.36}); the row exposes it.
    expect(page.fxEntries().find((entry) => entry.code === 'PHP')?.market).toBe(0.36);

    page.useMarket('PHP');
    const request = http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
    expect(request.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.36});
    request.flush({PHP: 0.36});
    expect(page.store.fxRates()).toEqual({PHP: 0.36});
  });

  it('should ignore a non-positive rate edit without issuing a request', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    page.store.setMonth({...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]});
    http.expectOne(isCompute).flush(COMPUTED);

    page.setRate('PHP', '0');
    http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
  });
});
