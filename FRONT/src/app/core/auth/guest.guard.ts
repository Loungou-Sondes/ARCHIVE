import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Redirect to home if a token is already stored (optional UX on /login). */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.whenSessionReady();
  if (auth.isLoggedIn()) {
    return router.createUrlTree(['/home']);
  }
  return true;
};
