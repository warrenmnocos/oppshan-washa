import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {BudgetMonth, Computed, Me, Salary, SalaryPresetView} from '../models/budget.models';

/** Typed client for /api/me and /api/budget/*. */
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

  compute(month: BudgetMonth): Observable<Computed> {
    return this.http.post<Computed>('/api/budget/compute', month);
  }

  fx(base: string): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>(`/api/budget/fx?base=${base}`);
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
