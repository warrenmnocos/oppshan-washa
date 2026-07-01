import {Routes} from '@angular/router';
import {authGuard, guestGuard} from './services/auth.guard';
import {AppShell} from './components/app-shell/app-shell';

/**
 * Application route table, three concerns:
 * - /sso/sign-in — the standalone sign-in page, behind guestGuard so an already-signed-in user is bounced away.
 * - '' — the AppShell frame (fixed header/footer, scrolling content), behind authGuard, wrapping the
 *   lazy in-app pages as children: the dashboard at '' and the budget page at 'budget'.
 * - ** — anything unmatched redirects home.
 * Pages use loadComponent so each ships as its own lazy chunk; the shell and the guards load eagerly.
 */
export const APP_ROUTES: Routes = [
  {
    path: 'sso/sign-in',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/sign-in/sign-in').then((m) => m.SignIn),
  },
  {
    path: '',
    component: AppShell,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'budget',
        loadComponent: () => import('./pages/budget/budget-page').then((m) => m.BudgetPage),
      },
    ],
  },
  {path: '**', redirectTo: ''},
];
