import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { MessageService } from 'primeng/api';
import { environment } from '../../../environments/environment';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import {
  BoiteConsultationDetail,
  CreateDossierRequest,
  Dossier,
} from './boite-consultation.models';

export interface BoiteConsultationOpenOptions {
  /** false = consultation seule (ex. page Recherche) */
  manageDossiers?: boolean;
}

@Injectable({ providedIn: 'root' })
export class BoiteConsultationService {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly api = `${environment.apiUrl}/api/boites`;

  readonly visible = signal(false);
  readonly loading = signal(false);
  readonly detail = signal<BoiteConsultationDetail | null>(null);
  readonly dossiers = signal<Dossier[]>([]);
  readonly dossiersLoading = signal(false);
  readonly dossierSaving = signal(false);
  readonly showAddDossierForm = signal(false);
  readonly manageDossiers = signal(true);

  open(boiteId: number, options?: BoiteConsultationOpenOptions): void {
    this.manageDossiers.set(options?.manageDossiers ?? true);
    this.detail.set(null);
    this.dossiers.set([]);
    this.showAddDossierForm.set(false);
    this.loading.set(true);
    this.visible.set(true);
    this.http.get<BoiteConsultationDetail>(`${this.api}/${boiteId}`).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loading.set(false);
        this.loadDossiers(boiteId);
      },
      error: (err) => {
        this.loading.set(false);
        this.visible.set(false);
        this.toastError(err, 'boiteConsult.loadDetailError');
      },
    });
  }

  loadDossiers(boiteId: number): void {
    this.dossiersLoading.set(true);
    this.http.get<Dossier[]>(`${this.api}/${boiteId}/dossiers`).subscribe({
      next: (list) => {
        this.dossiers.set(list ?? []);
        this.dossiersLoading.set(false);
      },
      error: (err) => {
        this.dossiersLoading.set(false);
        this.toastError(err, 'boiteConsult.loadDossiersError');
      },
    });
  }

  toggleAddDossierForm(): void {
    this.showAddDossierForm.update((v) => !v);
  }

  addDossier(boiteId: number, body: CreateDossierRequest, onSuccess?: () => void): void {
    if (this.dossierSaving()) {
      return;
    }
    this.dossierSaving.set(true);
    this.http.post<Dossier>(`${this.api}/${boiteId}/dossiers`, body).subscribe({
      next: () => {
        this.dossierSaving.set(false);
        this.showAddDossierForm.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('boiteConsult.addSuccessSummary'),
          detail: this.i18n.t('boiteConsult.addSuccessDetail'),
          life: 4000,
        });
        this.loadDossiers(boiteId);
        onSuccess?.();
      },
      error: (err) => {
        this.dossierSaving.set(false);
        this.toastError(err, 'boiteConsult.addError');
      },
    });
  }

  deleteDossier(boiteId: number, dossierId: number): void {
    this.http.delete(`${this.api}/${boiteId}/dossiers/${dossierId}`).subscribe({
      next: () => {
        this.messages.add({
          severity: 'info',
          summary: this.i18n.t('boiteConsult.deleteSuccessSummary'),
          life: 3000,
        });
        this.loadDossiers(boiteId);
      },
      error: (err) => {
        this.toastError(err, 'boiteConsult.deleteError');
      },
    });
  }

  close(): void {
    this.visible.set(false);
    this.detail.set(null);
    this.dossiers.set([]);
    this.loading.set(false);
    this.dossiersLoading.set(false);
    this.dossierSaving.set(false);
    this.showAddDossierForm.set(false);
    this.manageDossiers.set(true);
  }

  onVisibleChange(visible: boolean): void {
    this.visible.set(visible);
    if (!visible) {
      this.close();
    }
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 5000,
    });
  }
}
