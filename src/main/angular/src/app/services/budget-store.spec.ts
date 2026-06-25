import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {BudgetStore} from './budget-store';
import {BudgetMonth, Computed} from '../models/budget.models';

function month(): BudgetMonth {
  return {salaries: [], expenses: [], goals: [], debts: [], cur: [{code: 'JPY', sym: '¥'}]};
}

const COMPUTED: Computed = {
  moneyIn: 100, moneyOut: 40, free: 60, tithe: 10, otherExpenses: 30, debt: 0,
  savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 60, salaryNet: {}, salaryBreakdown: [], debts: [],
  goalProgress: [], savingsBalance: 0, activity: [],
};

// The compute round-trip POSTs to /api/budget/compute carrying the as-of month key (?month=YYYY-MM).
const isCompute = (request: {url: string}) => request.url.startsWith('/api/budget/compute');

describe('BudgetStore', () => {

  let store: BudgetStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BudgetStore, provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(BudgetStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should expose a YYYY-MM month key', () => {
    expect(store.monthKey()).toMatch(/^\d{4}-\d{2}$/);
  });

  it('should load the month and run compute, clearing dirty', () => {
    store.load();
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month());
    http.expectOne(isCompute).flush(COMPUTED);

    expect(store.dirty()).toBe(false);
    expect(store.computed().free).toBe(60);
  });

  it('should clamp forward navigation at the planning limit', () => {
    const beforeKey = store.monthKey();
    for (let i = 0; i < 62; i++) {
      store.navigate(1);
      // Each accepted navigation triggers a load; flush both calls.
      const loads = http.match((request) => request.url.startsWith('/api/budget/month/'));
      loads.forEach((request) => request.flush(month()));
      http.match(isCompute).forEach((request) => request.flush(COMPUTED));
    }
    // 60 forward steps allowed, then clamped — never beyond the limit.
    expect(store.canGoForward()).toBe(false);
    expect(store.monthKey()).not.toBe(beforeKey);
  });

  it('should mark dirty when mutating the working month', () => {
    store.setMonth(month());
    http.expectOne(isCompute).flush(COMPUTED);
    expect(store.dirty()).toBe(true);
  });

  it('should save the month and clear dirty', () => {
    store.setMonth(month());
    http.expectOne(isCompute).flush(COMPUTED);

    store.save();
    http.expectOne((request) => request.method === 'PUT').flush(month());
    http.expectOne(isCompute).flush(COMPUTED);

    expect(store.dirty()).toBe(false);
    expect(store.saving()).toBe(false);
  });

  it('should discard unsaved edits by reloading the current month and clearing dirty', () => {
    store.setMonth(month()); // unsaved edit -> dirty
    http.expectOne(isCompute).flush(COMPUTED);
    expect(store.dirty()).toBe(true);

    store.discard();
    http.expectOne((request) => request.url.startsWith('/api/budget/month/') && request.method === 'GET')
        .flush(month());
    http.expectOne(isCompute).flush(COMPUTED);

    expect(store.dirty()).toBe(false);
  });

  it('should fall back to an empty month when the load fails', () => {
    store.load();
    http.expectOne((request) => request.url.startsWith('/api/budget/month/'))
        .flush('boom', {status: 500, statusText: 'Server Error'});

    expect(store.loading()).toBe(false);
    expect(store.month().expenses[0].auto).toBe('tithe'); // empty-month default
  });

  it('should reset computed totals when compute fails', () => {
    store.setMonth(month());
    http.expectOne(isCompute).flush('boom', {status: 500, statusText: 'Server Error'});
    expect(store.computed().free).toBe(0);
  });

  it('should load the salary presets into the presets signal', () => {
    store.loadPresets();
    http.expectOne('/api/budget/presets').flush([{uuid: 'p1', name: 'JP', builtIn: true, salary: {}}]);
    expect(store.presets()).toHaveLength(1);
    expect(store.presets()[0].name).toBe('JP');
  });

  it('should save a preset and reload the list on success', () => {
    const salary = {name: 'A', currency: 'JPY', engine: 'generic', components: [], deductions: [], variables: []};
    store.savePreset('Side gig', salary);
    http.expectOne((request) => request.url === '/api/budget/presets' && request.method === 'POST')
        .flush({uuid: 'p1', name: 'Side gig', builtIn: false, salary});
    http.expectOne((request) => request.url === '/api/budget/presets' && request.method === 'GET')
        .flush([{uuid: 'p1', name: 'Side gig', builtIn: false, salary}]);
    expect(store.presets()).toHaveLength(1);
  });

  it('should delete a preset and reload the list on success', () => {
    store.deletePreset('p1');
    http.expectOne((request) => request.method === 'DELETE').flush(null);
    http.expectOne((request) => request.url === '/api/budget/presets' && request.method === 'GET').flush([]);
    expect(store.presets()).toHaveLength(0);
  });

  it('should load stored fx rates into the fx signal', () => {
    store.refreshFx('JPY');
    http.expectOne('/api/budget/fx?base=JPY').flush({PHP: 0.36});
    expect(store.fxRates()).toEqual({PHP: 0.36});
  });

  it('should populate market rates and flag ready on a successful live fetch', () => {
    store.fetchMarketRates('JPY');
    expect(store.fxStatus()).toBe('loading');
    http.expectOne((request) => request.url.includes('currency-api')).flush({jpy: {php: 0.36}});
    expect(store.marketRates()).toEqual({PHP: 0.36});
    expect(store.fxStatus()).toBe('ready');
  });

  it('should flag unavailable when both live-fetch sources fail', () => {
    store.fetchMarketRates('JPY');
    http.expectOne((request) => request.url.includes('currency-api'))
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
    http.expectOne((request) => request.url.includes('open.er-api.com'))
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
    expect(store.fxStatus()).toBe('unavailable');
    expect(store.marketRates()).toEqual({});
  });

  it('should PUT a rate edit and update the fx signal from the refreshed map', () => {
    store.setFxRate('JPY', 'PHP', 0.4);
    const request = http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
    expect(request.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.4});
    request.flush({PHP: 0.4});
    expect(store.fxRates()).toEqual({PHP: 0.4});
  });

  it('should trigger a debounced recompute after a rate edit persists', () => {
    vi.useFakeTimers();
    try {
      store.setFxRate('JPY', 'PHP', 0.4);
      http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT').flush({PHP: 0.4});
      vi.advanceTimersByTime(300); // past the 250ms debounce
      http.expectOne(isCompute).flush(COMPUTED);
      expect(store.computed().free).toBe(60);
    } finally {
      vi.useRealTimers();
    }
  });

  it('should apply a fetched market rate through the upsert', () => {
    store.fetchMarketRates('JPY');
    http.expectOne((request) => request.url.includes('currency-api')).flush({jpy: {php: 0.5}});

    store.useMarketRate('JPY', 'PHP');
    const request = http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
    expect(request.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.5});
    request.flush({PHP: 0.5});
    expect(store.fxRates()).toEqual({PHP: 0.5});
  });

  it('should ignore use-market when no market rate exists for the quote', () => {
    store.useMarketRate('JPY', 'PHP'); // nothing fetched yet
    http.expectNone((r) => r.method === 'PUT');
  });

  it('should clear the saving flag when save fails', () => {
    store.setMonth(month());
    http.expectOne(isCompute).flush(COMPUTED);

    store.save();
    http.expectOne((request) => request.method === 'PUT')
        .flush('boom', {status: 500, statusText: 'Server Error'});

    expect(store.saving()).toBe(false);
    expect(store.dirty()).toBe(true); // still dirty — save did not succeed
  });
});
