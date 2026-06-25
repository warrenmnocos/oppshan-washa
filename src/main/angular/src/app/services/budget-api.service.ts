import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {catchError, map, Observable, of, timeout} from 'rxjs';
import {BudgetMonth, Computed, Me, Salary, SalaryPresetView} from '../models/budget.models';

// Keyless public FX feeds, fetched client-side (the Lambda makes no outbound call). The primary is
// jsdelivr-backed currency-api; the fallback is open.er-api. Both return { <base>: { <quote>: rate } }
// once normalized. A 6s ceiling matches the prototype before falling back.
const CURRENCY_API_URL = (base: string) =>
  `https://latest.currency-api.pages.dev/v1/currencies/${base.toLowerCase()}.json`;
const ER_API_URL = (base: string) => `https://open.er-api.com/v6/latest/${base.toUpperCase()}`;
const FETCH_TIMEOUT_MS = 6000;

/** Typed client for /api/me and /api/budget/*, plus the client-side live FX fetch. */
@Injectable({providedIn: 'root'})
export class BudgetApiService {

  private readonly http = inject(HttpClient);

  me(): Observable<Me> {
    return this.http.get<Me>('/api/me');
  }

  getMonth(yearMonth: string): Observable<BudgetMonth> {
    return this.http.get<BudgetMonth>(`/api/budget/month/${yearMonth}`);
  }

  saveMonth(yearMonth: string, month: BudgetMonth): Observable<BudgetMonth> {
    return this.http.put<BudgetMonth>(`/api/budget/month/${yearMonth}`, month);
  }

  compute(month: BudgetMonth,
          asOf: string): Observable<Computed> {
    return this.http.post<Computed>(`/api/budget/compute?month=${asOf}`, month);
  }

  fx(base: string): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`/api/budget/fx?base=${base}`);
  }

  /** Upsert one stored rate (units of quote per one base); returns the refreshed rate map. */
  setFxRate(base: string,
            quote: string,
            rate: number): Observable<Record<string, number>> {
    return this.http.put<Record<string, number>>('/api/budget/fx', {base, quote, rate});
  }

  /**
   * Live market rates for a base, fetched client-side from the keyless currency-api with a timeout,
   * falling back to open.er-api, then to an empty map on any failure (offline/blocked). Quote codes
   * are upper-cased so they line up with the budget's currency codes.
   */
  fetchMarketRates(base: string): Observable<Record<string, number>> {
    const baseLower = base.toLowerCase();
    return this.http.get<Record<string, Record<string, number>>>(CURRENCY_API_URL(base)).pipe(
      timeout(FETCH_TIMEOUT_MS),
      map((payload) => this.upperCaseRates(payload?.[baseLower])),
      catchError(() => this.fetchMarketRatesFallback(base)),
    );
  }

  private fetchMarketRatesFallback(base: string): Observable<Record<string, number>> {
    return this.http.get<{rates?: Record<string, number>}>(ER_API_URL(base)).pipe(
      timeout(FETCH_TIMEOUT_MS),
      map((payload) => this.upperCaseRates(payload?.rates)),
      catchError(() => of({})),
    );
  }

  private upperCaseRates(rates: Record<string, number> | undefined | null): Record<string, number> {
    const result: Record<string, number> = {};
    for (const [code, rate] of Object.entries(rates ?? {})) {
      if (typeof rate === 'number' && isFinite(rate) && rate > 0) {
        result[code.toUpperCase()] = rate;
      }
    }

    return result;
  }

  listPresets(): Observable<SalaryPresetView[]> {
    return this.http.get<SalaryPresetView[]>('/api/budget/presets');
  }

  createPreset(name: string,
               salary: Salary): Observable<SalaryPresetView> {
    return this.http.post<SalaryPresetView>('/api/budget/presets', {name, salary});
  }

  deletePreset(uuid: string): Observable<void> {
    return this.http.delete<void>(`/api/budget/presets/${uuid}`);
  }
}
