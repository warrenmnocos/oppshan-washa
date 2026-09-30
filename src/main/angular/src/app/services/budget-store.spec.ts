import {TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {Subject} from 'rxjs';
import {BudgetStore} from './budget-store';
import {BudgetApiService} from './budget-api.service';
import {BudgetMonth, Computed, Goal} from '../models/budget.models';
import {GoalTargetType} from '../models/goal-target-type';

function month(): BudgetMonth {
    return {salaries: [], expenses: [], goals: [], debts: [], cur: [{code: 'JPY', sym: '¥'}]};
}

const COMPUTED: Computed = {
    moneyIn: 100, moneyOut: 40, free: 60, tithe: 10, otherExpenses: 30, debt: 0,
    savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 60, salaryNet: {}, salaryBreakdown: [], debts: [],
    goalProgress: [], savingsBalance: 0, activity: [], prepayYear: [], debtProgress: [], debtBalance: 0,
    expenseSubtotal: {total: 0, byCurrency: []},
    goalSubtotal: {total: 0, byCurrency: []},
    debtSubtotal: {total: 0, byCurrency: []},
};

// The compute round-trip POSTs to /api/budget/compute carrying the as-of month key (?month=YYYY-MM).
const isCompute = (request: { url: string }) => request.url.startsWith('/api/budget/compute');

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

    it('should ignore navigation, save, and discard while a month load is in flight', () => {
        store.load();
        const keyMidLoad = store.monthKey();

        // The month GET is still open, so loading() is true and the navLoading-style guards drop these.
        store.navigate(1);
        store.save();
        store.discard();

        expect(store.monthKey()).toBe(keyMidLoad);
        // Only the original load's GET exists — no second month request and no save PUT.
        const openRequests = http.match((request) => request.url.startsWith('/api/budget/month/'));
        expect(openRequests.length).toBe(1);
        openRequests[0].flush(month());
        http.expectOne(isCompute).flush(COMPUTED);
        expect(store.loading()).toBe(false);
    });

    it('should carry the previous month forward, unsaved, when navigating onto an empty month', () => {
        const open: Goal = {
            label: 'Trip',
            amt: 200,
            cur: 'JPY',
            target: {type: GoalTargetType.Open},
            savings: true,
            wd: 50,
            closed: false
        };
        const done: Goal = {
            label: 'Old',
            amt: 0,
            cur: 'JPY',
            target: {type: GoalTargetType.Open},
            savings: true,
            wd: 0,
            closed: true,
            closedKey: '2026-06'
        };
        const populated: BudgetMonth = {
            ...month(),
            expenses: [{label: 'Rent', amt: 1000, cur: 'JPY'}],
            goals: [open, done]
        };

        store.load();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(populated);
        http.expectOne(isCompute).flush(COMPUTED);
        expect(store.dirty()).toBe(false);

        // Navigate forward onto a month the server has no data for (all-empty lists).
        store.navigate(1);
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month());
        http.expectOne(isCompute).flush(COMPUTED);

        // The empty target is seeded from the month we left: income/expenses carry over, the closed goal
        // drops off, and the surviving goal's one-time withdrawal is reset. It is Unsaved (no record backs
        // it) but NOT dirty — matching the prototype, so it shows no Discard until the user edits it, the
        // same as navigating backward onto an empty month.
        expect(store.dirty()).toBe(false);
        expect(store.unsaved()).toBe(true);
        expect(store.month().expenses).toEqual([{label: 'Rent', amt: 1000, cur: 'JPY'}]);
        expect(store.month().goals.length).toBe(1);
        expect(store.month().goals[0].label).toBe('Trip');
        expect(store.month().goals[0].wd).toBe(0);
    });

    it('should not carry forward onto a month that already has its own data', () => {
        const populated: BudgetMonth = {...month(), expenses: [{label: 'Rent', amt: 1000, cur: 'JPY'}]};
        store.load();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(populated);
        http.expectOne(isCompute).flush(COMPUTED);

        const target: BudgetMonth = {...month(), expenses: [{label: 'Groceries', amt: 300, cur: 'JPY'}]};
        store.navigate(1);
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(target);
        http.expectOne(isCompute).flush(COMPUTED);

        // The target has its own saved data, so it loads as-is and stays clean — and reads Saved.
        expect(store.month().expenses).toEqual([{label: 'Groceries', amt: 300, cur: 'JPY'}]);
        expect(store.dirty()).toBe(false);
        expect(store.unsaved()).toBe(false);
    });

    it('should report unsaved for a month with no saved record even when untouched', () => {
        store.load();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month());
        http.expectOne(isCompute).flush(COMPUTED);

        // No local edits, but nothing backs this month server-side — the label must not read Saved.
        expect(store.dirty()).toBe(false);
        expect(store.unsaved()).toBe(true);
    });

    it('should not carry forward from a month that has no data of its own', () => {
        store.load();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month());
        http.expectOne(isCompute).flush(COMPUTED);

        // The month we're leaving is empty, so there's nothing to carry — the next month stays empty/clean.
        store.navigate(1);
        http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(month());
        http.expectOne(isCompute).flush(COMPUTED);

        expect(store.dirty()).toBe(false);
        expect(store.month().expenses.length).toBe(0);
    });

    it('should mark dirty when mutating the working month', () => {
        store.setMonth(month());
        http.expectOne(isCompute).flush(COMPUTED);
        expect(store.dirty()).toBe(true);
    });

    it('should set an interest-free repayment on the working month and mark dirty', () => {
        vi.useFakeTimers();
        try {
            store.setMonth({
                ...month(),
                debts: [{
                    name: 'Colleague',
                    principal: 480000,
                    annualRate: 0,
                    monthly: 0,
                    cur: 'JPY',
                    interestFree: true,
                    prepay: false,
                    prepayAmt: 0,
                    rateSteps: []
                }],
            });
            http.expectOne(isCompute).flush(COMPUTED);

            store.setDebtMonthly(0, 200000);
            expect(store.month().debts[0].monthly).toBe(200000);
            expect(store.dirty()).toBe(true);

            store.setDebtMonthly(0, NaN); // an emptied input clears the repayment
            expect(store.month().debts[0].monthly).toBe(0);
            vi.advanceTimersByTime(250);
            http.expectOne(isCompute).flush(COMPUTED);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should clear dirty and unsaved again when an edit is reverted to the loaded value', () => {
        vi.useFakeTimers();
        try {
            const populated: BudgetMonth = {...month(), expenses: [{label: 'Rent', amt: 1000, cur: 'JPY'}]};
            store.load();
            http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(populated);
            http.expectOne(isCompute).flush(COMPUTED);
            expect(store.unsaved()).toBe(false);

            // Edit away from the loaded value, then back: dirty is JSON equality against the loaded
            // snapshot, not a one-way flag (a deliberate improvement on the prototype's markDirty), so the
            // Unsaved indicator and the Discard button revert with the value.
            store.mutate((working) => working.expenses[0].amt = 2000);
            expect(store.dirty()).toBe(true);
            expect(store.unsaved()).toBe(true);

            store.mutate((working) => working.expenses[0].amt = 1000);
            expect(store.dirty()).toBe(false);
            expect(store.unsaved()).toBe(false);
            vi.advanceTimersByTime(250); // settle the debounced recompute the two edits queued
            http.expectOne(isCompute).flush(COMPUTED);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should clear dirty again when a rate edit is slid back to the stored value', () => {
        vi.useFakeTimers();
        try {
            store.refreshFx('JPY');
            http.expectOne((request) => request.url.startsWith('/api/budget/fx')).flush({PHP: 0.38});
            expect(store.dirty()).toBe(false); // loading stored rates is the read side, not an edit

            store.setFxRate('JPY', 'PHP', 0.4);
            expect(store.dirty()).toBe(true);

            store.setFxRate('JPY', 'PHP', 0.38);
            expect(store.dirty()).toBe(false);
            vi.advanceTimersByTime(250);
            http.expectOne(isCompute).flush(COMPUTED);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should save the month and clear dirty', () => {
        store.setMonth(month());
        http.expectOne(isCompute).flush(COMPUTED);

        // No non-base rate is set, so save goes straight to the month PUT (no fx PUTs).
        store.save();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/') && request.method === 'PUT').flush(month());
        http.expectOne(isCompute).flush(COMPUTED);

        expect(store.dirty()).toBe(false);
        expect(store.saving()).toBe(false);
    });

    it('should persist each working non-base rate, then the month, on save', () => {
        // A two-currency month plus a deferred rate edit: save now flushes the fx upsert(s) for the
        // working rate before the month PUT (the rate edit was deferred from the slider to here).
        const twoCur: BudgetMonth = {...month(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]};
        vi.useFakeTimers();
        try {
            store.setMonth(twoCur);
            http.expectOne(isCompute).flush(COMPUTED);
            store.setFxRate('JPY', 'PHP', 0.42); // deferred edit: working map only, no PUT
            vi.advanceTimersByTime(250); // settle the recompute the edit queued
            http.expectOne(isCompute).flush(COMPUTED);

            store.save();
            // The fx upsert fires for the working PHP rate, then the month PUT.
            const fxPut = http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
            expect(fxPut.request.body).toEqual({base: 'JPY', quote: 'PHP', rate: 0.42});
            fxPut.flush({PHP: 0.42});
            http.expectOne((r) => r.url.startsWith('/api/budget/month/') && r.method === 'PUT').flush(twoCur);
            http.expectOne(isCompute).flush(COMPUTED);

            expect(store.dirty()).toBe(false);
            expect(store.saving()).toBe(false);
        } finally {
            vi.useRealTimers();
        }
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

    it('should apply a rate edit to the working map and dirty without persisting', () => {
        // A rate edit no longer PUTs: it updates the working fx map, marks dirty, and recomputes against
        // the working rate (debounced 250ms). The new rate rides in the compute body's fxRates, not a PUT.
        vi.useFakeTimers();
        try {
            store.setFxRate('JPY', 'PHP', 0.4);
            expect(store.fxRates()).toEqual({PHP: 0.4}); // applied locally at once
            expect(store.dirty()).toBe(true);
            http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT'); // nothing persisted
            vi.advanceTimersByTime(250); // settle the debounced recompute
            const request = http.expectOne(isCompute);
            expect(request.request.body.fxRates).toEqual({PHP: 0.4}); // the working rate is sent to /compute
            request.flush(COMPUTED);
            expect(store.computed().free).toBe(60);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should apply a fetched market rate into the working map without persisting', () => {
        store.fetchMarketRates('JPY');
        http.expectOne((request) => request.url.includes('currency-api')).flush({jpy: {php: 0.5}});

        // useMarketRate routes through setFxRate, so it too defers: working map + dirty + recompute, no PUT.
        vi.useFakeTimers();
        try {
            store.useMarketRate('JPY', 'PHP');
            expect(store.fxRates()).toEqual({PHP: 0.5});
            expect(store.dirty()).toBe(true);
            http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
            vi.advanceTimersByTime(250); // settle the debounced recompute
            const request = http.expectOne(isCompute);
            expect(request.request.body.fxRates).toEqual({PHP: 0.5});
            request.flush(COMPUTED);
        } finally {
            vi.useRealTimers();
        }
    });

    it('should ignore use-market when no market rate exists for the quote', () => {
        store.useMarketRate('JPY', 'PHP'); // nothing fetched yet
        http.expectNone((r) => r.method === 'PUT');
    });

    it('should clear the saving flag when save fails', () => {
        store.setMonth(month());
        http.expectOne(isCompute).flush(COMPUTED);

        // No non-base rate set, so save goes straight to the month PUT, which fails here.
        store.save();
        http.expectOne((request) => request.url.startsWith('/api/budget/month/') && request.method === 'PUT')
            .flush('boom', {status: 500, statusText: 'Server Error'});

        expect(store.saving()).toBe(false);
        expect(store.dirty()).toBe(true); // still dirty — save did not succeed
    });
});

describe('BudgetStore — recompute ordering under out-of-order /compute', () => {

    // A rapid rate-slider drag fires many /compute calls in quick succession. The recompute stream
    // must keep only the LATEST in flight, so a slower earlier response can never land after a newer
    // one and leave the totals showing a stale rate (the out-of-order race that bites under variable
    // Lambda latency). Here a stubbed api hands back Subjects we resolve by hand, in the wrong order.
    let store: BudgetStore;
    let computeCalls: Array<{ fxRates: Record<string, number>; subject: Subject<Computed> }>;

    beforeEach(() => {
        computeCalls = [];
        const apiStub = {
            compute: (_month: BudgetMonth, _asOf: string, fxRates: Record<string, number>) => {
                const subject = new Subject<Computed>();
                computeCalls.push({fxRates, subject});
                return subject.asObservable();
            },
        };
        TestBed.configureTestingModule({
            providers: [BudgetStore, {provide: BudgetApiService, useValue: apiStub}],
        });
        store = TestBed.inject(BudgetStore);
    });

    it('applies only the latest rate when an earlier compute resolves out of order', () => {
        vi.useFakeTimers();
        try {
            const earlier: Computed = {...COMPUTED, free: 111}; // the stale rate's result
            const latest: Computed = {...COMPUTED, free: 222};   // the rate the user ended on

            store.setFxRate('JPY', 'PHP', 0.30); // emission A
            vi.advanceTimersByTime(90);          // auditTime(80) window fires -> compute(0.30) in flight
            store.setFxRate('JPY', 'PHP', 0.90); // emission B — must supersede A's still-in-flight compute
            vi.advanceTimersByTime(90);          // -> compute(0.90) in flight

            expect(computeCalls).toHaveLength(2);
            expect(computeCalls[0].fxRates).toEqual({PHP: 0.30});
            expect(computeCalls[1].fxRates).toEqual({PHP: 0.90});

            // The newer compute (B) returns first and is shown...
            computeCalls[1].subject.next(latest);
            expect(store.computed().free).toBe(222);

            // ...then the older, slower compute (A) returns LATE. Its result must be ignored — the older
            // request was unsubscribed when B started, so the totals stay on the latest rate (no overwrite).
            computeCalls[0].subject.next(earlier);
            expect(store.computed().free).toBe(222);
        } finally {
            vi.useRealTimers();
        }
    });
});
