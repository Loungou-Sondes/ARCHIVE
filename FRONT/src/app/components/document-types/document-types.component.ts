import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AfterViewInit, Component, DestroyRef, OnInit, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

/** Page Spring Data alignée sur {@code Page<DocumentTypeResponse>}. */
export interface DocumentTypePage {
  content: DocumentTypeRow[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface DocumentTypeRow {
  id: number;
  title: string;
  directionId: string | null;
  directionLabel: string | null;
}

export interface DirectionOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-document-types',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    InputTextModule,
    TableModule,
    DialogModule,
    SelectModule,
    ToastModule,
    TooltipModule,
    TranslocoPipe,
  ],
  templateUrl: './document-types.component.html',
  styleUrl: './document-types.component.scss',
  providers: [MessageService],
})
export class DocumentTypesComponent implements OnInit, AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchDebounced = new Subject<void>();

  private readonly api = `${environment.apiUrl}/api/document-types`;

  @ViewChild('dt') dt?: Table;

  readonly rows = signal<DocumentTypeRow[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);
  readonly directionOptions = signal<DirectionOption[]>([]);
  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportEntries'));

  searchText = signal('');
  showFilters = signal(false);
  filterDirectionId = signal<string | null>(null);

  /** Tri côté API : titre asc / desc. */
  sortOrder: 1 | -1 = 1;

  pageSize = 10;

  dialogVisible = signal(false);
  dialogMode = signal<'create' | 'edit'>('create');
  editingId: number | null = null;
  formTitle = '';
  formDirectionId: string | null = null;

  ngOnInit(): void {
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportEntries'));
    });
    this.loadDirectionOptions();
    this.searchDebounced
      .pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshTable());
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.refreshTable());
  }

  loadDirectionOptions(): void {
    this.http.get<DirectionOption[]>(`${this.api}/direction-options`).subscribe({
      next: (opts) => this.directionOptions.set(opts ?? []),
      error: (err) => this.toastError(err, 'docTypes.loadDirectionsError'),
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;

    const sort = 'title';
    const dir = this.sortOrder === 1 ? 'asc' : 'desc';

    let params = new HttpParams().set('page', String(page)).set('size', String(rows));
    params = params.append('sort', `${sort},${dir}`);
    params = addQuery(params, this.searchText());
    const d = this.filterDirectionId();
    if (d) {
      params = params.set('directionId', d);
    }

    this.loading.set(true);
    this.http.get<DocumentTypePage>(this.api, { params }).subscribe({
      next: (p) => {
        this.rows.set(p.content ?? []);
        this.totalRecords.set(p.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'docTypes.loadError');
      },
    });
  }

  /** Remet le paginateur sur la première page et relance le chargement paresseux. */
  refreshTable(): void {
    queueMicrotask(() => this.dt?.reset());
  }

  toggleFilters(): void {
    this.showFilters.update((v) => !v);
  }

  cycleSort(): void {
    this.sortOrder = this.sortOrder === 1 ? -1 : 1;
    this.refreshTable();
  }

  clearSearch(): void {
    this.searchText.set('');
    this.filterDirectionId.set(null);
    this.sortOrder = 1;
    this.refreshTable();
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  onFilterDirectionChange(): void {
    this.refreshTable();
  }

  countLabel(): string {
    const n = this.totalRecords();
    return n <= 1
      ? this.i18n.t('docTypes.countOne', { count: n })
      : this.i18n.t('docTypes.countMany', { count: n });
  }

  sortTooltip(): string {
    return this.sortOrder === 1 ? this.i18n.t('docTypes.sortDesc') : this.i18n.t('docTypes.sortAsc');
  }

  openCreate(): void {
    this.dialogMode.set('create');
    this.editingId = null;
    this.formTitle = '';
    this.formDirectionId = null;
    this.dialogVisible.set(true);
  }

  openEdit(row: DocumentTypeRow): void {
    this.dialogMode.set('edit');
    this.editingId = row.id;
    this.formTitle = row.title;
    this.formDirectionId = row.directionId;
    this.dialogVisible.set(true);
  }

  dialogTitle(): string {
    return this.dialogMode() === 'edit'
      ? this.i18n.t('docTypes.dialogEditTitle')
      : this.i18n.t('docTypes.dialogCreateTitle');
  }

  dialogSubtitle(): string {
    return this.dialogMode() === 'edit'
      ? this.i18n.t('docTypes.dialogEditSubtitle')
      : this.i18n.t('docTypes.dialogCreateSubtitle');
  }

  save(): void {
    const title = this.formTitle?.trim();
    if (!title) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('docTypes.toastTitle'),
        detail: this.i18n.t('docTypes.titleRequired'),
      });
      return;
    }
    const dirTrim = (this.formDirectionId ?? '').trim();
    const body: { title: string; directionId: string | null } = {
      title,
      directionId: dirTrim.length > 0 ? dirTrim : null,
    };
    if (this.dialogMode() === 'create') {
      this.http.post<DocumentTypeRow>(this.api, body).subscribe({
        next: () => {
          this.messages.add({
            severity: 'success',
            summary: this.i18n.t('docTypes.toastCreated'),
            detail: this.i18n.t('docTypes.createSuccess'),
          });
          this.dialogVisible.set(false);
          this.refreshTable();
        },
        error: (err) => this.toastError(err, 'docTypes.createError'),
      });
    } else if (this.editingId != null) {
      this.http.put<DocumentTypeRow>(`${this.api}/${this.editingId}`, body).subscribe({
        next: () => {
          this.messages.add({
            severity: 'success',
            summary: this.i18n.t('docTypes.toastUpdated'),
            detail: this.i18n.t('docTypes.updateSuccess'),
          });
          this.dialogVisible.set(false);
          this.refreshTable();
        },
        error: (err) => this.toastError(err, 'docTypes.updateError'),
      });
    }
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
