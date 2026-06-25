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
  savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 60, salaryNet: {}, salaryBreakdown: [], debts: [],
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

  function mount(month: BudgetMonth = monthWithTithe(),
                 computed: Computed = COMPUTED): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges(); // ngOnInit -> load + presets + fx (stored) + live market fetch
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month);
    http.expectOne(isCompute).flush(computed);
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

  it('should render the income breakdown gross, deduction, and net lines for a salary with deductions', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [], variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 420000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Income tax', amount: 50000}, {label: 'Pension', amount: 30000}],
        net: 420000,
      }],
    };

    const breakdown = mount(month, computed).nativeElement.querySelector('.salbody') as HTMLElement;
    expect(breakdown).toBeTruthy();

    const lines = Array.from(breakdown.querySelectorAll('.salline'));
    // Gross subtotal, two deduction lines, then net take-home.
    expect(lines.length).toBe(4);
    expect(lines[0].classList.contains('subtotal')).toBe(true);
    expect(lines[0].querySelector('.amt')!.textContent).toContain('¥500,000');
    expect(lines[1].querySelector('.amt')!.textContent).toContain('−¥50,000');
    expect(lines[2].querySelector('.amt')!.textContent).toContain('−¥30,000');
    expect(lines[3].classList.contains('net')).toBe(true);
    expect(lines[3].querySelector('.amt')!.textContent).toContain('¥420,000');
  });

  it('should not render an income breakdown for a salary with no deductions', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Side gig', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 100000, taxable: true, basic: true, varAuto: false}],
        deductions: [], variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Side gig': 100000},
      salaryBreakdown: [{name: 'Side gig', currency: 'JPY', gross: 100000, deductions: [], net: 100000}],
    };

    const fixture = mount(month, computed);
    expect((fixture.nativeElement as HTMLElement).querySelector('.salbody')).toBeNull();
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

  // Per-row currency conversions: a non-base amount renders an "≈ <base>" caption; a base amount
  // renders none. The stored rate flushed on mount is {PHP: 0.36} (units of PHP per one base ¥).
  describe('currency conversions', () => {

    // A month carrying PHP as a second currency plus a PHP-priced expense, so both directions show:
    // a non-base (PHP) amount converts to the base, and the base totals convert to the PHP "home".
    function multiCurrencyMonth(): BudgetMonth {
      return {
        salaries: [],
        expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}, {label: 'Manila rent', amt: 36000, cur: 'PHP'}],
        goals: [],
        debts: [],
        cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
      };
    }

    it('should convert a non-base amount back to the base currency', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      // ₱36,000 ÷ 0.36 = ¥100,000.
      expect(page.convB(36000, 'PHP')).toBe('≈ ¥100,000');
    });

    it('should render no conversion for a base-currency amount', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      expect(page.convB(150000, 'JPY')).toBe('');
    });

    it('should render no conversion when no rate is known for the currency', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      expect(page.convB(1000, 'USD')).toBe('');
    });

    it('should render no conversion for a non-finite amount', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      expect(page.convB(NaN, 'PHP')).toBe('');
    });

    it('should convert a base figure to the listed home currency', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      // ¥100,000 × 0.36 = ₱36,000 (the home currency is the second listed, PHP).
      expect(page.convHome(100000)).toBe('≈ ₱36,000');
    });

    it('should render no home conversion when there is only a base currency', () => {
      // monthWithTithe() lists JPY only, so there is no home currency to convert into.
      const page = mount().componentInstance;
      expect(page.convHome(100000)).toBe('');
    });

    it('should render an "≈" caption beside a non-base expense row', () => {
      const host = mount(multiCurrencyMonth()).nativeElement as HTMLElement;
      const rows = Array.from(host.querySelectorAll('.row'));
      const phpRow = rows.find((row) => (row.querySelector('input.nameinput') as HTMLInputElement | null)?.value === 'Manila rent');
      expect(phpRow).toBeTruthy();
      expect(phpRow!.querySelector('.conv')?.textContent).toContain('≈ ¥100,000');
    });
  });

  // ngOnInit kicks off the month load and sets store.loading() true; until the month request flushes
  // the page shows shimmer skeletons. Mount without settling so loading() is still true, assert the
  // skeletons render and the real content is suppressed, then flush the mount HTTP and assert the swap.
  it('should render loading skeletons while loading and the real content once loaded', () => {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges(); // ngOnInit -> load() sets loading() true; month request is in flight
    const host = fixture.nativeElement as HTMLElement;

    // While loading: shimmer skeletons render, the chart and metric values do not.
    expect(fixture.componentInstance.store.loading()).toBe(true);
    expect(host.querySelectorAll('.skrow').length).toBeGreaterThan(0);
    expect(host.querySelector('.skchart')).toBeTruthy();
    expect(host.querySelector('app-money-chart')).toBeNull();
    expect(host.querySelector('.metric .mv')).toBeNull();
    expect(host.querySelector('[aria-busy="true"]')).toBeTruthy();

    // Flush the mount HTTP: month load, compute, presets, stored fx, and the live market fetch.
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    http.expectOne((r) => r.url.includes('currency-api')).flush({jpy: {php: 0.36}});
    fixture.detectChanges();

    // Once loaded: skeletons gone, the real chart and metric values render.
    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('.skrow')).toBeNull();
    expect(host.querySelector('.skchart')).toBeNull();
    expect(host.querySelector('app-money-chart')).toBeTruthy();
    expect(host.querySelector('.metric .mv')).toBeTruthy();
  });

  // The save progress affordance: while a save is in flight, the top #saveBar gains its active .run
  // state and the wrap gains .saving (the dim/disabled overlay). Both clear once the save settles.
  it('should show the save bar and saving overlay while a save is in flight, then clear them', () => {
    const fixture = mount();
    const host = fixture.nativeElement as HTMLElement;
    const wrap = host.querySelector('main.wrap') as HTMLElement;
    const saveBar = host.querySelector('#saveBar') as HTMLElement;

    // Idle: the bar exists but is not running and the wrap is not in its saving state.
    expect(saveBar).toBeTruthy();
    expect(saveBar.classList.contains('run')).toBe(false);
    expect(wrap.classList.contains('saving')).toBe(false);

    // An edit makes the month dirty (and fires a compute); kick off a save, leaving the PUT in flight.
    fixture.componentInstance.store.setMonth(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    fixture.componentInstance.store.save();
    fixture.detectChanges();

    expect(fixture.componentInstance.store.saving()).toBe(true);
    expect(saveBar.classList.contains('run')).toBe(true);
    expect(wrap.classList.contains('saving')).toBe(true);

    // Settle the save (PUT then the follow-up compute): the bar stops and the overlay lifts.
    http.expectOne((request) => request.method === 'PUT').flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.saving()).toBe(false);
    expect(saveBar.classList.contains('run')).toBe(false);
    expect(wrap.classList.contains('saving')).toBe(false);
  });

  // Navigating to another month reuses the load() path, so loading() flips true and the same shimmer
  // skeletons render mid-switch (rather than freezing on the old month) until the new month flushes.
  it('should show the loading skeletons while navigating to another month', () => {
    const fixture = mount();
    const host = fixture.nativeElement as HTMLElement;

    // Real content is showing (loaded) before the switch.
    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('.skrow')).toBeNull();

    // Navigate forward a month: load() sets loading() true and the next month's request is in flight.
    fixture.componentInstance.store.navigate(1);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.loading()).toBe(true);
    expect(host.querySelectorAll('.skrow').length).toBeGreaterThan(0);
    expect(host.querySelector('.skchart')).toBeTruthy();
    expect(host.querySelector('app-money-chart')).toBeNull();

    // Flush the navigation's month load + compute: skeletons clear, real content returns.
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('.skrow')).toBeNull();
    expect(host.querySelector('app-money-chart')).toBeTruthy();
  });
});
