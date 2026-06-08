import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AfterViewInit, Component, DestroyRef, Input, OnInit, ViewChild, inject, signal } from '@angular/core';
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
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { DialogModule } from 'primeng/dialog';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { AlertesCountService } from '../../services/alertes-count.service';
import { BordereauPendingBadgeComponent } from '../bordereau/bordereau-pending-badge.component';
import {
  AlertesBordereauRow,
  AlertesBordereauxTableConfig,
} from './alertes-bordereaux-table.model';

interface BordereauPage {
  content: AlertesBordereauRow[];
  totalElements: number;
}

@Component({
  selector: 'app-alertes-bordereaux-table',
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
    DialogModule,
    BordereauPendingBadgeComponent,
    TranslocoPipe,
  ],
  templateUrl: './alertes-bordereaux-table.component.html',
  styleUrl: './alertes-bordereaux-table.component.scss',
  providers: [MessageService],
})
export class AlertesBordereauxTableComponent implements OnInit, AfterViewInit {
  @Input({ required: true }) config!: AlertesBordereauxTableConfig;

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly alertesCount = inject(AlertesCountService);
  private readonly searchDebounced = new Subject<void>();

  private readonly bordereauxApi = `${environment.apiUrl}/api/bordereaux`;

  @ViewChild('dt') dt?: Table;

  readonly rows = signal<AlertesBordereauRow[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);
  readonly deletingId = signal<number | null>(null);
  readonly supprimerDialogVisible = signal(false);
  readonly rowToDelete = signal<AlertesBordereauRow | null>(null);
  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportDefault'));

  searchText = signal('');
  pageSize = 10;

  constructor() {
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshTable());
  }

  ngOnInit(): void {
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportDefault'));
    });
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.refreshTable());
  }

  reload(): void {
    this.refreshTable();
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;

    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(rows))
      .append('sort', 'dateTransfert,desc');
    params = addQuery(params, this.searchText());

    const api = `${environment.apiUrl}/api/bordereaux/${this.config.apiPath}`;
    this.loading.set(true);
    this.http.get<BordereauPage>(api, { params }).subscribe({
      next: (p) => {
        this.rows.set(p.content ?? []);
        this.totalRecords.set(p.totalElements ?? 0);
        this.loading.set(false);
        this.alertesCount.refresh();
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, this.config.loadErrorKey);
      },
    });
  }

  refreshTable(): void {
    queueMicrotask(() => this.dt?.reset());
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  countLabel(): string {
    const n = this.totalRecords();
    if (n === 0) {
      return this.i18n.t(this.config.countZeroKey);
    }
    if (n === 1) {
      return this.i18n.t(this.config.countOneKey);
    }
    return this.i18n.t(this.config.countManyKey, { count: n });
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  openPrimaryAction(row: AlertesBordereauRow): void {
    if (this.config.mode === 'validation') {
      void this.router.navigate(['/home', 'alertes-echeances', 'consulter', String(row.id)], {
        queryParams: { validationAgents: '1' },
      });
      return;
    }
    void this.router.navigate(['/home', 'alertes-echeances', 'reprendre', String(row.id)]);
  }

  openSupprimer(row: AlertesBordereauRow): void {
    this.rowToDelete.set(row);
    this.supprimerDialogVisible.set(true);
  }

  onSupprimerDialogVisibleChange(visible: boolean): void {
    if (visible) return;
    this.supprimerDialogVisible.set(false);
    this.rowToDelete.set(null);
  }

  supprimerBordereau(row: AlertesBordereauRow): void {
    this.deletingId.set(row.id);
    this.http.delete(`${this.bordereauxApi}/${row.id}`).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.supprimerDialogVisible.set(false);
        this.rowToDelete.set(null);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('alertes.slipDeleted'),
          detail: this.i18n.t('alertes.slipDeletedDetail', { numero: row.numeroBordereau }),
          life: 6000,
        });
        this.refreshTable();
        this.alertesCount.refresh();
      },
      error: (err) => {
        this.deletingId.set(null);
        this.toastError(err, 'alertes.deleteSlipError');
      },
    });
  }

  formatDate(iso: string): string {
    if (!iso) {
      return this.emDash();
    }
    const d = new Date(iso + 'T12:00:00');
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(this.i18n.localeId());
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
