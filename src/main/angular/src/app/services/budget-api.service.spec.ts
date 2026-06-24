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

  it('should POST to compute', () => {
    api.compute(emptyMonth()).subscribe();
    const request = http.expectOne('/api/budget/compute');
    expect(request.request.method).toBe('POST');
    request.flush({moneyIn: 0, moneyOut: 0, free: 0, tithe: 0, salaryNet: {}, debts: []});
  });

  it('should GET fx rates for a base currency', () => {
    api.fx('JPY').subscribe();
    const request = http.expectOne('/api/budget/fx?base=JPY');
    expect(request.request.method).toBe('GET');
    request.flush({PHP: 0.36});
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
