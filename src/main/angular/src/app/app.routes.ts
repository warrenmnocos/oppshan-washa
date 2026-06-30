import {Routes} from '@angular/router';
import {authGuard, guestGuard} from './services/auth.guard';
import {AppShell} from './components/app-shell/app-shell';

export const APP_ROUTES: Routes = [
  {
    path: 'sso/sign-in',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/sign-in/sign-in').then((m) => m.SignIn),
  },
  {
    // The signed-in portal frame (fixed header + footer, scrolling content); apps render inside it.
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
