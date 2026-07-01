import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {catchError, map, Observable, of, timeout} from 'rxjs';
import {BudgetMonth, Computed, Me, Salary, SalaryPresetView} from '../models/budget.models';

/**
 * Keyless public FX feeds, fetched client-side (the Lambda makes no outbound call). The primary is the
 * jsdelivr-backed currency-api; the fallback is open.er-api. Both normalize to
 * { <base>: { <quote>: rate } }.
 */
const CURRENCY_API_URL = (base: string) =>
  `https://latest.currency-api.pages.dev/v1/currencies/${base.toLowerCase()}.json`;
const ER_API_URL = (base: string) => `https://open.er-api.com/v6/latest/${base.toUpperCase()}`;
/** The same feed's catalog: a flat { <lowercase code>: <name> } map for labeling currencies, fetched client-side. */
const CURRENCY_CATALOG_URL = 'https://latest.currency-api.pages.dev/v1/currencies.json';
/** Ceiling for each client-side FX fetch before it gives up and falls back (offline/blocked). */
const FETCH_TIMEOUT_MS = 6000;

/** Typed client for /api/me and /api/budget/*, plus the client-side live FX fetch. */
@Injectable({providedIn: 'root'})
export class BudgetApiService {

  private readonly http = inject(HttpClient);

  /** GET the signed-in identity from /api/me. */
  me(): Observable<Me> {
    return this.http.get<Me>('/api/me');
  }

  /** GET the stored budget month for a YYYY-MM key. */
  getMonth(yearMonth: string): Observable<BudgetMonth> {
    return this.http.get<BudgetMonth>(`/api/budget/month/${yearMonth}`);
  }

  /** PUT the month for a YYYY-MM key; resolves with the persisted month. */
  saveMonth(yearMonth: string, month: BudgetMonth): Observable<BudgetMonth> {
    return this.http.put<BudgetMonth>(`/api/budget/month/${yearMonth}`, month);
  }

  /** POST the working month plus its fx rates to /compute; returns the derived figures for the given month. */
  compute(month: BudgetMonth,
          asOf: string,
          fxRates: Record<string, number>): Observable<Computed> {
    return this.http.post<Computed>(`/api/budget/compute?month=${asOf}`, {...month, fxRates});
  }

  /** GET the stored rates for a base (quote code → units per one base). */
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

  /** Fallback market-rate source (open.er-api) with the same timeout; resolves to an empty map on any failure. */
  private fetchMarketRatesFallback(base: string): Observable<Record<string, number>> {
    return this.http.get<{rates?: Record<string, number>}>(ER_API_URL(base)).pipe(
      timeout(FETCH_TIMEOUT_MS),
      map((payload) => this.upperCaseRates(payload?.rates)),
      catchError(() => of({})),
    );
  }

  /**
   * The currency catalog (code → display name), fetched client-side from the same keyless feed with a
   * timeout, falling back to an empty map on any failure (offline/blocked). Codes are upper-cased so
   * they line up with the budget's currency codes; callers show bare codes when it comes back empty.
   */
  fetchCurrencyCatalog(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(CURRENCY_CATALOG_URL).pipe(
      timeout(FETCH_TIMEOUT_MS),
      map((payload) => this.upperCaseNames(payload)),
      catchError(() => of({})),
    );
  }

  /** Normalize a catalog map: upper-case each code and drop empty names. */
  private upperCaseNames(names: Record<string, string> | undefined | null): Record<string, string> {
    const result: Record<string, string> = {};
    for (const [code, name] of Object.entries(names ?? {})) {
      if (typeof name === 'string' && name.length > 0) {
        result[code.toUpperCase()] = name;
      }
    }

    return result;
  }

  /** Normalize a rate map: upper-case each code and keep only positive, finite rates. */
  private upperCaseRates(rates: Record<string, number> | undefined | null): Record<string, number> {
    const result: Record<string, number> = {};
    for (const [code, rate] of Object.entries(rates ?? {})) {
      if (typeof rate === 'number' && isFinite(rate) && rate > 0) {
        result[code.toUpperCase()] = rate;
      }
    }

    return result;
  }

  /** GET the shared salary-preset list. */
  listPresets(): Observable<SalaryPresetView[]> {
    return this.http.get<SalaryPresetView[]>('/api/budget/presets');
  }

  /** POST a new named salary preset; resolves with the stored preset. */
  createPreset(name: string,
               salary: Salary): Observable<SalaryPresetView> {
    return this.http.post<SalaryPresetView>('/api/budget/presets', {name, salary});
  }

  /** DELETE a salary preset by uuid. */
  deletePreset(uuid: string): Observable<void> {
    return this.http.delete<void>(`/api/budget/presets/${uuid}`);
  }
}
