import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const protectedApi = request.url.startsWith('/api/') &&
    !['/api/auth/login', '/api/auth/register'].includes(request.url) &&
    !request.url.startsWith('/api/public/');
  const token = protectedApi ? auth.accessToken() : null;
  const outgoing = token ? request.clone({ setHeaders: { Authorization: 'Bearer ' + token } }) : request;
  return next(outgoing).pipe(catchError((error: HttpErrorResponse) => {
    if (protectedApi && error.status === 401) auth.logout(true);
    return throwError(() => error);
  }));
};
