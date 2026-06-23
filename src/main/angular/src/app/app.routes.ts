import {Routes} from '@angular/router';
import {authGuard, guestGuard} from './services/auth.guard';

export const APP_ROUTES: Routes = [
  {
    path: 'sso/sign-in',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/sign-in/sign-in').then((m) => m.SignIn),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'budget',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/budget/budget-page').then((m) => m.BudgetPage),
  },
  {path: '**', redirectTo: ''},
];
