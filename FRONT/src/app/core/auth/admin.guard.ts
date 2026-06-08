import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Réservé aux comptes ayant le rôle {@code ROLE_ADMIN}. */
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.whenSessionReady();
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  if (!auth.isAdmin()) {
    return router.createUrlTree(['/home', 'agent-dashboard']);
  }
  return true;
};
