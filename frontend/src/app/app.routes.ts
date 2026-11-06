/* Mappa URL e componenti caricati su richiesta; authGuard e adminGuard regolano la navigazione, mentre il backend protegge le API. */
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
  { path: 'appuntamenti', loadComponent: () => import('./appointments/appointments-page').then(m => m.AppointmentsPage),
    canActivate: [authGuard], title: 'Appuntamenti | Nutritional Expert' },
  { path: 'admin/appuntamenti', loadComponent: () => import('./admin/admin-appointments-page').then(m => m.AdminAppointmentsPage),
    canActivate: [adminGuard], title: 'Prenotazioni studio | Nutritional Expert' },
  { path: '**', redirectTo: 'login' }
];
