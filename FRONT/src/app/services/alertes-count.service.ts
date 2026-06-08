import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';

export interface AlertesCount {
  total: number;
  bordereauxEnAttente: number;
  bordereauxValidationAgents: number;
  boitesSemiActif: number;
  boitesEcheance: number;
  lignesPleines: number;
  reglesConservation: number;
  passwordResetRequests: number;
}

const EMPTY: AlertesCount = {
  total: 0,
  bordereauxEnAttente: 0,
  bordereauxValidationAgents: 0,
  boitesSemiActif: 0,
  boitesEcheance: 0,
  lignesPleines: 0,
  reglesConservation: 0,
  passwordResetRequests: 0,
};

@Injectable({ providedIn: 'root' })
export class AlertesCountService {
  private readonly http = inject(HttpClient);
  private readonly api = `${environment.apiUrl}/api/alertes/count`;

  readonly counts = signal<AlertesCount>(EMPTY);
  readonly loading = signal(false);

  /** Compteur affiché : reflète les alertes réelles tant qu’elles ne sont pas traitées côté serveur. */
  readonly unread = computed(() => this.counts());

  private inFlight = false;

  refresh(onDone?: () => void): void {
    if (this.inFlight) {
      onDone?.();
      return;
    }
    this.inFlight = true;
    this.loading.set(true);
    this.http.get<AlertesCount>(this.api).subscribe({
      next: (c) => {
        this.counts.set(normalizeCounts(c));
        this.loading.set(false);
      },
      error: () => {
        this.counts.set(EMPTY);
        this.loading.set(false);
      },
      complete: () => {
        this.inFlight = false;
        onDone?.();
      },
    });
  }

  readonly badgeLabel = computed(() => {
    const n = this.counts().total;
    if (n <= 0) return '';
    return n > 99 ? '99+' : String(n);
  });
}

function normalizeCounts(c: Partial<AlertesCount> | null | undefined): AlertesCount {
  if (!c) return EMPTY;
  const bordereauxEnAttente = Math.max(0, Number(c.bordereauxEnAttente) || 0);
  const bordereauxValidationAgents = Math.max(0, Number(c.bordereauxValidationAgents) || 0);
  const boitesSemiActif = Math.max(0, Number(c.boitesSemiActif) || 0);
  const boitesEcheance = Math.max(0, Number(c.boitesEcheance) || 0);
  const lignesPleines = Math.max(0, Number(c.lignesPleines) || 0);
  const reglesConservation = Math.max(0, Number(c.reglesConservation) || 0);
  const passwordResetRequests = Math.max(0, Number(c.passwordResetRequests) || 0);
  return {
    bordereauxEnAttente,
    bordereauxValidationAgents,
    boitesSemiActif,
    boitesEcheance,
    lignesPleines,
    reglesConservation,
    passwordResetRequests,
    total:
      bordereauxEnAttente
      + bordereauxValidationAgents
      + boitesSemiActif
      + boitesEcheance
      + lignesPleines
      + reglesConservation
      + passwordResetRequests,
  };
}
