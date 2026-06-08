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
export interface BoiteArchivesIntermediaireItem {
  boiteId: number;
  boiteTitre: string;
  documentTypeTitle: string;
  dateEtat: string;
  emplacement: string | null;
  anneeMin: number;
  anneeMax: number;
  regleReference: string | null;
  finalDecision: string | null;
}

export interface BordereauArchivesIntermediairesGroup {
  bordereauId: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionLabel: string | null;
  agentUserName: string;
  boitesCount: number;
  totalBoitesBordereau: number;
  boites: BoiteArchivesIntermediaireItem[];
}

interface ArchivesIntermediairesGroupedPage {
  content: BordereauArchivesIntermediairesGroup[];
  totalElements: number;
  totalBoites: number;
  totalPages: number;
  number: number;
  size: number;
}

@Component({
  selector: 'app-archives-intermediaires',
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
  templateUrl: './archives-intermediaires.component.html',
  styleUrl: './archives-intermediaires.component.scss',
  providers: [MessageService],
})
export class ArchivesIntermediairesComponent {
  private readonly http = inject(HttpClient);
  private readonly transloco = inject(TranslocoService);
  private readonly i18n = inject(AppTranslateService);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly boiteConsult = inject(BoiteConsultationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchDebounced = new Subject<void>();
  private readonly api = `${environment.apiUrl}/api/boites/archives-intermediaires`;

  readonly groups = signal<BordereauArchivesIntermediairesGroup[]>([]);
  readonly totalBordereaux = signal(0);
  readonly totalBoites = signal(0);
  readonly loading = signal(false);

  searchText = signal('');

  sortField = signal<'numero' | 'dateTransfert'>('numero');
  sortOrder: 1 | -1 = -1;

  page = 0;
  pageSize = 5;

  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportSlips'));

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
    params = this.appendSortParams(params);

    params = addQuery(params, this.searchText());

    this.loading.set(true);
    this.http.get<ArchivesIntermediairesGroupedPage>(this.api, { params }).subscribe({
      next: (p) => {
        this.groups.set(p.content ?? []);
        this.totalBordereaux.set(p.totalElements ?? 0);
        this.totalBoites.set(p.totalBoites ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'common.loadArchivesError');
      },
    });
  }

  onPageChange(event: PaginatorState): void {
    const rows = event.rows ?? this.pageSize;
    const page = event.page ?? 0;
    if (rows !== this.pageSize) {
      this.pageSize = rows;
    }
    this.load(page);
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  cycleSort(): void {
    if (this.sortField() === 'numero') {
      if (this.sortOrder === -1) {
        this.sortOrder = 1;
      } else {
        this.sortField.set('dateTransfert');
        this.sortOrder = -1;
      }
    } else if (this.sortOrder === -1) {
      this.sortOrder = 1;
    } else {
      this.sortField.set('numero');
      this.sortOrder = -1;
    }
    this.load(0);
  }

  sortTooltip(): string {
    if (this.sortField() === 'numero') {
      return this.sortOrder === -1
        ? this.transloco.translate('archives.sortNumeroAsc')
        : this.transloco.translate('archives.sortDateRecent');
    }
    return this.sortOrder === -1
      ? this.transloco.translate('archives.sortDateOld')
      : this.transloco.translate('archives.sortNumeroRecent');
  }

  clearSearch(): void {
    this.searchText.set('');
    this.sortField.set('numero');
    this.sortOrder = -1;
    this.load(0);
  }

  private appendSortParams(params: HttpParams): HttpParams {
    if (this.sortField() === 'numero') {
      const dir = this.sortOrder === 1 ? 'asc' : 'desc';
      return params.append('sort', `anneeNumero,${dir}`).append('sort', `rangNumero,${dir}`);
    }
    const dir = this.sortOrder === 1 ? 'asc' : 'desc';
    return params.append('sort', `dateTransfert,${dir}`);
  }

  countLabel(): string {
    const br = this.totalBordereaux();
    const bx = this.totalBoites();
    const brLabel = br === 1 ? this.i18n.t('common.slipOne') : this.i18n.t('common.slipMany', { count: br });
    const bxLabel = bx === 1 ? this.i18n.t('common.boxOne') : this.i18n.t('common.boxMany', { count: bx });
    return this.i18n.t('archives.countSummary', { slips: brLabel, boxes: bxLabel });
  }

  boitesLabel(count: number): string {
    return count === 1
      ? this.i18n.t('archives.boxesOnSlipOne')
      : this.i18n.t('archives.boxesOnSlipMany', { count });
  }

  boiteIndexLabel(index: number, total: number): string {
    return this.i18n.t('archives.boxIndexAria', { index: index + 1, total });
  }

  /** Affiché quand des boîtes du bordereau ont déjà été transférées ou détruites. */
  hasBoitesPartielles(group: BordereauArchivesIntermediairesGroup): boolean {
    return group.totalBoitesBordereau > 1 && group.boitesCount < group.totalBoitesBordereau;
  }

  totalBoitesLabel(total: number): string {
    return total === 1
      ? this.i18n.t('archives.totalBoxesOne')
      : this.i18n.t('archives.totalBoxesMany', { count: total });
  }

  anneesLabel(min: number, max: number): string {
    if (min === max) {
      return String(min);
    }
    return `${min} – ${max}`;
  }

  finalDecisionLabel(code: string | null | undefined): string {
    if (!code) {
      return this.i18n.t('common.emDash');
    }
    switch (code) {
      case 'TRANSFERER':
        return this.i18n.t('common.decisionTransferFuture');
      case 'DETRUIRE':
        return this.i18n.t('common.decisionDestroyFuture');
      case 'CONSERVER':
        return this.i18n.t('common.decisionKeepFuture');
      default:
        return code;
    }
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

  /** Ouvre le détail boîte + section dossiers (ajout / liste). */
  openBoiteDossiers(boiteId: number): void {
    this.boiteConsult.open(boiteId);
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
