import {computed, inject, Injectable, signal} from '@angular/core';
import {Subject} from 'rxjs';
import {debounceTime} from 'rxjs/operators';
import {BudgetApiService} from './budget-api.service';
import {BudgetMonth, Computed, Salary, SalaryPresetView} from '../models/budget.models';

const FORWARD_LIMIT = 60; // months of forward planning (HANDOVER §2)

function emptyMonth(): BudgetMonth {
  return {
    salaries: [],
    expenses: [{label: 'Tithe', auto: 'tithe', cur: 'JPY'}],
    goals: [],
    debts: [],
    cur: [{code: 'JPY', sym: '¥'}, {code: 'PHP', sym: '₱'}],
  };
}

function emptyComputed(): Computed {
  return {
    moneyIn: 0, moneyOut: 0, free: 0, tithe: 0, otherExpenses: 0, debt: 0,
    savingsGoals: 0, nonSavingsGoals: 0, savingsRate: 0, salaryNet: {}, debts: [],
    goalProgress: [], savingsBalance: 0, activity: [],
  };
}

function keyForOffset(offset: number): string {
  const now = new Date();
  const base = new Date(now.getFullYear(), now.getMonth() + offset, 1);
  return `${base.getFullYear()}-${String(base.getMonth() + 1).padStart(2, '0')}`;
}

/** Signal store for the working budget month: load, edit (marks dirty + recomputes), save, navigate. */
@Injectable({providedIn: 'root'})
export class BudgetStore {

  private readonly api = inject(BudgetApiService);

  private readonly monthOffsetSignal = signal(0);
  private readonly monthSignal = signal<BudgetMonth>(emptyMonth());
  private readonly computedSignal = signal<Computed>(emptyComputed());
  private readonly dirtySignal = signal(false);
  private readonly loadingSignal = signal(false);
  private readonly savingSignal = signal(false);
  private readonly presetsSignal = signal<SalaryPresetView[]>([]);

  private readonly recompute$ = new Subject<void>();

  readonly month = this.monthSignal.asReadonly();
  readonly computed = this.computedSignal.asReadonly();
  readonly dirty = this.dirtySignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly saving = this.savingSignal.asReadonly();
  readonly presets = this.presetsSignal.asReadonly();
  readonly monthKey = computed(() => keyForOffset(this.monthOffsetSignal()));
  readonly canGoForward = computed(() => this.monthOffsetSignal() < FORWARD_LIMIT);

  constructor() {
    this.recompute$.pipe(debounceTime(250)).subscribe(() => this.runCompute());
  }

  load(): void {
    const key = this.monthKey();
    this.loadingSignal.set(true);
    this.api.getMonth(key).subscribe({
      next: (month) => {
        this.monthSignal.set(month);
        this.dirtySignal.set(false);
        this.loadingSignal.set(false);
        this.runCompute();
      },
      error: () => {
        this.monthSignal.set(emptyMonth());
        this.loadingSignal.set(false);
      },
    });
  }

  navigate(delta: number): void {
    const next = this.monthOffsetSignal() + delta;
    if (next > FORWARD_LIMIT) {
      return;
    }
    this.monthOffsetSignal.set(next);
    this.load();
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

  setMonth(month: BudgetMonth): void {
    this.monthSignal.set(month);
    this.dirtySignal.set(true);
    this.runCompute();
  }

  save(): void {
    this.savingSignal.set(true);
    this.api.saveMonth(this.monthKey(), this.monthSignal()).subscribe({
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

  private runCompute(): void {
    this.api.compute(this.monthSignal(), this.monthKey()).subscribe({
      next: (result) => this.computedSignal.set(result),
      error: () => this.computedSignal.set(emptyComputed()),
    });
  }
}
