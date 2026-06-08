import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

const LOGIN_PATH = '/api/auth/login';
const LOGOUT_PATH = '/api/auth/logout';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthBypass =
    (req.method === 'POST' && req.url.includes(LOGIN_PATH)) ||
    (req.method === 'POST' && req.url.includes(LOGOUT_PATH));

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isAuthBypass && auth.isLoggedIn()) {
        auth.expireSession('expired');
      }
      return throwError(() => err);
    }),
  );
};
