import { HttpInterceptorFn } from '@angular/common/http';

/** Envoie le cookie de session httpOnly à chaque appel API (même origine CORS autorisée). */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ withCredentials: true }));
