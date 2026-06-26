import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {BudgetApiService} from './budget-api.service';
import {BudgetMonth} from '../models/budget.models';

function emptyMonth(): BudgetMonth {
  return {salaries: [], expenses: [], goals: [], debts: [], cur: [{code: 'JPY', sym: '¥'}]};
}

describe('BudgetApiService', () => {

  let api: BudgetApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [BudgetApiService, provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(BudgetApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('should GET the current user from /api/me', () => {
    api.me().subscribe();
    const request = http.expectOne('/api/me');
    expect(request.request.method).toBe('GET');
    request.flush({uuid: 'u', displayName: 'Alice', email: 'a@example.com'});
  });

  it('should GET a month by key', () => {
    api.getMonth('2026-06').subscribe();
    const request = http.expectOne('/api/budget/month/2026-06');
    expect(request.request.method).toBe('GET');
    request.flush(emptyMonth());
  });

  it('should PUT a month', () => {
    api.saveMonth('2026-06', emptyMonth()).subscribe();
    const request = http.expectOne('/api/budget/month/2026-06');
    expect(request.request.method).toBe('PUT');
    request.flush(emptyMonth());
  });

  it('should POST to compute with the as-of month key and the working fx rates in the body', () => {
    api.compute(emptyMonth(), '2026-06', {PHP: 0.4}).subscribe();
    const request = http.expectOne('/api/budget/compute?month=2026-06');
    expect(request.request.method).toBe('POST');
    // The body is the month spread plus the working fx-rate map (quote → rate) the backend prefers
    // over the DB when present.
    expect(request.request.body).toEqual({...emptyMonth(), fxRates: {PHP: 0.4}});
    request.flush({moneyIn: 0, moneyOut: 0, free: 0, tithe: 0, salaryNet: {}, debts: [], goalProgress: [], savingsBalance: 0});
  });

  it('should GET fx rates for a base currency', () => {
    api.fx('JPY').subscribe();
    const request = http.expectOne('/api/budget/fx?base=JPY');
    expect(request.request.method).toBe('GET');
    request.flush({PHP: 0.36});
  });

  it('should PUT an upserted fx rate with the base, quote, and rate', () => {
    api.setFxRate('JPY', 'PHP', 0.4).subscribe();
    const request = http.expectOne('/api/budget/fx');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.4});
    request.flush({PHP: 0.4});
  });

  it('should fetch live market rates and upper-case the quote codes', () => {
    let result: Record<string, number> | undefined;
    api.fetchMarketRates('JPY').subscribe((rates) => (result = rates));
    const request = http.expectOne((r) => r.url.includes('currency-api') && r.url.includes('jpy'));
    expect(request.request.method).toBe('GET');
    request.flush({jpy: {php: 0.36, usd: 0.0067}});
    expect(result).toEqual({PHP: 0.36, USD: 0.0067});
  });

  it('should fall back to open.er-api when the primary fetch fails', () => {
    let result: Record<string, number> | undefined;
    api.fetchMarketRates('JPY').subscribe((rates) => (result = rates));
    http.expectOne((r) => r.url.includes('currency-api'))
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
    const fallback = http.expectOne((r) => r.url.includes('open.er-api.com'));
    fallback.flush({rates: {PHP: 0.36}});
    expect(result).toEqual({PHP: 0.36});
  });

  it('should fall back to an empty map when both fetches fail', () => {
    let result: Record<string, number> | undefined;
    api.fetchMarketRates('JPY').subscribe((rates) => (result = rates));
    http.expectOne((r) => r.url.includes('currency-api'))
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
    http.expectOne((r) => r.url.includes('open.er-api.com'))
        .flush('down', {status: 503, statusText: 'Service Unavailable'});
    expect(result).toEqual({});
  });

  it('should GET the salary presets', () => {
    api.listPresets().subscribe();
    const request = http.expectOne('/api/budget/presets');
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('should POST a new salary preset with its name and salary', () => {
    const salary = {name: 'A', currency: 'JPY', engine: 'generic', components: [], deductions: [], variables: []};
    api.createPreset('Side gig', salary).subscribe();
    const request = http.expectOne('/api/budget/presets');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({name: 'Side gig', salary});
    request.flush({uuid: 'p1', name: 'Side gig', builtIn: false, salary});
  });

  it('should DELETE a salary preset by uuid', () => {
    api.deletePreset('p1').subscribe();
    const request = http.expectOne('/api/budget/presets/p1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
  });
});
