import {inject} from '@angular/core';
import {CanActivateFn, Router} from '@angular/router';
import {catchError, map, of} from 'rxjs';
import {BudgetApiService} from './budget-api.service';

/**
 * Allows activation when /api/me succeeds (signed in); otherwise routes to the public sign-in page.
 * /api/me returns 401 when signed out (and 403 when the Google identity is not on the household
 * allowlist), so the SPA shows /sso/sign-in rather than force-redirecting the browser to Google.
 */
export const authGuard: CanActivateFn = () => {
  const api = inject(BudgetApiService);
  const router = inject(Router);
  return api.me().pipe(
    map(() => true),
    catchError(() => of(router.createUrlTree(['/sso/sign-in']))),
  );
};

/**
 * Inverse of {@link authGuard}: activation succeeds only while signed out. A visitor who already has a
 * session is redirected to the app root ('/') instead, keeping guest-only views closed to signed-in users.
 */
export const guestGuard: CanActivateFn = () => {
  const api = inject(BudgetApiService);
  const router = inject(Router);
  return api.me().pipe(
    map(() => router.createUrlTree(['/'])),
    catchError(() => of(true)),
  );
};
