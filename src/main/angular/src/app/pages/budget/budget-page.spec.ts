import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {provideTranslateService} from '@ngx-translate/core';
import {BudgetPage} from './budget-page';
import {BudgetMonth, Computed, Debt, NEVER_AMORTIZES} from '../../models/budget.models';
import {DeductionBase} from '../../models/deduction-base';
import {DeductionType} from '../../models/deduction-type';
import {GoalTargetType} from '../../models/goal-target-type';

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
  goalProgress: [], savingsBalance: 0, activity: [], prepayYear: [],
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
    // The page also fetches live market rates and the currency catalog client-side on mount. The
    // per-base rates URL carries the base in its path (…/currencies/jpy.json); the catalog URL is
    // the bare …/currencies.json, so match each precisely.
    http.expectOne((request) => request.url.endsWith('/currencies.json')).flush({jpy: 'Japanese Yen', php: 'Philippine Peso'});
    http.expectOne((request) => request.url.endsWith('/currencies/jpy.json')).flush({jpy: {php: 0.36}});
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

  it('should render a per-currency toggle (not a select) on an editable expense row', () => {
    // Two currencies so the app-currency-picker renders its .curtog with a button each; the
    // JPY-priced Rent row is the editable one.
    const month: BudgetMonth = {
      ...monthWithTithe(),
      cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
    };
    const host = mount(month).nativeElement as HTMLElement;
    const rows = Array.from(host.querySelectorAll('.row'));
    const rentRow = rows.find((row) => (row.querySelector('input.nameinput') as HTMLInputElement | null)?.value === 'Rent') as HTMLElement;
    expect(rentRow).toBeTruthy();

    // The picker renders a .curtog with one button per listed currency (two currencies), not a <select>.
    const toggle = rentRow.querySelector('.curtog');
    expect(toggle).toBeTruthy();
    expect(rentRow.querySelector('select.cursel')).toBeNull();
    expect(rentRow.querySelector('select')).toBeNull();
    const buttons = Array.from(toggle!.querySelectorAll('button'));
    expect(buttons.length).toBe(2);
    // The base (JPY) button is the pressed one for a JPY-priced expense; the other is not.
    const pressed = buttons.filter((button) => button.getAttribute('aria-pressed') === 'true');
    expect(pressed.length).toBe(1);
    expect(pressed[0].getAttribute('title')).toBe('JPY');
  });

  it('should set the expense currency from the per-row toggle', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
    };
    const fixture = mount(month);
    const host = fixture.nativeElement as HTMLElement;
    const rows = Array.from(host.querySelectorAll('.row'));
    const rentRow = rows.find((row) => (row.querySelector('input.nameinput') as HTMLInputElement | null)?.value === 'Rent') as HTMLElement;
    // The Rent expense is the second entry (index 1); clicking the PHP button in the picker's toggle
    // emits changed, which routes through setExpense to retarget the row's currency.
    const phpButton = Array.from(rentRow.querySelectorAll('.curtog button'))
      .find((button) => button.getAttribute('title') === 'PHP') as HTMLButtonElement;
    phpButton.click();
    expect(fixture.componentInstance.month().expenses[1].cur).toBe('PHP');
  });

  it('should open the income dialog on Add income without adding a row, and commit on save', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    expect(page.month().salaries).toHaveLength(0);

    // Add income opens the dialog on a fresh draft: the salary list is unchanged but editedSalary()
    // resolves the draft, so the dialog mounts.
    page.addSalary();
    expect(page.month().salaries).toHaveLength(0);
    expect(page.editedSalary()).not.toBeNull();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-salary-dialog')).toBeTruthy();

    // Saving the new income via applySalary pushes it (then clears the draft and closes the dialog).
    page.applySalary(page.editedSalary()!);
    expect(page.month().salaries).toHaveLength(1);
    expect(page.editedSalary()).toBeNull();
  });

  it('should render the income breakdown gross, deduction, and net lines for a salary with deductions', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        // The salblock-vs-simple-row choice keys on the salary's configured deductions (matching the
        // prototype), so a salary that produces deduction lines must carry them in its config.
        deductions: [
          {label: 'Income tax', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 10, pretax: false, varAuto: false},
          {label: 'Pension', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 6, pretax: false, varAuto: false},
        ],
        variables: [],
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
    // The single pay component is itemized above Gross when the breakdown has deductions (matching
    // the prototype, which loops every component): component, gross subtotal, two deductions, net.
    expect(lines.length).toBe(5);
    expect(lines[0].querySelector('.sl')!.textContent).toContain('Basic salary');
    expect(lines[0].querySelector('.amt')!.textContent).toContain('¥500,000');
    // Gross is read-only inline (editable only in the dialog), so the gross subtotal is a static
    // .amt span, never a number input.
    expect(lines[1].classList.contains('subtotal')).toBe(true);
    expect(lines[1].querySelector('input[type=number]')).toBeNull();
    expect(lines[1].querySelector('.amt')!.textContent).toContain('¥500,000');
    expect(lines[2].querySelector('.amt')!.textContent).toContain('−¥50,000');
    expect(lines[3].querySelector('.amt')!.textContent).toContain('−¥30,000');
    expect(lines[4].classList.contains('net')).toBe(true);
    expect(lines[4].querySelector('.amt')!.textContent).toContain('¥420,000');
  });

  it('should render a deduction-less salary as a simple inline row, not a salblock', () => {
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

    // Prototype parity (incomeBlockHTML no-lines branch): an income with no deductions renders as a
    // simple inline row — name input, currency picker, an inline-editable amount, and a remove × —
    // not a .salblock with a redundant Gross == Net breakdown, and with no edit pencil. The Money-in
    // card is the first card inside .split (the translate pipe echoes keys in tests, so don't match
    // on the heading text).
    const card = mount(month, computed).nativeElement as HTMLElement;
    const moneyIn = card.querySelector('.split > .card') as HTMLElement;
    expect(moneyIn.querySelector('.salblock')).toBeNull();
    expect(moneyIn.querySelector('.salbody')).toBeNull();

    const incomeRow = moneyIn.querySelector('.rows > .row:not(.total):not(.free)') as HTMLElement;
    expect(incomeRow).toBeTruthy();
    expect((incomeRow.querySelector('.nm .nameinput') as HTMLInputElement).value).toBe('Side gig');
    expect(incomeRow.querySelector('.nm .nmedit')).toBeNull(); // no pencil on the simple row
    expect(incomeRow.querySelector('app-currency-picker')).toBeTruthy();
    expect((incomeRow.querySelector('.ctrlrow input[type=number]') as HTMLInputElement).valueAsNumber).toBe(100000);
  });

  it('should render a per-component row plus the gross row for a multi-component salary', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [
          {label: 'Basic salary', amount: 400000, taxable: true, basic: true, varAuto: false},
          {label: 'Housing', amount: 100000, taxable: false, basic: false, varAuto: false},
        ],
        deductions: [{label: 'Income tax', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 10, pretax: false, varAuto: false}],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 450000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Income tax', amount: 50000}], net: 450000,
      }],
    };

    const breakdown = mount(month, computed).nativeElement.querySelector('.salbody') as HTMLElement;
    const lines = Array.from(breakdown.querySelectorAll('.salline'));
    // Two component rows, the gross subtotal, one deduction, then net take-home.
    expect(lines.length).toBe(5);
    expect(lines[0].classList.contains('subtotal')).toBe(false);
    expect(lines[0].querySelector('.amt')!.textContent).toContain('¥400,000');
    expect(lines[1].querySelector('.amt')!.textContent).toContain('¥100,000');
    expect(lines[2].classList.contains('subtotal')).toBe(true);
    expect(lines[2].querySelector('.amt')!.textContent).toContain('¥500,000');
  });

  it('should itemize a single-component salary above gross when it has deductions', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [{label: 'Income tax', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 10, pretax: false, varAuto: false}],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 450000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Income tax', amount: 50000}], net: 450000,
      }],
    };

    const breakdown = mount(month, computed).nativeElement.querySelector('.salbody') as HTMLElement;
    const lines = Array.from(breakdown.querySelectorAll('.salline'));
    // The single component is listed above Gross (prototype parity): component, gross, deduction, net.
    expect(lines.length).toBe(4);
    expect(lines[0].classList.contains('subtotal')).toBe(false);
    expect(lines[0].querySelector('.sl')!.textContent).toContain('Basic salary');
    expect(lines[0].querySelector('.amt')!.textContent).toContain('¥500,000');
    expect(lines[1].classList.contains('subtotal')).toBe(true);
    expect(lines[1].querySelector('.amt')!.textContent).toContain('¥500,000');
  });

  it('should render the "% of base" note key on a percentage deduction', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [{label: 'Income tax', type: DeductionType.Pct, base: DeductionBase.Gross, rate: 10, pretax: false, varAuto: false}],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 450000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Income tax', amount: 50000}], net: 450000,
      }],
    };

    const fixture = mount(month, computed);
    const note = fixture.componentInstance.deductionNote(fixture.componentInstance.month().salaries[0], 0);
    // No i18n JSON is loaded, so assert the key + params, not the rendered string (B.7).
    expect(note?.key).toBe('budget.income.note.pct');
    expect(note?.params['rate']).toBe(10);
    // The base label resolves through the translate service; with no JSON it echoes the base key.
    expect(note?.params['base']).toBe('budget.income.base.gross');

    // The note renders inside the deduction line as a .dednote caption (the key echoed by the pipe).
    const dednote = (fixture.nativeElement as HTMLElement).querySelector('.salbody .salline .dednote');
    expect(dednote?.textContent).toContain('budget.income.note.pct');
  });

  it('should suffix the percentage note key with "capped" when a cap is set', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [{label: 'Pension', type: DeductionType.Pct, base: DeductionBase.Basic, rate: 9, cap: 60000, pretax: true, varAuto: false}],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 440000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Pension', amount: 45000}], net: 455000,
      }],
    };

    const page = mount(month, computed).componentInstance;
    const note = page.deductionNote(page.month().salaries[0], 0);
    expect(note?.key).toBe('budget.income.note.pctCapped');
    expect(note?.params['base']).toBe('budget.income.base.basic');
  });

  it('should render the formula note key on a formula deduction', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [{label: 'National tax', type: DeductionType.Formula, expr: '0.2*annual', pretax: false, varAuto: false}],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 400000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'National tax', amount: 100000}], net: 400000,
      }],
    };

    const page = mount(month, computed).componentInstance;
    expect(page.deductionNote(page.month().salaries[0], 0)).toEqual({key: 'budget.income.note.formula', params: {}});
  });

  it('should pluralize the brackets note key on the rule count', () => {
    const month: BudgetMonth = {
      ...monthWithTithe(),
      salaries: [{
        name: 'Day job', currency: 'JPY', engine: 'generic',
        components: [{label: 'Basic salary', amount: 500000, taxable: true, basic: true, varAuto: false}],
        deductions: [
          {label: 'Tax A', type: DeductionType.Brackets, brackets: [{val: 0}], pretax: false, varAuto: false},
          {label: 'Tax B', type: DeductionType.Brackets, brackets: [{val: 0}, {val: 100000}], pretax: false, varAuto: false},
        ],
        variables: [],
      }],
    };
    const computed: Computed = {
      ...COMPUTED,
      salaryNet: {'Day job': 400000},
      salaryBreakdown: [{
        name: 'Day job', currency: 'JPY', gross: 500000,
        deductions: [{label: 'Tax A', amount: 50000}, {label: 'Tax B', amount: 50000}], net: 400000,
      }],
    };

    const page = mount(month, computed).componentInstance;
    // Singular for one rule, plural for two — both carry n as a param.
    expect(page.deductionNote(page.month().salaries[0], 0)).toEqual({key: 'budget.income.note.bracketsOne', params: {n: 1}});
    expect(page.deductionNote(page.month().salaries[0], 1)).toEqual({key: 'budget.income.note.brackets', params: {n: 2}});
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

  it('should render a drag handle on each currency row and label the add-currency options from the catalog', () => {
    // Two currencies listed, market prices USD, catalog names USD — so the picker offers "USD — US Dollar".
    const month: BudgetMonth = {...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]};
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(month);
    http.expectOne(isCompute).flush(COMPUTED);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    http.expectOne((r) => r.url.endsWith('/currencies.json')).flush({usd: 'US Dollar'});
    http.expectOne((r) => r.url.endsWith('/currencies/jpy.json')).flush({jpy: {php: 0.36, usd: 0.0067}});
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    // A draggable grip handle sits on every currency row.
    const grips = host.querySelectorAll('.currow .cur-grip[draggable="true"]');
    expect(grips.length).toBe(2);

    // The add-currency dropdown labels USD with its catalog name (the placeholder is the first option).
    const options = Array.from(host.querySelectorAll('.curmgr-add option')).map((o) => o.textContent?.trim());
    expect(options).toContain('USD — US Dollar');
  });

  it('should render one unified row per currency: a base flag on the base, a rate control + reciprocal on a non-base', () => {
    // JPY base + PHP non-base; PHP is priced so an editable rate control belongs in PHP's own row.
    const month: BudgetMonth = {...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]};
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges();
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(month);
    http.expectOne(isCompute).flush(COMPUTED);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    http.expectOne((r) => r.url.endsWith('/currencies.json')).flush({});
    http.expectOne((r) => r.url.endsWith('/currencies/jpy.json')).flush({jpy: {php: 0.36}});
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    // Two unified rows: the first is the base, the second is the non-base PHP.
    const rows = host.querySelectorAll('.curlist .currow');
    expect(rows).toHaveLength(2);

    // The base (first) row carries the base flag and no rate control or remove button.
    const baseRow = rows[0];
    expect(baseRow.classList.contains('base')).toBe(true);
    expect(baseRow.querySelector('.cur-baseflag')).not.toBeNull();
    expect(baseRow.querySelector('input[type="range"]')).toBeNull();
    expect(baseRow.querySelector('.cur-rm.off')).not.toBeNull();

    // The non-base row carries its own rate slider, the rate line, and the reciprocal — all inline.
    const phpRow = rows[1];
    expect(phpRow.classList.contains('base')).toBe(false);
    expect(phpRow.querySelector('input[type="range"]')).not.toBeNull();
    expect(phpRow.querySelector('.cur-rate')?.textContent).toContain('¥1 = ₱');
    expect(phpRow.querySelector('.cur-inv')?.textContent).toContain('1 ₱ = ¥');
  });

  it('should update the fx state and recompute against the working rate when a rate is edited', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    // Add a non-base currency so an editable rate row exists.
    page.store.setMonth({...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]});
    http.expectOne(isCompute).flush(COMPUTED);

    // A rate edit no longer PUTs: it updates the working map and recomputes (debounced) against it; the
    // rate is persisted only on Save. Advance the recompute debounce and assert it rode in the body.
    vi.useFakeTimers();
    try {
      page.setRate('PHP', '0.42');
      expect(page.store.fxRates()).toEqual({PHP: 0.42}); // applied locally at once
      expect(page.store.dirty()).toBe(true);
      http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT'); // nothing persisted
      vi.advanceTimersByTime(250); // settle the debounced recompute
      const request = http.expectOne(isCompute);
      expect(request.request.body.fxRates).toEqual({PHP: 0.42}); // the working rate is sent to /compute
      request.flush(COMPUTED);
    } finally {
      vi.useRealTimers();
    }

    const row = page.fxEntries().find((entry) => entry.code === 'PHP');
    expect(row?.rate).toBe(0.42);
  });

  it('should apply a fetched market rate via use-market into the working rate, deferring persistence', () => {
    const fixture = mount();
    const page = fixture.componentInstance;
    page.store.setMonth({...monthWithTithe(), cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}]});
    http.expectOne(isCompute).flush(COMPUTED);
    // Market rates already fetched on mount ({PHP: 0.36}); the row exposes it.
    expect(page.fxEntries().find((entry) => entry.code === 'PHP')?.market).toBe(0.36);

    // useMarket routes through setFxRate, so it too defers: working map + dirty + recompute, no PUT.
    vi.useFakeTimers();
    try {
      page.useMarket('PHP');
      expect(page.store.fxRates()).toEqual({PHP: 0.36});
      expect(page.store.dirty()).toBe(true);
      http.expectNone((r) => r.url === '/api/budget/fx' && r.method === 'PUT');
      vi.advanceTimersByTime(250); // settle the debounced recompute
      const request = http.expectOne(isCompute);
      expect(request.request.body.fxRates).toEqual({PHP: 0.36});
      request.flush(COMPUTED);
    } finally {
      vi.useRealTimers();
    }

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
  // renders the "≈ <home>" cross-rate into the second currency. The stored rate flushed on mount is
  // {PHP: 0.36} (units of PHP per one base ¥).
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

    it('should convert a base-currency amount to the listed home currency', () => {
      const page = mount(multiCurrencyMonth()).componentInstance;
      // A base amount shows the opposite cross-rate (like the prototype's convText base branch):
      // ¥150,000 × 0.36 = ₱54,000 (the home currency is the second listed, PHP).
      expect(page.convB(150000, 'JPY')).toBe('≈ ₱54,000');
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

  // The annual principal-prepayment card renders computed().prepayYear: one row per flagged debt
  // (name + amount in the debt's currency) plus a base-currency total, or an empty state when none.
  describe('annual principal-prepayment card', () => {

    // A month with one prepayment-flagged debt, matched by name to the prepayYear entry below.
    function prepayMonth(): BudgetMonth {
      return {
        ...monthWithTithe(),
        debts: [{
          name: 'Mortgage', principal: 5000000, annualRate: 1.5, monthly: 38000, cur: 'JPY',
          prepay: true, prepayAmt: 50000, rateSteps: [],
        }],
      };
    }

    function findCard(host: HTMLElement): HTMLElement | undefined {
      return Array.from(host.querySelectorAll('section.card'))
        .find((card) => card.querySelector('h2')?.textContent?.includes('budget.prepayYear.title')) as HTMLElement | undefined;
    }

    it('should render one row per prepayYear entry plus a total', () => {
      const computed: Computed = {
        ...COMPUTED,
        prepayYear: [
          {name: 'Mortgage', currency: 'JPY', amount: 600000, amountBase: 600000},
          {name: 'Car loan', currency: 'PHP', amount: 36000, amountBase: 100000},
        ],
      };
      const host = mount(prepayMonth(), computed).nativeElement as HTMLElement;
      const card = findCard(host);
      expect(card).toBeTruthy();

      // Two entry rows plus the total row (.row.total).
      const rows = Array.from(card!.querySelectorAll('.rows .row'));
      expect(rows.length).toBe(3);
      expect(rows[0].textContent).toContain('Mortgage');
      expect(rows[0].querySelector('.val')!.textContent).toContain('¥600,000');
      expect(rows[1].textContent).toContain('Car loan');
      expect(card!.querySelector('.row.total')).toBeTruthy();
      // The total sums amountBase across entries (600,000 + 100,000) in the base currency.
      expect(card!.querySelector('.row.total .val')!.textContent).toContain('¥700,000');
      // No empty-state hint while there are entries (the card still carries its descriptive hint,
      // but the in-rows empty-state row echoing budget.prepayYear.empty must be absent).
      expect(card!.querySelector('.rows .hint')).toBeNull();
      expect(card!.textContent).not.toContain('budget.prepayYear.empty');
    });

    it('should show the empty state when prepayYear is empty', () => {
      const host = mount(monthWithTithe(), {...COMPUTED, prepayYear: []}).nativeElement as HTMLElement;
      const card = findCard(host);
      expect(card).toBeTruthy();
      expect(card!.querySelector('.hint')?.textContent).toContain('budget.prepayYear.empty');
      expect(card!.querySelector('.row.total')).toBeNull();
    });

    it('should sum amountBase across entries for the base-currency total', () => {
      const page = mount(monthWithTithe(), {
        ...COMPUTED,
        prepayYear: [
          {name: 'A', currency: 'JPY', amount: 1, amountBase: 120000},
          {name: 'B', currency: 'PHP', amount: 2, amountBase: 80000},
        ],
      }).componentInstance;
      expect(page.prepayYearTotalBase()).toBe(200000);
    });

    it('should build a rate summary with each step as "after Ny" via the matched debt', () => {
      const month: BudgetMonth = {
        ...monthWithTithe(),
        debts: [{
          name: 'Mortgage', principal: 5000000, annualRate: 1.5, monthly: 38000, cur: 'JPY',
          prepay: true, prepayAmt: 50000,
          rateSteps: [{afterYears: 5, rate: 2.5}, {afterYears: 0, rate: 9}],
        }],
      };
      const page = mount(month, {
        ...COMPUTED, prepayYear: [{name: 'Mortgage', currency: 'JPY', amount: 600000, amountBase: 600000}],
      }).componentInstance;
      // Base rate then the one positive-afterYears step; the afterYears fragment echoes its key (B.7).
      const summary = page.prepayRateSummary('Mortgage');
      expect(summary).toContain('1.5%');
      expect(summary).toContain('2.5%');
      expect(summary).toContain('budget.prepayYear.afterYears');
      // The afterYears:0 step is filtered out (no "9%").
      expect(summary).not.toContain('9%');
    });
  });

  // ngOnInit kicks off the month load and sets store.loading() true. The cards no longer swap to
  // shorter placeholders while loading — the real elements stay rendered and shimmer in place (driven
  // off the card's aria-busy) so nothing resizes. Mount without settling so loading() is still true,
  // assert the busy state + real content render (and the old .skrow/.skchart placeholders are gone),
  // then flush the mount HTTP and assert aria-busy clears.
  it('should shimmer the real cards in place while loading and clear aria-busy once loaded', () => {
    const fixture = TestBed.createComponent(BudgetPage);
    fixture.detectChanges(); // ngOnInit -> load() sets loading() true; month request is in flight
    const host = fixture.nativeElement as HTMLElement;

    // While loading: the card is aria-busy and the REAL metric values + chart still render in place
    // (no placeholder swap). The removed .skrow/.skchart placeholders never appear.
    expect(fixture.componentInstance.store.loading()).toBe(true);
    expect(host.querySelector('[aria-busy="true"]')).toBeTruthy();
    expect(host.querySelector('.metric .mv')).toBeTruthy();
    expect(host.querySelector('app-money-chart')).toBeTruthy();
    expect(host.querySelector('.skrow')).toBeNull();
    expect(host.querySelector('.skchart')).toBeNull();

    // Flush the mount HTTP: month load, compute, presets, stored fx, and the live market fetch.
    http.expectOne((r) => r.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    http.expectOne('/api/budget/presets').flush([]);
    http.expectOne((r) => r.url.startsWith('/api/budget/fx')).flush({PHP: 0.36});
    http.expectOne((r) => r.url.endsWith('/currencies.json')).flush({jpy: 'Japanese Yen'});
    http.expectOne((r) => r.url.endsWith('/currencies/jpy.json')).flush({jpy: {php: 0.36}});
    fixture.detectChanges();

    // Once loaded: aria-busy is gone (so the shimmer lifts) and the real chart + metric values remain.
    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('[aria-busy="true"]')).toBeNull();
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

    // Settle the save: save now persists the working non-base rate first (the mount seeded {PHP: 0.36}),
    // then the month PUT, then the follow-up compute. The bar stops and the overlay lifts once all land.
    http.expectOne((r) => r.url === '/api/budget/fx' && r.method === 'PUT').flush({PHP: 0.36});
    http.expectOne((r) => r.url.startsWith('/api/budget/month/') && r.method === 'PUT').flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.saving()).toBe(false);
    expect(saveBar.classList.contains('run')).toBe(false);
    expect(wrap.classList.contains('saving')).toBe(false);
  });

  // Navigating to another month reuses the load() path, so loading() flips true. The prior month's
  // real content stays rendered mid-switch and just shimmers in place (the card is aria-busy) rather
  // than collapsing to placeholders — this is the case that used to shrink the cards.
  it('should keep the cards rendered and shimmer them in place while navigating to another month', () => {
    const fixture = mount();
    const host = fixture.nativeElement as HTMLElement;

    // Real content is showing (loaded), not busy, before the switch.
    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('[aria-busy="true"]')).toBeNull();
    const chartBeforeNav = host.querySelector('app-money-chart');
    expect(chartBeforeNav).toBeTruthy();

    // Navigate forward a month: load() sets loading() true and the next month's request is in flight.
    fixture.componentInstance.store.navigate(1);
    fixture.detectChanges();

    // Mid-switch: the card is aria-busy and the prior month's chart + metric values are STILL rendered
    // (no placeholder swap), so the cards keep their size. The removed placeholders never appear.
    expect(fixture.componentInstance.store.loading()).toBe(true);
    expect(host.querySelector('[aria-busy="true"]')).toBeTruthy();
    expect(host.querySelector('app-money-chart')).toBeTruthy();
    expect(host.querySelector('.metric .mv')).toBeTruthy();
    expect(host.querySelector('.skrow')).toBeNull();
    expect(host.querySelector('.skchart')).toBeNull();

    // Flush the navigation's month load + compute: the shimmer lifts (aria-busy clears), content stays.
    http.expectOne((request) => request.url.startsWith('/api/budget/month/')).flush(monthWithTithe());
    http.expectOne(isCompute).flush(COMPUTED);
    fixture.detectChanges();

    expect(fixture.componentInstance.store.loading()).toBe(false);
    expect(host.querySelector('[aria-busy="true"]')).toBeNull();
    expect(host.querySelector('app-money-chart')).toBeTruthy();
  });

  // The body was reorganized to match the prototype: the Currencies & FX card moves above the
  // metrics, the separate Goals and Debts cards fold into Money out (under "Savings & goals" and
  // "Debt financing" group headers), and a generic notes footer plus a formatted month label land.
  describe('restructured layout', () => {

    // The Money out card is the second .split card; locate it by its "Money out" heading key.
    function moneyOutCard(host: HTMLElement): HTMLElement {
      return Array.from(host.querySelectorAll('.split section.card'))
        .find((card) => card.querySelector('h2')?.textContent?.includes('budget.page.moneyOut')) as HTMLElement;
    }

    it('should place the Currencies & FX card before the metrics in DOM order', () => {
      const host = mount().nativeElement as HTMLElement;
      const cards = Array.from(host.querySelectorAll('section.card'));
      // The currencies card heads with .fxhead (its heading is a .label, not an h2, matching the prototype).
      const fxIndex = cards.findIndex((card) => card.querySelector('.fxhead'));
      const metricsIndex = cards.findIndex((card) => card.querySelector('.metrics'));
      expect(fxIndex).toBeGreaterThanOrEqual(0);
      expect(metricsIndex).toBeGreaterThanOrEqual(0);
      expect(fxIndex).toBeLessThan(metricsIndex);
    });

    it('should render the metric block as label → value → sub with free cash featured', () => {
      const host = mount().nativeElement as HTMLElement;
      const metrics = Array.from(host.querySelectorAll('.metric'));
      expect(metrics.length).toBe(5);

      // Each metric leads with the uppercase label, then the .mv value, then the .msub sub-line.
      const first = metrics[0];
      const children = Array.from(first.children);
      expect(children[0].classList.contains('label')).toBe(true);
      expect(children[1].classList.contains('mv')).toBe(true);
      expect(children[2].classList.contains('msub')).toBe(true);

      // The featured metric is now Free cash left (third), not Money in.
      const feat = host.querySelector('.metric.feat') as HTMLElement;
      expect(feat).toBeTruthy();
      expect(feat.querySelector('.label')!.textContent).toContain('budget.page.freeCashLeft');
      expect(host.querySelectorAll('.metric.feat').length).toBe(1);
      // Money in is no longer the featured metric.
      expect(metrics[0].classList.contains('feat')).toBe(false);
    });

    it('should fold goals and debts into the Money out card under group headers', () => {
      // A month carrying a goal and a prepay-flagged debt plus the tithe expense.
      const month: BudgetMonth = {
        ...monthWithTithe(),
        goals: [{label: 'Emergency fund', amt: 50000, cur: 'JPY', target: {type: GoalTargetType.Open}, savings: true, wd: 0, closed: false}],
        debts: [{name: 'Mortgage', principal: 5000000, annualRate: 1.5, monthly: 38000, cur: 'JPY', prepay: true, prepayAmt: 50000, prepayCur: 'JPY', rateSteps: []}],
      };
      const host = mount(month).nativeElement as HTMLElement;

      // There is a single .split row now (the old Goals/Debts .split was removed).
      expect(host.querySelectorAll('.split').length).toBe(1);

      const out = moneyOutCard(host);
      // The goal row and the debt row live inside Money out, not a separate card. Their names sit in
      // editable name inputs, so read the input values rather than textContent.
      const names = Array.from(out.querySelectorAll('input.nameinput')).map((i) => (i as HTMLInputElement).value);
      expect(names).toContain('Emergency fund');
      expect(names).toContain('Mortgage');
      // Money out carries the two group headers and the prepay sub-row.
      const heads = Array.from(out.querySelectorAll('.grouphead')).map((h) => h.textContent);
      expect(heads.some((t) => t?.includes('budget.page.savingsAndGoals'))).toBe(true);
      expect(heads.some((t) => t?.includes('budget.page.debtFinancing'))).toBe(true);
      expect(out.querySelector('.row.subrow')).toBeTruthy();
      // Its totals: a "Total money out" total row and a featured free row.
      expect(out.querySelector('.row.total')).toBeTruthy();
      expect(out.querySelector('.row.free')).toBeTruthy();

      // No standalone "Debt payoff" / "Savings & goals" card heading survives outside Money out.
      const cardHeadings = Array.from(host.querySelectorAll('section.card > h2')).map((h) => h.textContent);
      expect(cardHeadings).not.toContain('budget.page.debtPayoff');
    });

    it('should give Money in a Total money in row and the income preset hint', () => {
      const host = mount().nativeElement as HTMLElement;
      const cardIn = Array.from(host.querySelectorAll('.split section.card'))
        .find((card) => card.querySelector('h2')?.textContent?.includes('budget.page.moneyIn')) as HTMLElement;
      expect(cardIn.querySelector('.row.total')!.textContent).toContain('budget.page.totalIn');
      expect(cardIn.textContent).toContain('budget.page.incomePresetHint');
    });

    it('should render the generic notes footer with a disclaimer', () => {
      const host = mount().nativeElement as HTMLElement;
      const notes = host.querySelector('.notes') as HTMLElement;
      expect(notes).toBeTruthy();
      expect(notes.querySelector('.disc')!.textContent).toContain('budget.notes.disclaimer');
      // The how-it-works and per-month-records prose render (keys echoed, no JSON loaded).
      expect(notes.textContent).toContain('budget.notes.howBody');
      expect(notes.textContent).toContain('budget.notes.recordsBody');
    });

    it('should format the floatbar month label as "Month YYYY" rather than the raw key', () => {
      const host = mount().nativeElement as HTMLElement;
      const label = host.querySelector('.floatbar .mlabel')!.textContent ?? '';
      // The store's default month key is the current YYYY-MM; the label is the long-month form.
      expect(label).not.toMatch(/^\d{4}-\d{2}$/);
      expect(label).toMatch(/^[A-Z][a-z]+ \d{4}$/);
    });

    it('should derive monthLabel and currentYear from the month key', () => {
      const page = mount().componentInstance;
      // The default key is the current YYYY-MM; the label is the long-month form, the year its YYYY.
      expect(page.monthLabel()).toMatch(/^[A-Z][a-z]+ \d{4}$/);
      expect(page.currentYear()).toMatch(/^\d{4}$/);
      // The formatted label ends with the calendar year.
      expect(page.monthLabel().endsWith(page.currentYear())).toBe(true);
    });
  });
});
