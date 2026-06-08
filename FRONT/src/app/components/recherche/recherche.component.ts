import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { addQuery, buildSearchQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { BoiteConsultationDialogComponent } from '../../shared/boite-consultation/boite-consultation-dialog.component';
import { BoiteConsultationService } from '../../shared/boite-consultation/boite-consultation.service';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { RippleModule } from 'primeng/ripple';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

export interface BoiteRechercheRow {
  boiteId: number;
  titre: string;
  motsCles: string | null;
  anneeMin: number;
  anneeMax: number;
  documentTypeTitle: string | null;
  bordereauId: number;
  numeroBordereau: string;
  directionLabel: string | null;
  typeEtat: string | null;
  typeEtatLabel: string | null;
  emplacement: string | null;
}

interface BoiteRecherchePage {
  content: BoiteRechercheRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-recherche',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    InputTextModule,
    TableModule,
    PaginatorModule,
    ProgressSpinnerModule,
    TagModule,
    ToastModule,
    TooltipModule,
    BoiteConsultationDialogComponent,
    TranslocoPipe,
  ],
  templateUrl: './recherche.component.html',
  styleUrl: './recherche.component.scss',
  providers: [MessageService],
})
export class RechercheComponent {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly boiteConsult = inject(BoiteConsultationService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly api = `${environment.apiUrl}/api/boites/recherche`;

  @ViewChild('dt') dt?: Table;

  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportBoxes'));

  constructor() {
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportBoxes'));
    });
  }

  readonly rows = signal<BoiteRechercheRow[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);
  readonly hasSearched = signal(false);

  nom = signal('');
  motsCles = signal('');
  anneeMin = signal<number | null>(null);
  anneeMax = signal<number | null>(null);

  pageSize = 20;

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;

    let params = new HttpParams().set('page', String(page)).set('size', String(rows));
    params = params.append('sort', 'titre,asc');

    params = addQuery(params, buildSearchQuery(this.nom(), this.motsCles()));

    const amin = this.anneeMin();
    const amax = this.anneeMax();
    if (amin != null && !Number.isNaN(amin)) {
      params = params.set('anneeMin', String(Math.trunc(amin)));
    }
    if (amax != null && !Number.isNaN(amax)) {
      params = params.set('anneeMax', String(Math.trunc(amax)));
    }

    this.loading.set(true);
    this.http.get<BoiteRecherchePage>(this.api, { params }).subscribe({
      next: (p) => {
        this.rows.set(p.content ?? []);
        this.totalRecords.set(p.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'common.searchError');
      },
    });
  }

  runSearch(): void {
    if (!this.hasAnyCriterion()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('common.criteriaRequired'),
        detail: this.i18n.t('recherche.criteriaWarnDetail'),
        life: 6000,
      });
      return;
    }
    this.hasSearched.set(true);
    queueMicrotask(() => this.dt?.reset());
  }

  refreshTable(): void {
    if (!this.hasSearched()) {
      return;
    }
    queueMicrotask(() => this.dt?.reset());
  }

  onAnneeChange(which: 'min' | 'max', value: number | string | null): void {
    const parsed =
      value === '' || value === null || value === undefined
        ? null
        : typeof value === 'number'
          ? value
          : Number(value);
    const year = parsed != null && Number.isFinite(parsed) ? Math.trunc(parsed) : null;
    if (which === 'min') {
      this.anneeMin.set(year);
    } else {
      this.anneeMax.set(year);
    }
  }

  clearSearch(): void {
    this.nom.set('');
    this.motsCles.set('');
    this.anneeMin.set(null);
    this.anneeMax.set(null);
    this.hasSearched.set(false);
    this.rows.set([]);
    this.totalRecords.set(0);
  }

  hasAnyCriterion(): boolean {
    return (
      this.nom().trim().length > 0
      || this.motsCles().trim().length > 0
      || this.anneeMin() != null
      || this.anneeMax() != null
    );
  }

  countLabel(): string {
    const n = this.totalRecords();
    if (n === 0) {
      return this.i18n.t('recherche.countZero');
    }
    if (n === 1) {
      return this.i18n.t('recherche.countOne');
    }
    return this.i18n.t('recherche.countMany', { count: n });
  }

  anneesLabel(min: number, max: number): string {
    return min === max ? String(min) : `${min} – ${max}`;
  }

  etatSeverity(typeEtat: string | null | undefined): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (typeEtat) {
      case 'SEMI_ACTIF':
        return 'success';
      case 'TRANSFERT':
        return 'info';
      case 'DESTRUCTION':
        return 'warn';
      default:
        return 'secondary';
    }
  }

  openBoiteDetail(row: BoiteRechercheRow): void {
    this.boiteConsult.open(row.boiteId, { manageDossiers: false });
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 10000,
    });
  }
}
