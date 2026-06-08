import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Tableau de bord admin ; les agents sont redirigés vers leur propre dashboard. */
export const dashboardGuard: CanActivateFn = async () => {
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
