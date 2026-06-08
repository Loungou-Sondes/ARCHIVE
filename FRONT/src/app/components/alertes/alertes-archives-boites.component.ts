import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

export interface BoiteSemiActifAlerteRow {
  boiteId: number;
  boiteTitre: string;
  bordereauId: number;
  numeroBordereau: string;
  regleId: number;
  regleReference: string;
  documentTypeId: number;
  documentTypeTitle: string;
  finalDecision: string;
  anneeAffichage: number | null;
  actionEcheance: boolean;
}

export interface BoiteEcheanceAlerteRow {
  boiteId: number;
  boiteTitre: string;
  bordereauId: number;
  numeroBordereau: string;
  regleId: number;
  regleReference: string;
  documentTypeTitle: string;
  finalDecision: string;
  anneeEcheance: number;
}

export type BoiteAlerteCard =
  | { kind: 'semi-actif'; row: BoiteSemiActifAlerteRow }
  | { kind: 'echeance'; row: BoiteEcheanceAlerteRow };

interface BoiteSemiActifPage {
  content: BoiteSemiActifAlerteRow[];
  totalElements: number;
}

interface BoiteEcheancePage {
  content: BoiteEcheanceAlerteRow[];
  totalElements: number;
}

interface ApprovePendingRow {
  boiteId: number;
  boiteTitre: string;
  finalDecision: string;
  annee: number | null;
  semiActiveUnknown: boolean;
}

interface ReporterYearOption {
  label: string;
  value: number;
}

@Component({
  selector: 'app-alertes-archives-boites',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    DialogModule,
    InputTextModule,
    SelectModule,
    ToastModule,
    TranslocoPipe,
  ],
  templateUrl: './alertes-archives-boites.component.html',
  styleUrl: './alertes-archives-boites.component.scss',
  providers: [MessageService],
})
export class AlertesArchivesBoitesComponent {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchDebounced = new Subject<void>();
  private readonly boitesApi = `${environment.apiUrl}/api/boites`;

  readonly adminOnly = input(true);

  readonly totalChange = output<number>();

  readonly cards = signal<BoiteAlerteCard[]>([]);
  readonly loading = signal(false);
  readonly searchText = signal('');

  readonly reportingId = signal<number | null>(null);
  readonly approvingId = signal<number | null>(null);
  readonly reporterDialogVisible = signal(false);
  readonly pendingReporterRow = signal<BoiteSemiActifAlerteRow | null>(null);
  readonly selectedReporterYear = signal<number | null>(null);
  readonly approuverDialogVisible = signal(false);
  readonly pendingApproveRow = signal<ApprovePendingRow | null>(null);

  readonly reporterYearOptions: ReporterYearOption[] = this.buildReporterYearOptions();

  constructor() {
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.reload());
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  reload(): void {
    if (!this.adminOnly()) {
      this.cards.set([]);
      this.totalChange.emit(0);
      return;
    }
    this.loading.set(true);
    let params = new HttpParams().set('page', '0').set('size', '200');
    params = addQuery(params, this.searchText());
    this.http
      .get<BoiteSemiActifPage>(`${this.boitesApi}/alertes-semi-actif`, { params })
      .subscribe({
        next: (semi) => {
          this.http
            .get<BoiteEcheancePage>(`${this.boitesApi}/alertes-echeance-destruction-transfert`, { params })
            .subscribe({
              next: (ech) => {
                const semiRows = (semi.content ?? []).map((row) => ({ kind: 'semi-actif' as const, row }));
                const semiIds = new Set(semiRows.map((c) => c.row.boiteId));
                const echRows = (ech.content ?? [])
                  .filter((row) => !semiIds.has(row.boiteId))
                  .map((row) => ({ kind: 'echeance' as const, row }));
                const merged: BoiteAlerteCard[] = [...semiRows, ...echRows];
                this.cards.set(merged);
                const total = (semi.totalElements ?? 0) + (ech.totalElements ?? 0);
                this.totalChange.emit(total);
                this.loading.set(false);
              },
              error: (err) => {
                this.loading.set(false);
                this.toastError(err, 'alertes.loadEcheanceError');
              },
            });
        },
        error: (err) => {
          this.loading.set(false);
          this.toastError(err, 'alertes.loadSemiActifError');
        },
      });
  }

  cardTitre(card: BoiteAlerteCard): string {
    return card.row.boiteTitre;
  }

  isUrgent(card: BoiteAlerteCard): boolean {
    if (card.kind === 'echeance') {
      return true;
    }
    return card.row.actionEcheance;
  }

  isDetruire(dec: string): boolean {
    return dec === 'DETRUIRE' || dec === 'ELIMINER';
  }

  openKeep(card: BoiteAlerteCard): void {
    if (card.kind !== 'semi-actif') {
      return;
    }
    this.closeAllDialogs();
    this.pendingReporterRow.set(card.row);
    this.selectedReporterYear.set(new Date().getFullYear() + 1);
    this.reporterDialogVisible.set(true);
  }

