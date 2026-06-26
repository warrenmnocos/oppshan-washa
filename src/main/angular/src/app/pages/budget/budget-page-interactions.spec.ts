import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {BudgetPage} from './budget-page';
import {BudgetMonth, Computed, Goal} from '../../models/budget.models';
import {GoalTargetType} from '../../models/goal-target-type';

function goalMonth(): BudgetMonth {
  return {
    ...emptyMonth(),
    goals: [{label: 'Emergency fund', amt: 50000, cur: 'JPY', target: {type: GoalTargetType.Open}, savings: true, wd: 0, closed: false}],
  };
}

function emptyMonth(): BudgetMonth {
  return {
    salaries: [], goals: [], debts: [],
    expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}],
    cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
  };
}

const COMPUTED: Computed = {
  moneyIn: 0, moneyOut: 0, free: 0, tithe: 0, otherExpenses: 0, debt: 0,
  savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 0, salaryNet: {}, salaryBreakdown: [], debts: [],
  goalProgress: [], savingsBalance: 0, activity: [], prepayYear: [],
};

// The compute round-trip carries the as-of month key (?month=YYYY-MM); match on the path.
const isCompute = (request: {url: string}) => request.url.startsWith('/api/budget/compute');

describe('BudgetPage interactions', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideTranslateService({lang: 'en'})],
    });
    http = TestBed.inject(HttpTestingController);
  });

  // Mount and settle the initial load (month + compute + fx). Mutations afterwards queue a
  // debounced (250ms) compute that does not fire within these synchronous tests. A caller can
  // seed the month and computed result that the initial load flushes back.
  function mount(month: BudgetMonth = emptyMonth(),
                 computed: Computed = COMPUTED,
                 market: Record<string, Record<string, number>> = {jpy: {php: 0.36}},
                 catalog: Record<string, string> = {jpy: 'Japanese Yen', php: 'Philippine Peso'}): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(month);
    http.expectOne(isCompute).flush(computed);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    http.expectOne((r) => r.url.endsWith('/currencies.json')).flush(catalog); // currency catalog
    http.expectOne((r) => r.url.endsWith('/currencies/jpy.json')).flush(market); // live market fetch
    return fixture;
  }

  it('should open the dialog on Add income, commit on save, then allow edits and removal', () => {
    const page = mount().componentInstance;
    // Add income opens the dialog on a fresh draft rather than dropping a blank row: the list is
    // unchanged and editedSalary() resolves the new draft.
    page.addSalary();
    expect(page.month().salaries).toHaveLength(0);
    expect(page.editedSalary()).not.toBeNull();

    // Saving the new income through applySalary pushes it (then clears the draft).
    const draft = page.editedSalary()!;
    page.applySalary({...draft, name: 'Earner A', currency: 'PHP'});
    expect(page.month().salaries).toHaveLength(1);
    expect(page.editedSalary()).toBeNull();
    expect(page.month().salaries[0].name).toBe('Earner A');
    expect(page.month().salaries[0].currency).toBe('PHP');

    // The committed salary stays inline-editable (basic amount) and removable.
    page.setSalaryBasic(0, 500000);
    expect(page.salaryBasicAmount(page.month().salaries[0])).toBe(500000);
    page.removeSalary(0);
    expect(page.month().salaries).toHaveLength(0);
  });

  it('should add, edit, and remove an expense while keeping the tithe', () => {
    const page = mount().componentInstance;
    page.addExpense();
    expect(page.month().expenses).toHaveLength(2);
    page.setExpense(1, 'label', 'Rent');
    page.setExpense(1, 'amt', '150000');
    page.setExpense(1, 'cur', 'PHP');
    const rent = page.month().expenses[1];
    expect(rent.label).toBe('Rent');
    expect(rent.amt).toBe(150000);
    expect(rent.cur).toBe('PHP');
    expect(page.isTithe(page.month().expenses[0])).toBe(true);
    page.removeExpense(1);
    expect(page.month().expenses).toHaveLength(1);
  });

  it('should open the dialog on Add goal, commit on save, then label every target type', () => {
    const page = mount().componentInstance;
    // Add goal opens the dialog on a fresh draft (a new goal holds nothing, so its balance is 0).
    page.addGoal();
    expect(page.month().goals).toHaveLength(0);
    expect(page.editedGoal()).not.toBeNull();
    expect(page.editedGoalBalance()).toBe(0);

    // Saving the new goal through applyGoal pushes it (then clears the draft).
    const draft = page.editedGoal()!;
    page.applyGoal({...draft, label: 'NISA'});
    expect(page.month().goals).toHaveLength(1);
    expect(page.editedGoal()).toBeNull();
    expect(page.month().goals[0].label).toBe('NISA');

    expect(page.goalTargetLabel({target: {type: GoalTargetType.Open}} as Goal)).toContain('open');
    expect(page.goalTargetLabel({target: {type: GoalTargetType.Amount, amount: 36000000}} as Goal)).toContain('target');
    expect(page.goalTargetLabel({target: {type: GoalTargetType.Relative, base: 'all', mult: 6}} as Goal)).toContain('all');
    page.removeGoal(0);
    expect(page.month().goals).toHaveLength(0);
  });

  it('should guard removal of a goal that still holds a balance', () => {
    const progress = {
      label: 'Emergency fund', currency: 'JPY', balance: 80000, target: null,
      pct: null, savings: true, complete: false, closed: false,
    };
    const page = mount(goalMonth(), {...COMPUTED, goalProgress: [progress]}).componentInstance;
    expect(page.canRemoveGoal(0)).toBe(false);

    page.removeGoal(0); // guarded — the goal stays
    expect(page.month().goals).toHaveLength(1);
  });

  it('should allow removal once a goal balance is drained', () => {
    const progress = {
      label: 'Emergency fund', currency: 'JPY', balance: 0, target: null,
      pct: null, savings: true, complete: false, closed: false,
    };
    const page = mount(goalMonth(), {...COMPUTED, goalProgress: [progress]}).componentInstance;
    expect(page.canRemoveGoal(0)).toBe(true);

    page.removeGoal(0);
    expect(page.month().goals).toHaveLength(0);
  });

  it('should render the activity card rows for withdrawals and closures', () => {
    const activity = [
      {label: 'Emergency fund', currency: 'JPY', amount: 20000, kind: 'withdrawal' as const},
      {label: 'Old goal', currency: 'JPY', amount: 5000, kind: 'closed' as const},
    ];
    const fixture = mount(goalMonth(), {...COMPUTED, activity});
    fixture.detectChanges();
    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('.actrow');
    expect(rows.length).toBe(2);
    expect(rows[0].querySelector('.acttag.closed')).toBeNull(); // withdrawal tag
    expect(rows[1].querySelector('.acttag.closed')).toBeTruthy(); // closed tag
  });

  it('should hide the activity card when there is no activity', () => {
    const fixture = mount();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.actrow')).toBeNull();
  });

  it('should open the dialog on Add debt, commit on save, then allow edits and removal', () => {
    const page = mount().componentInstance;
    // Add debt opens the dialog on a fresh draft rather than dropping a blank row.
    page.addDebt();
    expect(page.month().debts).toHaveLength(0);
    expect(page.editedDebt()).not.toBeNull();

    // Saving the new debt through applyDebt pushes it (then clears the draft).
    const draft = page.editedDebt()!;
    page.applyDebt({...draft, name: 'Mortgage', principal: 5000000, monthly: 38000});
    expect(page.month().debts).toHaveLength(1);
    expect(page.editedDebt()).toBeNull();
    expect(page.month().debts[0].name).toBe('Mortgage');
    expect(page.month().debts[0].principal).toBe(5000000);

    // The committed debt stays inline-editable (monthly) and removable.
    page.setDebt(0, 'monthly', '40000');
    expect(page.month().debts[0].monthly).toBe(40000);
    expect(page.debtMonthsLabel(page.month().debts[0])).toBe('—'); // no projection yet
    page.removeDebt(0);
    expect(page.month().debts).toHaveLength(0);
  });

  it('should import a valid budget envelope and reject a malformed one', async () => {
    const page = mount().componentInstance;

    const valid = JSON.stringify({app: 'tokyo-budget', version: 1, data: {
      ...emptyMonth(), salaries: [{name: 'A', currency: 'JPY', engine: 'generic', components: [], deductions: [], variables: []}],
    }});
    await page.importJson({target: {files: [new File([valid], 'b.json')], value: ''}} as unknown as Event);
    http.match(isCompute).forEach((r) => r.flush(COMPUTED));
    expect(page.month().salaries).toHaveLength(1);
    expect(page.importError()).toBeNull();

    await page.importJson({target: {files: [new File(['{bad'], 'x.json')], value: ''}} as unknown as Event);
    expect(page.importError()).not.toBeNull();
  });

  it('should save and delete a salary preset through the store', () => {
    const page = mount().componentInstance;

    page.saveSalaryPreset({
      name: 'Weekend gig',
      salary: {name: 'A', currency: 'JPY', engine: 'generic', components: [], deductions: [], variables: []},
    });
    const create = http.expectOne('/api/budget/presets');
    expect(create.request.method).toBe('POST');
    expect(create.request.body).toEqual({
      name: 'Weekend gig',
      salary: {name: 'A', currency: 'JPY', engine: 'generic', components: [], deductions: [], variables: []},
    });
    create.flush({uuid: 'p1', name: 'Weekend gig', builtIn: false, salary: {}});
    // Creating a preset reloads the list.
    http.expectOne('/api/budget/presets').flush([{uuid: 'p1', name: 'Weekend gig', builtIn: false, salary: {}}]);
    expect(page.store.presets()).toHaveLength(1);

    page.deleteSalaryPreset('p1');
    const remove = http.expectOne('/api/budget/presets/p1');
    expect(remove.request.method).toBe('DELETE');
    remove.flush(null);
    http.expectOne('/api/budget/presets').flush([]);
    expect(page.store.presets()).toHaveLength(0);
  });

  it('should discard unsaved edits, reloading the month and clearing dirty', () => {
    const page = mount().componentInstance;
    page.addExpense(); // unsaved edit -> dirty
    expect(page.store.dirty()).toBe(true);

    page.store.discard();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/') && r.method === 'GET').flush(emptyMonth());
    // The discard reload replaces the working month, so the added expense is gone.
    expect(page.store.dirty()).toBe(false);
    expect(page.month().expenses).toHaveLength(1);
  });

  it('should reflect the store dirty flag in the floatbar dot and discard control', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    const bar = () => (fixture.nativeElement as HTMLElement).querySelector('.floatbar')!;

    fixture.detectChanges();
    // Clean state: the status dot has no .unsaved modifier and no Discard button is shown.
    expect(bar().querySelector('.dot')!.classList.contains('unsaved')).toBe(false);
    expect(bar().querySelectorAll('.btn').length).toBe(1); // Save only

    page.addExpense(); // dirty
    fixture.detectChanges();
    http.match(isCompute).forEach((r) => r.flush(COMPUTED));
    fixture.detectChanges();
    expect(bar().querySelector('.dot')!.classList.contains('unsaved')).toBe(true);
    expect(bar().querySelectorAll('.btn').length).toBe(2); // Discard + Save
  });

  it('should refresh FX rates on demand', () => {
    const page = mount().componentInstance;
    page.refreshFx();
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.4});
    http.expectOne((r) => r.url.includes('currency-api')).flush({jpy: {php: 0.36}});
    const phpRow = page.fxEntries().find((entry) => entry.code === 'PHP');
    expect(phpRow?.rate).toBe(0.4);
  });

  it('should add a market currency from the dropdown and seed its rate', () => {
    // Seed the market feed with USD (plus the already-present PHP) so USD is addable.
    const page = mount(emptyMonth(), COMPUTED, {jpy: {php: 0.36, usd: 0.0067}}).componentInstance;

    // The dropdown offers only priced currencies not yet in the month (PHP is already listed).
    expect(page.addableCurrencies()).toEqual(['USD']);

    // Adding mutates the month and seeds the rate into the WORKING fx map (no PUT) — the seed is
    // persisted only on Save. Both the mutate and the rate seed queue a debounced recompute.
    vi.useFakeTimers();
    try {
      page.addCurrency('USD');
      expect(page.month().cur).toHaveLength(3);
      expect(page.month().cur[2]).toEqual({code: 'USD', sym: '$'});
      // The market quote is seeded into the working rate map (alongside the mount's {PHP: 0.36}).
      expect(page.store.fxRates()).toEqual({PHP: 0.36, USD: 0.0067});
      expect(page.store.dirty()).toBe(true);
      http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT'); // nothing persisted
      vi.advanceTimersByTime(250); // settle the debounced recompute
      const request = http.expectOne(isCompute);
      expect(request.request.body.fxRates).toEqual({PHP: 0.36, USD: 0.0067}); // seeded rate sent to /compute
      request.flush(COMPUTED);
    } finally {
      vi.useRealTimers();
    }

    // USD is now listed, so the dropdown no longer offers it.
    expect(page.addableCurrencies()).toEqual([]);
  });

  it('should ignore the placeholder option and a code with no market rate', () => {
    const page = mount().componentInstance; // market feed prices only PHP
    page.addCurrency(''); // placeholder
    page.addCurrency('USD'); // not priced
    expect(page.month().cur).toHaveLength(2);
    http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
  });

  it('should guard removal of an in-use currency and the base, allowing an unused one', () => {
    const month: BudgetMonth = {
      ...emptyMonth(),
      expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}, {label: 'Rent', amt: 150000, cur: 'PHP'}],
      cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}, {code: 'USD', sym: '$'}],
    };
    const page = mount(month).componentInstance;

    expect(page.currencyInUse('JPY')).toBe(true);  // the base
    expect(page.currencyInUse('PHP')).toBe(true);  // referenced by the Rent expense
    expect(page.currencyInUse('USD')).toBe(false); // unreferenced
    expect(page.canRemoveCurrency(0)).toBe(false); // base
    expect(page.canRemoveCurrency(1)).toBe(false); // in use

    page.removeCurrency(1); // guarded — PHP stays
    expect(page.month().cur).toHaveLength(3);

    expect(page.canRemoveCurrency(2)).toBe(true); // USD is free to drop
    page.removeCurrency(2);
    expect(page.month().cur.map((c) => c.code)).toEqual(['JPY', 'PHP']);
  });

  it('should keep at least one currency', () => {
    const page = mount({...emptyMonth(), cur: [{code: 'JPY', sym: '¥'}]}).componentInstance;
    expect(page.canRemoveCurrency(0)).toBe(false);
    page.removeCurrency(0); // blocked — never drop the base
    expect(page.month().cur).toHaveLength(1);
  });

  // A minimal DragEvent stand-in: jsdom doesn't construct a real DataTransfer, so supply the bits the
  // handlers touch (effectAllowed/dropEffect/setData/getData) plus a no-op preventDefault.
  function dragEvent(): DragEvent {
    const dataTransfer = {
      effectAllowed: '', dropEffect: '', data: {} as Record<string, string>,
      setData(format: string, value: string) { this.data[format] = value; },
      getData(format: string) { return this.data[format] ?? ''; },
    };

    return {preventDefault() {}, dataTransfer} as unknown as DragEvent;
  }

  it('should reorder currencies on a drop without changing the base', () => {
    // Three currencies; drag the third (USD) onto the second (PHP) slot — the base (JPY) is untouched.
    const month: BudgetMonth = {...emptyMonth(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}, {code: 'USD', sym: '$'}]};
    const page = mount(month).componentInstance;

    page.onCurrencyDragStart(2, dragEvent());
    expect(page.draggingCurrency()).toBe(2);
    page.onCurrencyDragOver(1, dragEvent());
    expect(page.dropTargetCurrency()).toBe(1);
    page.onCurrencyDrop(1, dragEvent());

    expect(page.month().cur.map((c) => c.code)).toEqual(['JPY', 'USD', 'PHP']);
    // The base didn't change, so no re-fetch fired and the drag state cleared.
    expect(page.draggingCurrency()).toBeNull();
    expect(page.dropTargetCurrency()).toBeNull();
    http.expectNone((r) => r.url.startsWith('/api/budget/fx') && r.method === 'GET');
  });

  it('should rebase by inverting stored rates client-side when a currency is dropped at the top slot', () => {
    const month: BudgetMonth = {...emptyMonth(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]};
    const page = mount(month).componentInstance; // mount seeds the stored rate {PHP: 0.36}

    // Drag PHP (index 1) onto the top slot (index 0): PHP becomes the base.
    page.onCurrencyDragStart(1, dragEvent());
    page.onCurrencyDrop(0, dragEvent());

    expect(page.month().cur.map((c) => c.code)).toEqual(['PHP', 'JPY']);
    // The stored ¥1 = ₱0.36 is inverted CLIENT-SIDE to ₱1 = ¥2.778 (1/0.36); the old PHP entry is
    // dropped (PHP is the base now). The backend holds no PHP-keyed rates, so NO stored-fx GET fires —
    // a GET would return {} and discard the user's rate.
    const rates = page.store.fxRates();
    expect(rates['PHP']).toBeUndefined();
    expect(rates['JPY']).toBeCloseTo(1 / 0.36, 6);
    http.expectNone((r) => r.url.startsWith('/api/budget/fx') && r.method === 'GET');
    // The rebase re-fetches the live market quotes for the new base (PHP).
    http.expectOne((r) => r.url.endsWith('/currencies/php.json')).flush({php: {jpy: 2.637}});
  });

  it('should invert a third currency correctly on rebase', () => {
    // Base JPY with two stored rates: ¥1 = ₱0.36 and ¥1 = $0.0067.
    const month: BudgetMonth = {...emptyMonth(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}, {code: 'USD', sym: '$'}]};
    const page = mount(month, COMPUTED, {jpy: {php: 0.36, usd: 0.0067}}).componentInstance;
    page.store.setFxRate('JPY', 'USD', 0.0067); // seed USD alongside the mounted {PHP: 0.36}

    // Rebase to PHP: every rate re-expresses against PHP. JPY → 1/0.36; USD → 0.0067/0.36.
    page.onCurrencyDragStart(1, dragEvent());
    page.onCurrencyDrop(0, dragEvent());

    expect(page.month().cur.map((c) => c.code)).toEqual(['PHP', 'JPY', 'USD']);
    const rates = page.store.fxRates();
    expect(rates['PHP']).toBeUndefined();
    expect(rates['JPY']).toBeCloseTo(1 / 0.36, 6);
    expect(rates['USD']).toBeCloseTo(0.0067 / 0.36, 6);
    http.expectNone((r) => r.url.startsWith('/api/budget/fx') && r.method === 'GET');
    http.expectOne((r) => r.url.endsWith('/currencies/php.json')).flush({php: {jpy: 2.637, usd: 0.0186}});
  });

  it('should label the add-currency options "CODE — Name" from the catalog', () => {
    // The catalog names JPY/PHP/USD; the market prices USD, so USD is the one addable option.
    const page = mount(emptyMonth(), COMPUTED, {jpy: {php: 0.36, usd: 0.0067}},
      {jpy: 'Japanese Yen', php: 'Philippine Peso', usd: 'US Dollar'}).componentInstance;

    expect(page.addableCurrencyOptions()).toEqual([{code: 'USD', label: 'USD — US Dollar'}]);
  });

  it('should fall back to the bare code when the catalog has no name for a currency', () => {
    // The catalog is empty (offline), so the addable USD option degrades to its bare code.
    const page = mount(emptyMonth(), COMPUTED, {jpy: {php: 0.36, usd: 0.0067}}, {}).componentInstance;

    expect(page.addableCurrencyOptions()).toEqual([{code: 'USD', label: 'USD'}]);
  });

  // The inline principal-prepayment sub-row under each prepay-flagged debt in Money out: a currency
  // toggle + amount input that edit the working month through the store's mutate-based helpers.
  describe('inline debt prepayment sub-row', () => {

    // A month with one prepayment-flagged debt and a non-flagged one, so only the flagged debt's
    // sub-row should render.
    function debtsMonth(): BudgetMonth {
      return {
        ...emptyMonth(),
        debts: [
          {name: 'Mortgage', principal: 5000000, annualRate: 1.5, monthly: 38000, cur: 'JPY', prepay: true, prepayAmt: 50000, prepayCur: 'JPY', rateSteps: []},
          {name: 'Car loan', principal: 1000000, annualRate: 4, monthly: 20000, cur: 'JPY', prepay: false, prepayAmt: 0, rateSteps: []},
        ],
      };
    }

    it('should render a sub-row with an amount input only for a prepay-flagged debt', () => {
      const fixture = mount(debtsMonth());
      fixture.detectChanges(); // render the flushed month into the DOM
      const host = fixture.nativeElement as HTMLElement;
      const subrows = host.querySelectorAll('.row.subrow');
      // Only the flagged Mortgage gets a sub-row, not the non-flagged Car loan.
      expect(subrows.length).toBe(1);

      const subrow = subrows[0];
      expect((subrow.querySelector('input[type=number]') as HTMLInputElement).value).toBe('50000');
      // The sub-row's app-currency-picker renders a .curtog with one button per listed currency.
      expect(subrow.querySelectorAll('.curtog button').length).toBe(2);
    });

    it('should route a sub-row amount edit through the store helper, updating the month and dirtying', () => {
      const page = mount(debtsMonth()).componentInstance;
      const spy = vi.spyOn(page.store, 'setDebtPrepayAmount');

      page.setDebtPrepayAmount(0, 75000);
      expect(spy).toHaveBeenCalledWith(0, 75000);
      // The mutate-based helper updated the working month and marked it dirty.
      expect(page.month().debts[0].prepayAmt).toBe(75000);
      expect(page.store.dirty()).toBe(true);
    });

    it('should coerce a cleared (NaN) sub-row amount to zero through the store helper', () => {
      const page = mount(debtsMonth()).componentInstance;
      page.setDebtPrepayAmount(0, NaN);
      expect(page.month().debts[0].prepayAmt).toBe(0);
    });

    it('should route a sub-row currency toggle through the store helper, updating prepayCur', () => {
      const page = mount(debtsMonth()).componentInstance;
      const spy = vi.spyOn(page.store, 'setDebtPrepayCurrency');

      page.setDebtPrepayCurrency(0, 'PHP');
      expect(spy).toHaveBeenCalledWith(0, 'PHP');
      expect(page.month().debts[0].prepayCur).toBe('PHP');
      expect(page.prepayCurrencyOf(page.month().debts[0])).toBe('PHP');
      expect(page.store.dirty()).toBe(true);
    });

    it('should default the sub-row currency to the debt currency when prepayCur is unset', () => {
      const month: BudgetMonth = {
        ...emptyMonth(),
        debts: [{name: 'Loan', principal: 100, annualRate: 3, monthly: 10, cur: 'PHP', prepay: true, prepayAmt: 5, rateSteps: []}],
      };
      const page = mount(month).componentInstance;
      expect(page.prepayCurrencyOf(page.month().debts[0])).toBe('PHP');
    });
  });

  afterEach(() => {
    // Drain any debounced compute and any in-flight live market fetch that a base change kicked off.
    http.match(isCompute).forEach((r) => r.flush(COMPUTED));
    http.match((r) => r.url.includes('currency-api')).forEach((r) => r.flush({}));
  });
});
