import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {BudgetPage} from './budget-page';
import {BudgetMonth, Computed, Goal} from '../../models/budget.models';

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
};

describe('BudgetPage interactions', () => {

  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  // Mount and settle the initial load (month + compute + fx). Mutations afterwards queue a
  // debounced (250ms) compute that does not fire within these synchronous tests.
  function mount(): ComponentFixture<BudgetPage> {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(emptyMonth());
    http.expectOne('/api/budget/compute').flush(COMPUTED);
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
    expect(page.goalTargetLabel({target: {type: 'open'}} as Goal)).toContain('open');
    expect(page.goalTargetLabel({target: {type: 'amount', amount: 36000000}} as Goal)).toContain('target');
    expect(page.goalTargetLabel({target: {type: 'relative', base: 'all', mult: 6}} as Goal)).toContain('all');
    page.removeGoal(0);
    expect(page.month().goals).toHaveLength(0);
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
    http.match('/api/budget/compute').forEach((r) => r.flush(COMPUTED));
    expect(page.month().salaries).toHaveLength(1);
    expect(page.importError()).toBeNull();

    await page.importJson({target: {files: [new File(['{bad'], 'x.json')], value: ''}} as unknown as Event);
    expect(page.importError()).not.toBeNull();
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
    http.match('/api/budget/compute').forEach((r) => r.flush(COMPUTED));
  });
});
