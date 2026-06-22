import {Routes} from '@angular/router';
import {authGuard} from './services/auth.guard';

export const APP_ROUTES: Routes = [
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
