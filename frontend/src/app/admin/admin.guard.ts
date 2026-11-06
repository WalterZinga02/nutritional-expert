/* Consente la navigazione nelle pagine studio solo a un ADMIN autenticato; le stesse autorizzazioni sono verificate dal backend. */
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.user() || !auth.accessToken()) return router.createUrlTree(['/login']);
  return auth.user()?.role === 'ADMIN' ? true : router.createUrlTree(['/account']);
};
