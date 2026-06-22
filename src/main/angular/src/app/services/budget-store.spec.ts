import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {BudgetStore} from './budget-store';
import {BudgetMonth, Computed} from '../models/budget.models';

function month(): BudgetMonth {
  return {salaries: [], expenses: [], goals: [], debts: [], cur: [{code: 'JPY', sym: '¥'}]};
}

const COMPUTED: Computed = {moneyIn: 100, moneyOut: 40, free: 60, tithe: 10, salaryNet: {}, debts: []};

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
    http.expectOne('/api/budget/compute').flush(COMPUTED);

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
      http.match('/api/budget/compute').forEach((request) => request.flush(COMPUTED));
    }
    // 60 forward steps allowed, then clamped — never beyond the limit.
    expect(store.canGoForward()).toBe(false);
    expect(store.monthKey()).not.toBe(beforeKey);
  });

  it('should mark dirty when mutating the working month', () => {
    store.setMonth(month());
    http.expectOne('/api/budget/compute').flush(COMPUTED);
    expect(store.dirty()).toBe(true);
  });

  it('should save the month and clear dirty', () => {
    store.setMonth(month());
    http.expectOne('/api/budget/compute').flush(COMPUTED);

    store.save();
    http.expectOne((request) => request.method === 'PUT').flush(month());
    http.expectOne('/api/budget/compute').flush(COMPUTED);

    expect(store.dirty()).toBe(false);
    expect(store.saving()).toBe(false);
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
    http.expectOne('/api/budget/compute').flush('boom', {status: 500, statusText: 'Server Error'});
    expect(store.computed().free).toBe(0);
  });

  it('should clear the saving flag when save fails', () => {
    store.setMonth(month());
    http.expectOne('/api/budget/compute').flush(COMPUTED);

    store.save();
    http.expectOne((request) => request.method === 'PUT')
        .flush('boom', {status: 500, statusText: 'Server Error'});

    expect(store.saving()).toBe(false);
    expect(store.dirty()).toBe(true); // still dirty — save did not succeed
  });
});
