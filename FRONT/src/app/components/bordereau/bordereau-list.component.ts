import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AfterViewInit, Component, DestroyRef, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { BordereauPendingBadgeComponent } from './bordereau-pending-badge.component';

/** Aligné sur {@code BordereauListItemResponse} côté API. */
export interface BordereauRow {
  id: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionId: string | null;
  directionLabel: string | null;
  agentUserName: string;
  boitesCount: number;
  observation: string | null;
}

interface BordereauPage {
  content: BordereauRow[];
  totalElements: number;
}

@Component({
  selector: 'app-bordereau-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    InputTextModule,
    TableModule,
    ToastModule,
    TooltipModule,
    BordereauPendingBadgeComponent,
    TranslocoPipe,
  ],
  templateUrl: './bordereau-list.component.html',
  styleUrl: './bordereau-list.component.scss',
  providers: [MessageService],
})
export class BordereauListComponent implements OnInit, AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchDebounced = new Subject<void>();
  private readonly filterDebounced = new Subject<void>();
  protected readonly auth = inject(AuthService);

  private readonly api = `${environment.apiUrl}/api/bordereaux`;

  @ViewChild('dt') dt?: Table;

  readonly rows = signal<BordereauRow[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);

  readonly pendingRows = signal<BordereauRow[]>([]);
  readonly pendingTotal = signal(0);
  readonly pendingLoading = signal(false);
  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportEntries'));

  searchText = signal('');
  showFilters = signal(false);
  filterAnneeMin = signal('');
  filterAnneeMax = signal('');

  readonly hasActiveFilters = computed(
    () => !!this.parseYear(this.filterAnneeMin()) || !!this.parseYear(this.filterAnneeMax())
  );

  sortField = signal<'numero' | 'dateTransfert'>('dateTransfert');
  sortOrder: 1 | -1 = -1;
  pageSize = 10;

  constructor() {
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reloadTable());
    this.filterDebounced.pipe(debounceTime(280), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reloadTable());
  }

  ngOnInit(): void {
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportEntries'));
    });
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => {
      this.reloadTable();
      if (this.auth.isAgent()) {
        this.reloadPendingTable();
      }
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    this.fetchPage(event.first ?? 0, event.rows ?? this.pageSize);
  }

  /** Recharge explicitement (évite que {@code dt.reset()} ne relance pas l’API). */
  reloadTable(): void {
    const rows = this.dt?.rows ?? this.pageSize;
    if (this.dt) {
      this.dt.first = 0;
    }
    this.fetchPage(0, rows);
  }

  refreshTable(): void {
    this.reloadTable();
    if (this.auth.isAgent()) {
      this.reloadPendingTable();
    }
  }

  reloadPendingTable(): void {
    if (!this.auth.isAgent()) {
      return;
    }
    let params = new HttpParams().set('page', '0').set('size', '20');
    params = this.appendSortParams(params);
    params = this.appendSearchAndFilterParams(params);
    this.pendingLoading.set(true);
    this.http.get<BordereauPage>(`${this.api}/en-attente`, { params }).subscribe({
      next: (p) => {
        this.pendingRows.set(p.content ?? []);
        this.pendingTotal.set(p.totalElements ?? 0);
        this.pendingLoading.set(false);
      },
      error: (err) => {
        this.pendingLoading.set(false);
        this.toastError(err, 'bordereau.loadPendingError');
      },
    });
  }

  private fetchPage(first: number, rows: number): void {
    const page = Math.floor(first / rows);
    this.pageSize = rows;

    let params = new HttpParams().set('page', String(page)).set('size', String(rows));
    params = this.appendSortParams(params);
    params = this.appendSearchAndFilterParams(params);

    this.loading.set(true);
    this.http.get<BordereauPage>(this.api, { params }).subscribe({
      next: (p) => {
        this.rows.set(p.content ?? []);
        this.totalRecords.set(p.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'bordereau.loadError');
      },
    });
  }

  toggleFilters(): void {
    this.showFilters.update((v) => !v);
  }

  cycleSort(): void {
    if (this.sortField() === 'dateTransfert') {
      this.sortField.set('numero');
      this.sortOrder = 1;
    } else if (this.sortOrder === 1) {
      this.sortOrder = -1;
    } else {
      this.sortField.set('dateTransfert');
      this.sortOrder = -1;
    }
    this.refreshTable();
  }

  clearSearch(): void {
    this.searchText.set('');
    this.filterAnneeMin.set('');
    this.filterAnneeMax.set('');
    this.refreshTable();
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
    if (this.auth.isAgent()) {
      this.reloadPendingTable();
    }
  }

  onFilterAnneeChange(): void {
    this.filterDebounced.next();
    if (this.auth.isAgent()) {
      this.reloadPendingTable();
    }
  }

  countLabel(): string {
    const n = this.totalRecords();
    if (n === 0) {
      return this.i18n.t('bordereau.countZero');
    }
    if (n === 1) {
      return this.i18n.t('common.slipOne');
    }
    return this.i18n.t('common.slipMany', { count: n });
  }

  sortTooltip(): string {
    if (this.sortField() === 'dateTransfert') {
      return this.sortOrder === -1
        ? this.i18n.t('bordereau.sortByDateRecent')
        : this.i18n.t('bordereau.sortByDateOldest');
    }
    return this.sortOrder === 1
      ? this.i18n.t('bordereau.sortByNumeroAsc')
      : this.i18n.t('bordereau.sortByNumeroDesc');
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  private appendSortParams(params: HttpParams): HttpParams {
    if (this.sortField() === 'numero') {
      const dir = this.sortOrder === 1 ? 'asc' : 'desc';
      return params.append('sort', `anneeNumero,${dir}`).append('sort', `rangNumero,${dir}`);
    }
    const dir = this.sortOrder === 1 ? 'asc' : 'desc';
    return params.append('sort', `dateTransfert,${dir}`);
  }

  private appendSearchAndFilterParams(params: HttpParams): HttpParams {
    params = addQuery(params, this.searchText());
    const anneeMin = this.parseYear(this.filterAnneeMin());
    if (anneeMin != null) {
      params = params.set('anneeMin', String(anneeMin));
    }
    const anneeMax = this.parseYear(this.filterAnneeMax());
    if (anneeMax != null) {
      params = params.set('anneeMax', String(anneeMax));
    }
    return params;
  }

  /** Année sur 4 chiffres (ex. 2026), utilisée pour le filtre sur {@code anneeNumero}. */
  private parseYear(value: string): number | null {
    const trimmed = value.trim();
    if (!/^\d{4}$/.test(trimmed)) {
      return null;
    }
    const year = Number(trimmed);
    if (year < 1900 || year > 2100) {
      return null;
    }
    return year;
  }

  openNouveau(): void {
    void this.router.navigate(['/home', 'bordereau-transfert', 'nouveau']);
  }

  openDetail(row: BordereauRow): void {
    void this.router.navigate(['/home', 'bordereau-transfert', String(row.id)]);
  }

  openPendingEdit(row: BordereauRow): void {
    void this.router.navigate(['/home', 'bordereau-transfert', 'reprendre', String(row.id)]);
  }

  pendingCountLabel(): string {
    const n = this.pendingTotal();
    if (n === 0) {
      return this.i18n.t('bordereau.pendingShortZero');
    }
    if (n === 1) {
      return this.i18n.t('bordereau.pendingShortOne');
    }
    return this.i18n.t('bordereau.pendingShortMany', { count: n });
  }

  formatDate(iso: string): string {
    if (!iso) {
      return this.emDash();
    }
    const d = new Date(iso + 'T12:00:00');
    if (Number.isNaN(d.getTime())) {
      return iso;
    }
    return d.toLocaleDateString(this.i18n.localeId());
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 12000,
    });
  }
}