  openPrimaryAction(card: BoiteAlerteCard): void {
    if (card.kind === 'echeance') {
      this.closeAllDialogs();
      this.pendingApproveRow.set({
        boiteId: card.row.boiteId,
        boiteTitre: card.row.boiteTitre,
        finalDecision: card.row.finalDecision,
        annee: card.row.anneeEcheance,
        semiActiveUnknown: false,
      });
      this.approuverDialogVisible.set(true);
      return;
    }
    if (card.row.actionEcheance) {
      this.closeAllDialogs();
      this.pendingApproveRow.set({
        boiteId: card.row.boiteId,
        boiteTitre: card.row.boiteTitre,
        finalDecision: card.row.finalDecision,
        annee: null,
        semiActiveUnknown: true,
      });
      this.approuverDialogVisible.set(true);
    }
  }

  approuverDialogHeader(): string {
    const row = this.pendingApproveRow();
    if (!row) {
      return this.i18n.t('alertes.confirmAction');
    }
    return this.isDetruire(row.finalDecision)
      ? this.i18n.t('alertes.validateDestroy')
      : this.i18n.t('alertes.validateTransfer');
  }

  onReporterVisibleChange(visible: boolean): void {
    if (visible) {
      return;
    }
    this.reporterDialogVisible.set(false);
    this.pendingReporterRow.set(null);
    this.selectedReporterYear.set(null);
  }

  onApprouverVisibleChange(visible: boolean): void {
    if (visible) {
      return;
    }
    this.approuverDialogVisible.set(false);
    this.pendingApproveRow.set(null);
  }

  private closeAllDialogs(): void {
    this.reporterDialogVisible.set(false);
    this.pendingReporterRow.set(null);
    this.selectedReporterYear.set(null);
    this.approuverDialogVisible.set(false);
    this.pendingApproveRow.set(null);
  }

  primaryActionLabel(card: BoiteAlerteCard): string {
    const decision = card.row.finalDecision;
    return this.isDetruire(decision) ? this.i18n.t('alertes.destroy') : this.i18n.t('alertes.transfer');
  }

  primaryActionIcon(card: BoiteAlerteCard): string {
    return this.isDetruire(card.row.finalDecision) ? 'pi pi-trash' : 'pi pi-send';
  }

  showKeepButton(card: BoiteAlerteCard): boolean {
    return card.kind === 'semi-actif';
  }

  showPrimaryActionButton(card: BoiteAlerteCard): boolean {
    return card.kind === 'echeance' || (card.kind === 'semi-actif' && card.row.actionEcheance);
  }

  primaryActionIsDestroy(card: BoiteAlerteCard): boolean {
    return this.isDetruire(card.row.finalDecision);
  }

  confirmReporter(): void {
    const row = this.pendingReporterRow();
    const annee = this.selectedReporterYear();
    if (!row || annee == null) {
      return;
    }
    this.reportingId.set(row.boiteId);
    this.http.post(`${this.boitesApi}/${row.boiteId}/reporter-alerte-semi-actif`, { annee }).subscribe({
      next: () => {
        this.reportingId.set(null);
        this.reporterDialogVisible.set(false);
        this.pendingReporterRow.set(null);
        this.selectedReporterYear.set(null);
        this.messages.add({
          severity: 'info',
          summary: this.i18n.t('alertes.alertPostponed'),
          detail: this.i18n.t('alertes.reappearInYear', { annee }),
          life: 8000,
        });
        this.reload();
      },
      error: (err) => {
        this.reportingId.set(null);
        this.toastError(err, 'alertes.postponeError');
      },
    });
  }

  confirmApprouver(): void {
    const row = this.pendingApproveRow();
    if (!row) {
      return;
    }
    this.approvingId.set(row.boiteId);
    this.http.post(`${this.boitesApi}/${row.boiteId}/approuver-destruction-transfert`, {}).subscribe({
      next: () => {
        this.approvingId.set(null);
        this.approuverDialogVisible.set(false);
        this.pendingApproveRow.set(null);
        const destroy = this.isDetruire(row.finalDecision);
        this.messages.add({
          severity: 'success',
          summary: destroy ? this.i18n.t('alertes.destroyValidated') : this.i18n.t('alertes.transferValidated'),
          detail: destroy
            ? this.i18n.t('alertes.boxDestroyed', { titre: row.boiteTitre })
            : this.i18n.t('alertes.boxTransferred', { titre: row.boiteTitre }),
          life: 8000,
        });
        this.reload();
      },
      error: (err) => {
        this.approvingId.set(null);
        this.toastError(err, 'alertes.validateError');
      },
    });
  }

  private buildReporterYearOptions(): ReporterYearOption[] {
    const currentYear = new Date().getFullYear();
    const options: ReporterYearOption[] = [];
    for (let y = currentYear + 1; y <= currentYear + 50; y++) {
      options.push({ label: String(y), value: y });
    }
    return options;
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 8000,
    });
  }
}
