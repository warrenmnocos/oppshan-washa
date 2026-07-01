import {computed, inject, Injectable, signal} from '@angular/core';
import {forkJoin, Observable, of, Subject} from 'rxjs';
import {auditTime, catchError, switchMap} from 'rxjs/operators';
import {BudgetApiService} from './budget-api.service';
import {BudgetMonth, Computed, Salary, SalaryPresetView} from '../models/budget.models';

/** Cap on forward navigation: the working month can move at most 60 months (5 years) past the current one. */
const FORWARD_LIMIT = 60;

/** Live-fetch lifecycle for market rates: idle, in-flight, succeeded, or unavailable (offline). */
export type FxFetchStatus = 'idle' | 'loading' | 'ready' | 'unavailable';

/** A blank month seeded with the default Tithe expense and the JPY/PHP currency pair. */
function emptyMonth(): BudgetMonth {
  return {
    salaries: [],
    expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}],
    goals: [],
    debts: [],
    cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
  };
}

/**
 * Whether a month carries real input. The backend returns all-empty lists for a month with no saved
 * data (BudgetService.getMonth), so an empty salaries/expenses/goals/debts set means "nothing there".
 * The currency list is ignored: it always comes back populated with the default pair.
 */
function hasData(month: BudgetMonth): boolean {
  return month.salaries.length > 0 || month.expenses.length > 0
      || month.goals.length > 0 || month.debts.length > 0;
}

/**
 * A next-month starting point copied from the month being left: same income, expenses, debts and
 * currencies, with per-month goal events reset. Closed goals drop off (they're finished, not recurring)
 * and each carried goal's one-time withdrawal is zeroed, so only the recurring contribution carries.
 * Mirrors the prototype's carry-into-an-empty-month; the caller marks the result dirty so it saves as a
 * new month rather than persisting silently.
 */
function carriedForward(source: BudgetMonth): BudgetMonth {
  const copy = structuredClone(source);
  copy.goals = copy.goals
      .filter((goal) => !goal.closed)
      .map((goal) => ({...goal, wd: 0}));
  return copy;
}

/** All-zero computed figures, used before the first compute lands and to reset after a failed one. */
function emptyComputed(): Computed {
  return {
    moneyIn: 0, moneyOut: 0, free: 0, tithe: 0, otherExpenses: 0, debt: 0,
    savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 0, salaryNet: {}, salaryBreakdown: [],
    debts: [], goalProgress: [], savingsBalance: 0, activity: [], prepayYear: [],
  };
}

