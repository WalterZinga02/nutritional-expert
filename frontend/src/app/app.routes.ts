import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage), title: 'Accedi | Nutritional Expert' },
  { path: 'registrazione', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage),
    data: { registering: true }, title: 'Registrati | Nutritional Expert' },
  { path: 'account', loadComponent: () => import('./auth/account-page').then(m => m.AccountPage),
    canActivate: [authGuard], title: 'Il mio account | Nutritional Expert' },
  { path: '**', redirectTo: 'login' }
];