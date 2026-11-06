import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { adminGuard } from './admin/admin.guard';
export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage), title: 'Accedi | Nutritional Expert' },
  { path: 'registrazione', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage),
    data: { registering: true }, title: 'Registrati | Nutritional Expert' },
  { path: 'account', loadComponent: () => import('./auth/account-page').then(m => m.AccountPage),
    canActivate: [authGuard], title: 'Il mio account | Nutritional Expert' },
  { path: 'admin/disponibilita', loadComponent: () => import('./admin/availability-page').then(m => m.AvailabilityPage),
    canActivate: [adminGuard], title: 'Disponibilità studio | Nutritional Expert' },
  { path: '**', redirectTo: 'login' }
];