/** Turn a month offset (relative to the current month) into a YYYY-MM key. */
function keyForOffset(offset: number): string {
  const now = new Date();
  const base = new Date(now.getFullYear(), now.getMonth() + offset, 1);
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * Signal store for the working budget month: loads it, applies edits (each marks the month dirty and
 * kicks a debounced recompute), saves, and navigates between months. Reads and writes go through
 * {@link BudgetApiService}, which stays authoritative for every computed figure.
 */
@Injectable({providedIn: 'root'})
export class BudgetStore {

  /** The budget HTTP client this store loads, saves, computes, and fetches rates through. */
  private readonly api = inject(BudgetApiService);

  /** Offset in months from the current calendar month: 0 = this month, negative = past, positive = future. */
  private readonly monthOffsetSignal = signal(0);
  /** The working month being viewed and edited; starts empty until load() resolves. */
  private readonly monthSignal = signal<BudgetMonth>(emptyMonth());
  /** Server-computed figures for the working month (money in/out, savings rate, projections). */
  private readonly computedSignal = signal<Computed>(emptyComputed());
  /** True once the working month has edits not yet saved; cleared by load() and save(). */
  private readonly dirtySignal = signal(false);
  /** True while a month load and its follow-up compute are in flight. */
  private readonly loadingSignal = signal(false);
  /** True while a save (deferred rate upserts, then the month PUT) is in flight. */
  private readonly savingSignal = signal(false);
  /** The shared salary-preset list. */
  private readonly presetsSignal = signal<SalaryPresetView[]>([]);

  /** Working rates against the current base (quote code → units per one base); edited live, persisted only by save(). */
  private readonly fxRatesSignal = signal<Record<string, number>>({});
  /** Last live market quotes fetched client-side, available as an alternative to the working rates. */
  private readonly marketRatesSignal = signal<Record<string, number>>({});
  /** Lifecycle of the client-side market-rate fetch (idle, loading, ready, or unavailable). */
  private readonly fxStatusSignal = signal<FxFetchStatus>('idle');

  /** Currency catalog (code → display name), fetched client-side once; empty until it lands and on offline/blocked. */
  private readonly currencyNamesSignal = signal<Record<string, string>>({});

  /** Emits on each edit to drive the debounced, cancellable recompute wired up in the constructor. */
  private readonly recompute$ = new Subject<void>();

  readonly month = this.monthSignal.asReadonly();
  readonly computed = this.computedSignal.asReadonly();
  readonly dirty = this.dirtySignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly saving = this.savingSignal.asReadonly();
  readonly presets = this.presetsSignal.asReadonly();
  readonly fxRates = this.fxRatesSignal.asReadonly();
  readonly marketRates = this.marketRatesSignal.asReadonly();
  readonly fxStatus = this.fxStatusSignal.asReadonly();
  readonly currencyNames = this.currencyNamesSignal.asReadonly();
  /** The working month as a YYYY-MM key, derived from the current offset. */
  readonly monthKey = computed(() => keyForOffset(this.monthOffsetSignal()));
  /** Whether forward navigation is still allowed (the offset is below FORWARD_LIMIT). */
  readonly canGoForward = computed(() => this.monthOffsetSignal() < FORWARD_LIMIT);

  /**
   * Wire the recompute pipeline. auditTime(80) (not a debounce) re-runs /compute on rapid successive
   * edits LIVE, emitting the latest state every 80ms while the values keep changing with a final pass
   * on release, instead of only settling once they pause; the throttle exists because /compute is
   * server-authoritative. switchMap CANCELS any still-in-flight /compute so a slower earlier response
   * can't land after a newer one and leave the totals showing a stale rate (an out-of-order race under
   * the Lambda's variable latency). catchError sits INSIDE the switchMap so a failed compute resets the
   * totals without terminating the stream; letting the error escape the outer pipe would kill every
   * future recompute.
   */
  constructor() {
    this.recompute$.pipe(
        auditTime(80),
        switchMap(() => this.api.compute(this.monthSignal(), this.monthKey(), this.fxRatesSignal())
            .pipe(catchError(() => of(emptyComputed())))),
    ).subscribe((result) => this.computedSignal.set(result));
  }

  /**
   * Load the current month, then compute its figures. Stays in the loading state THROUGH the follow-up
   * compute: the money-in/out/free figures come from a second /api/budget/compute round-trip after the
   * month structure lands, so clearing loading here would blank them until it resolves and then pop
   * them in. runCompute() clears loading once the figures arrive; a load failure falls back to an empty
   * month. Live edits use the debounced recompute$ path instead, which never touches loading.
   *
   * When navigate() hands over a carrySource (forward move) and the loaded month has no saved data, the
   * month is seeded from that source and marked dirty (the carry-forward path) instead of shown empty.
   */
  load(carrySource: BudgetMonth | null = null): void {
    const key = this.monthKey();
    this.loadingSignal.set(true);
    this.api.getMonth(key).subscribe({
      next: (month) => {
        if (carrySource && !hasData(month)) {
          // Target month has no saved data: seed it from the month we came from, left Unsaved so the
          // user reviews and saves it rather than it persisting silently.
          this.monthSignal.set(carriedForward(carrySource));
          this.dirtySignal.set(true);
        } else {
          this.monthSignal.set(month);
          this.dirtySignal.set(false);
        }
        this.runCompute();
      },
      error: () => {
        this.monthSignal.set(emptyMonth());
        this.loadingSignal.set(false);
      },
    });
  }

  /**
   * Shift the month offset by delta (ignoring a move past FORWARD_LIMIT) and reload that month. Moving
   * FORWARD onto a month with no saved data seeds it from the month being left — a carry-forward
   * starting point, left Unsaved — but only when that month actually has data. The source is captured
   * before the offset changes; load() applies it once it sees the target is empty.
   */
  navigate(delta: number): void {
    const next = this.monthOffsetSignal() + delta;
    if (next > FORWARD_LIMIT) {
      return;
    }
    const leaving = this.monthSignal();
    const carrySource = delta > 0 && hasData(leaving) ? structuredClone(leaving) : null;
    this.monthOffsetSignal.set(next);
    this.load(carrySource);
  }

  /** Drop unsaved edits by reloading the current month from the backend (same path as load()). */
  discard(): void {
    this.load();
  }

  /** Apply a mutation to the working month, mark dirty, and trigger a debounced recompute. */
  mutate(change: (month: BudgetMonth) => void): void {
    const next = structuredClone(this.monthSignal());
    change(next);
    this.monthSignal.set(next);
    this.dirtySignal.set(true);
    this.recompute$.next();
  }

  /**
   * Replace the working month wholesale, mark it dirty, and compute immediately (not debounced) so the
   * figures refresh in one step rather than after the edit window.
   */
  setMonth(month: BudgetMonth): void {
    this.monthSignal.set(month);
    this.dirtySignal.set(true);
    this.runCompute();
  }

  /**
   * Set a debt's monthly principal-prepayment amount, in that debt's prepayment currency. Goes through
   * mutate so the debounced /compute refreshes the derived figures; never write the month signal
   * directly. Non-finite input coerces to 0 (an empty input clears it).
   */
  setDebtPrepayAmount(index: number,
                      value: number): void {
    this.mutate((month) => {
      const debt = month.debts[index];
      if (debt) {
        debt.prepayAmt = isFinite(value) ? value : 0;
      }
    });
  }

  /** Set a debt's prepayment currency (mutate-based, so the debounced /compute follows). */
  setDebtPrepayCurrency(index: number,
                        code: string): void {
    this.mutate((month) => {
      const debt = month.debts[index];
      if (debt) {
        debt.prepayCur = code;
      }
    });
  }

  /**
   * Persist the deferred rate edits first (one upsert per non-base rate), then PUT the month. Rate
   * edits are held back from each edit to here, so save() is where they reach the fx_rate table. When
   * there are no rates to write, use of(null): forkJoin([]) never emits and would hang the save. On
   * success, adopt the persisted month and clear dirty; on failure, just drop the saving flag.
   */
  save(): void {
    this.savingSignal.set(true);
    const base = this.monthSignal().cur[0]?.code;
    const ratePuts = Object.entries(this.fxRatesSignal())
        .filter(([quote, rate]) => base != null && quote !== base && rate > 0)
        .map(([quote, rate]) => this.api.setFxRate(base as string, quote, rate));
    const persisted: Observable<unknown> = ratePuts.length ? forkJoin(ratePuts) : of(null);
    persisted.pipe(switchMap(() => this.api.saveMonth(this.monthKey(), this.monthSignal()))).subscribe({
      next: (saved) => {
        this.monthSignal.set(saved);
        this.dirtySignal.set(false);
        this.savingSignal.set(false);
        this.runCompute();
      },
      error: () => this.savingSignal.set(false),
    });
  }

  /** Load the shared preset list (built-ins first, then alphabetical) from the backend. */
  loadPresets(): void {
    this.api.listPresets().subscribe({next: (presets) => this.presetsSignal.set(presets)});
  }

  /** Persist the current salary draft as a named preset, then refresh the list on success. */
  savePreset(name: string,
             salary: Salary): void {
    this.api.createPreset(name, salary).subscribe({next: () => this.loadPresets()});
  }

  /** Delete a user preset (built-ins are rejected by the backend), then refresh on success. */
  deletePreset(uuid: string): void {
    this.api.deletePreset(uuid).subscribe({next: () => this.loadPresets()});
  }

  /** Load the stored rates for a base into the fx signal (the read side; no compute change). */
  refreshFx(base: string): void {
    this.api.fx(base).subscribe({next: (rates) => this.fxRatesSignal.set(rates)});
  }

  /**
   * Replace the working rate map wholesale, mark dirty, and recompute. Used when a rebase re-expresses
   * every stored rate against a new base client-side, where the backend holds no rates keyed by the new
   * base. The new base carries no self-entry, so passing a map without it drops the old, now-stale
   * entry. Deferred to save() like any rate edit; nothing is persisted here.
   */
  setFxRates(rates: Record<string, number>): void {
    this.fxRatesSignal.set(rates);
    this.dirtySignal.set(true);
    this.recompute$.next();
  }

  /**
   * Fetch live market rates client-side for a base. On success they populate the market signal and the
   * status flips to ready (or unavailable when the map comes back empty); a failed or timed-out fetch
   * leaves the working rates untouched and flags unavailable. It never surfaces an error.
   */
  fetchMarketRates(base: string): void {
    this.fxStatusSignal.set('loading');
    this.api.fetchMarketRates(base).subscribe({
      next: (rates) => {
        const hasRates = Object.keys(rates).length > 0;
        this.marketRatesSignal.set(rates);
        this.fxStatusSignal.set(hasRates ? 'ready' : 'unavailable');
      },
      error: () => {
        this.marketRatesSignal.set({});
        this.fxStatusSignal.set('unavailable');
      },
    });
  }

  /**
   * Fetch the currency catalog (code → name) client-side once, into the names signal. A failed or
   * timed-out fetch leaves it empty (callers then show bare codes); it never surfaces an error.
   */
  fetchCurrencyCatalog(): void {
    this.api.fetchCurrencyCatalog().subscribe({next: (names) => this.currencyNamesSignal.set(names)});
  }

  /**
   * Apply one rate edit to the working fx map. Reject non-positive or non-finite input, then clamp the
   * top at 1e9 so an extreme value can't overflow the NUMERIC(18,8) fx_rate column (it caps below
   * 10^10, and no real rate approaches this). Update the rate signal at once so live conversions track
   * the working rate, mark the month dirty, and trigger a debounced recompute (conversions and
   * money-out depend on the rate). The rate is NOT persisted here: save() writes it to fx_rate, and
   * /compute carries it meanwhile.
   */
  setFxRate(base: string,
            quote: string,
            rate: number): void {
    if (!isFinite(rate) || rate <= 0) {
      return;
    }

    const safeRate = Math.min(rate, 1_000_000_000);
    this.fxRatesSignal.update((rates) => ({...rates, [quote]: safeRate}));
    this.dirtySignal.set(true);
    this.recompute$.next();
  }

  /** Apply the fetched market rate for a quote into the working rate (deferred to Save like any edit). */
  useMarketRate(base: string,
                quote: string): void {
    const market = this.marketRatesSignal()[quote];
    if (market === undefined) {
      return;
    }

    this.setFxRate(base, quote, market);
  }

  /**
   * Compute the current month's figures on the immediate (non-debounced) path and clear loading once
   * they land or the call fails. Used after load, save, and a wholesale month replacement, where the
   * figures should refresh in one step rather than through the edit window.
   */
  private runCompute(): void {
    this.api.compute(this.monthSignal(), this.monthKey(), this.fxRatesSignal()).subscribe({
      next: (result) => {
        this.computedSignal.set(result);
        this.loadingSignal.set(false);
      },
      error: () => {
        this.computedSignal.set(emptyComputed());
        this.loadingSignal.set(false);
      },
    });
  }
}
