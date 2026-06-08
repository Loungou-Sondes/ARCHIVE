import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { BoiteConsultationDialogComponent } from '../../shared/boite-consultation/boite-consultation-dialog.component';
import { BoiteConsultationService } from '../../shared/boite-consultation/boite-consultation.service';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

export type HistoriqueTypeFilter = '' | 'TRANSFERT' | 'DESTRUCTION';

export interface BoiteHistoriqueItem {
  boiteId: number;
  boiteTitre: string;
  documentTypeTitle: string;
  typeEtat: string;
  dateEtat: string;
  utilisateur: string | null;
  dernierEmplacement: string | null;
  regleReference: string | null;
  finalDecision: string | null;
}

export interface BordereauHistoriqueGroup {
  bordereauId: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionLabel: string | null;
  agentUserName: string;
  boitesCount: number;
  boites: BoiteHistoriqueItem[];
}

interface HistoriqueGroupedPage {
  content: BordereauHistoriqueGroup[];
  totalElements: number;
  totalBoites: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-historique',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    InputTextModule,
    PaginatorModule,
    ProgressSpinnerModule,
    TagModule,
    ToastModule,
    TooltipModule,
    BoiteConsultationDialogComponent,
    TranslocoPipe,
  ],
  templateUrl: './historique.component.html',
  styleUrl: './historique.component.scss',
  providers: [MessageService],
})
export class HistoriqueComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly boiteConsult = inject(BoiteConsultationService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly searchDebounced = new Subject<void>();
  private readonly api = `${environment.apiUrl}/api/boites/historique`;

  readonly groups = signal<BordereauHistoriqueGroup[]>([]);
  readonly totalBordereaux = signal(0);
  readonly totalBoites = signal(0);
  readonly loading = signal(false);

  searchText = signal('');
  typeFilter = signal<HistoriqueTypeFilter>('');

  page = 0;
  pageSize = 5;

  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportSlips'));

  readonly typeFilterOptions: { value: HistoriqueTypeFilter; labelKey: string }[] = [
    { value: '', labelKey: 'historique.filterAll' },
    { value: 'TRANSFERT', labelKey: 'historique.filterTransferred' },
    { value: 'DESTRUCTION', labelKey: 'historique.filterDestroyed' },
  ];

  constructor() {
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportSlips'));
    });
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load(0));
    this.load(0);
  }

  load(page = this.page): void {
    this.page = page;

    let params = new HttpParams().set('page', String(page)).set('size', String(this.pageSize));

    params = addQuery(params, this.searchText());
    const type = this.typeFilter();
    if (type) {
      params = params.set('typeEtat', type);
    }

    this.loading.set(true);
    this.http.get<HistoriqueGroupedPage>(this.api, { params }).subscribe({
      next: (p) => {
        this.groups.set(p.content ?? []);
        this.totalBordereaux.set(p.totalElements ?? 0);
        this.totalBoites.set(p.totalBoites ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'common.loadHistoryError');
      },
    });
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? this.pageSize;
    const first = event.first ?? 0;
    const page = rows > 0 ? Math.floor(first / rows) : 0;
    if (rows !== this.pageSize) {
      this.pageSize = rows;
    }
    this.load(page);
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  setTypeFilter(value: HistoriqueTypeFilter): void {
    if (this.typeFilter() === value) {
      return;
    }
    this.typeFilter.set(value);
    this.load(0);
  }

  clearSearch(): void {
    this.searchText.set('');
    this.typeFilter.set('');
    this.load(0);
  }

  countLabel(): string {
    const br = this.totalBordereaux();
    const bx = this.totalBoites();
    const brLabel = br === 1 ? this.i18n.t('common.slipOne') : this.i18n.t('common.slipMany', { count: br });
    const bxLabel = bx === 1 ? this.i18n.t('common.boxOne') : this.i18n.t('common.boxMany', { count: bx });
    return this.i18n.t('historique.countSummary', { slips: brLabel, boxes: bxLabel });
  }

  boitesLabel(count: number): string {
    return count === 1
      ? this.i18n.t('historique.boxesOnSlipOne')
      : this.i18n.t('historique.boxesOnSlipMany', { count });
  }

  isTransfert(typeEtat: string): boolean {
    return typeEtat === 'TRANSFERT';
  }

  typeEtatLabel(typeEtat: string): string {
    if (typeEtat === 'DESTRUCTION') {
      return this.i18n.t('common.stateDestroyed');
    }
    if (typeEtat === 'TRANSFERT') {
      return this.i18n.t('common.stateTransferred');
    }
    return typeEtat;
  }

  formatDate(iso: string | null | undefined): string {
    if (!iso) {
      return this.i18n.t('common.emDash');
    }
    const [y, m, d] = iso.split('T')[0].split('-');
    if (!y || !m || !d) {
      return iso;
    }
    return `${d}/${m}/${y}`;
  }

  openBordereau(bordereauId: number): void {
    void this.router.navigate(['/home', 'bordereau-transfert', bordereauId]);
  }

  /** Consultation seule (boîtes transférées / détruites — pas d’ajout de dossiers). */
  openBoiteConsult(boiteId: number): void {
    this.boiteConsult.open(boiteId, { manageDossiers: false });
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
