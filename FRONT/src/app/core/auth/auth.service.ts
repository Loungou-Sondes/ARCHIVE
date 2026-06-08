import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { IDLE_TIMEOUT_MS, SESSION_MAX_MS } from './auth-session.constants';

export type SessionEndReason = 'expired' | 'idle';

export interface LoginResponse {
  expiresIn: number;
  username: string;
  roles: string[];
}

export interface UserInfo {
  username: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly userSignal = signal<UserInfo | null>(null);
  private readonly sessionReadySignal = signal(false);

  private sessionInitPromise: Promise<void> = Promise.resolve();
  private sessionExpiryTimerId: ReturnType<typeof setTimeout> | null = null;
  private idleTimerId: ReturnType<typeof setTimeout> | null = null;
  private activityAbort?: AbortController;

  readonly user = this.userSignal.asReadonly();
  readonly sessionReady = this.sessionReadySignal.asReadonly();

  readonly displayName = computed(() => this.userSignal()?.username ?? 'Utilisateur');

  readonly displayRole = computed(() => {
    const roles = this.userSignal()?.roles ?? [];
    if (roles.some((r) => r === 'ROLE_ADMIN')) {
      return 'Administrateur';
    }
    return 'Agent';
  });

  readonly displayInitials = computed(() => {
    const name = this.userSignal()?.username ?? 'U';
    const trimmed = name.trim();
    if (trimmed.length >= 2) {
      return trimmed.substring(0, 2).toUpperCase();
    }
    return trimmed.toUpperCase().padEnd(2, '·').substring(0, 2);
  });

  readonly isAdmin = computed(() =>
    (this.userSignal()?.roles ?? []).some((r) => r === 'ROLE_ADMIN'),
  );

  readonly profileLoaded = computed(() => this.userSignal() !== null);

  readonly isAgent = computed(
    () => this.profileLoaded() && !this.isAdmin(),
  );

  isLoggedIn(): boolean {
    return this.userSignal() !== null;
  }

  whenSessionReady(): Promise<void> {
    return this.sessionInitPromise;
  }

  /**
   * Vérifie la session serveur (cookie httpOnly) — aucun jeton en localStorage.
   */
  hydrateProfile(): Promise<void> {
    localStorage.removeItem('access_token');
    sessionStorage.removeItem('access_token');

    this.sessionInitPromise = new Promise((resolve) => {
      this.http
        .get<UserInfo>(`${environment.apiUrl}/api/auth/me`)
        .pipe(
          tap((u) => {
            this.userSignal.set(u);
            this.beginSession(SESSION_MAX_MS);
          }),
          catchError(() => {
            this.clearSession();
            return of(null);
          }),
        )
        .subscribe({
          complete: () => {
            this.markSessionReady();
            resolve();
          },
        });
    });
    return this.sessionInitPromise;
  }

  login(username: string, password: string): Observable<void> {
    return this.http.post<LoginResponse>(`${environment.apiUrl}/api/auth/login`, { username, password }).pipe(
      tap((res) => {
        this.userSignal.set({ username: res.username, roles: res.roles ?? [] });
        this.beginSession((res.expiresIn ?? 3600) * 1000);
      }),
      map(() => void 0),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/api/auth/logout`, null).pipe(
      catchError(() => of(undefined)),
      tap(() => this.clearSession()),
      map(() => void 0),
    );
  }

  expireSession(reason: SessionEndReason = 'expired'): void {
    const hadSession = this.isLoggedIn();
    this.clearSession();
    if (hadSession) {
      void this.http.post<void>(`${environment.apiUrl}/api/auth/logout`, null).subscribe({
        error: () => undefined,
      });
      void this.router.navigate(['/login'], { queryParams: { reason } });
    }
  }

  private beginSession(maxDurationMs: number): void {
    this.scheduleSessionExpiry(maxDurationMs);
    this.startIdleWatch();
  }

  private scheduleSessionExpiry(maxDurationMs: number): void {
    this.clearSessionExpiryTimer();
    const duration = Math.min(maxDurationMs, SESSION_MAX_MS);
    this.sessionExpiryTimerId = setTimeout(() => this.expireSession('expired'), duration);
  }

  private startIdleWatch(): void {
    this.stopIdleWatch();
    this.activityAbort = new AbortController();
    const { signal } = this.activityAbort;
    const bump = () => this.resetIdleTimer();

    for (const eventName of ['mousedown', 'keydown', 'touchstart', 'scroll', 'click'] as const) {
      document.addEventListener(eventName, bump, { passive: true, signal });
    }

    bump();
  }

  private resetIdleTimer(): void {
    this.clearIdleTimer();
    this.idleTimerId = setTimeout(() => this.expireSession('idle'), IDLE_TIMEOUT_MS);
  }

  private clearSessionExpiryTimer(): void {
    if (this.sessionExpiryTimerId !== null) {
      clearTimeout(this.sessionExpiryTimerId);
      this.sessionExpiryTimerId = null;
    }
  }

  private clearIdleTimer(): void {
    if (this.idleTimerId !== null) {
      clearTimeout(this.idleTimerId);
      this.idleTimerId = null;
    }
  }

  private stopIdleWatch(): void {
    this.activityAbort?.abort();
    this.activityAbort = undefined;
    this.clearIdleTimer();
  }

  private markSessionReady(): void {
    this.sessionReadySignal.set(true);
  }

  private clearSession(): void {
    this.clearSessionExpiryTimer();
    this.stopIdleWatch();
    this.userSignal.set(null);
  }
}
