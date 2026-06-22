import {inject} from '@angular/core';
import {CanActivateFn} from '@angular/router';
import {catchError, map, of} from 'rxjs';
import {BudgetApiService} from './budget-api.service';

/**
 * Allows activation when /api/me succeeds. On 401 it kicks off the Google OIDC flow by navigating
 * the browser to the server's sign-in entry point (which 302s to Google).
 */
export const authGuard: CanActivateFn = () => {
  const api = inject(BudgetApiService);
  return api.me().pipe(
    map(() => true),
    catchError(() => {
      window.location.href = '/sso/sign-in';
      return of(false);
    }),
  );
};
