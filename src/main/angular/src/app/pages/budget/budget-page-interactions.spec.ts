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
  savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 0, salaryNet: {}, debts: [],
  goalProgress: [], savingsBalance: 0, activity: [],
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
                 computed: Computed = COMPUTED): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(month);
    http.expectOne(isCompute).flush(computed);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    return fixture;
  }

  it('should add and remove a salary', () => {
    const page = mount().componentInstance;
    page.addSalary();
    expect(page.month().salaries).toHaveLength(1);
    page.setSalaryName(0, 'Earner A');
    page.setSalaryBasic(0, 500000);
    page.setSalaryCurrency(0, 'PHP');
    expect(page.month().salaries[0].name).toBe('Earner A');
    expect(page.salaryBasicAmount(page.month().salaries[0])).toBe(500000);
    expect(page.month().salaries[0].currency).toBe('PHP');
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

  it('should add and edit a goal and label every target type', () => {
    const page = mount().componentInstance;
    page.addGoal();
    page.setGoal(0, 'label', 'NISA');
    page.setGoal(0, 'amt', '100000');
    page.setGoal(0, 'cur', 'JPY');
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

  it('should add, edit, and remove a debt', () => {
    const page = mount().componentInstance;
    page.addDebt();
    page.setDebt(0, 'name', 'Mortgage');
    page.setDebt(0, 'principal', '5000000');
    page.setDebt(0, 'monthly', '38000');
    expect(page.month().debts[0].name).toBe('Mortgage');
    expect(page.month().debts[0].principal).toBe(5000000);
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
    expect(page.fxEntries()).toContainEqual({code: 'PHP', rate: 0.4});
  });

  it('should add, reorder the base, and keep at least one currency', () => {
    const page = mount().componentInstance;

    page.addCurrency();
    expect(page.month().cur).toHaveLength(3);
    expect(page.month().cur[2].code).toBe('USD');

    // Promote PHP to the base; the base change refreshes rates.
    page.moveCurrency(1, -1);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({JPY: 2.77});
    expect(page.baseCurrency().code).toBe('PHP');

    page.removeCurrency(2); // USD
    page.removeCurrency(1); // JPY
    expect(page.month().cur).toHaveLength(1);
    page.removeCurrency(0); // blocked — never drop the base
    expect(page.month().cur).toHaveLength(1);
  });

  afterEach(() => {
    // Drain any debounced compute that may have fired, then verify nothing unexpected.
    http.match(isCompute).forEach((r) => r.flush(COMPUTED));
  });
});
