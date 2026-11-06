/* Protegge le pagine personali nella navigazione Angular; senza sessione valida rimanda al login. La sicurezza effettiva resta sul backend. */
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.user() && auth.accessToken() ? true : inject(Router).createUrlTree(['/login']);
};
