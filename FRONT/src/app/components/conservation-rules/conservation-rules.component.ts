import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  inject,
  OnInit,
  signal,
  ViewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { Table, TableLazyLoadEvent, TableModule } from 'primeng/table';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

export interface ConservationRulePage {
  content: ConservationRuleRow[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export interface DocumentTypeMini {
  id: number;
  title: string;
}

export interface ConservationRuleRow {
  id: number;
  reference: string;
  documentType: DocumentTypeMini;
  activeUnknown: boolean;
  activeYears: number | null;
  semiActiveUnknown: boolean;
  semiActiveYears: number | null;
  finalDecision: string;
  status: string;
  durationAlert?: boolean;
}

type FinalDecisionValue = 'CONSERVER' | 'DETRUIRE' | 'TRANSFERER';

interface DocumentTypeOption {
  id: number;
  title: string;
}

interface DocumentTypeListPage {
  content: { id: number; title: string }[];
  totalElements: number;
}

interface InvalidationLinkedBoite {
  id: number;
  titre: string;
  anneeMin: number;
  anneeMax: number;
  metrageCm: number;
}

interface InvalidationLinkedBordereau {
  bordereauId: number;
  numeroAffiche: string;
  boites: InvalidationLinkedBoite[];
}

interface InvalidationPreview {
  ruleId: number;
  reference: string;
  documentTypeId: number;
  documentTypeTitle: string;
  totalBoites: number;
  bordereaux: InvalidationLinkedBordereau[];
}

@Component({
  selector: 'app-conservation-rules',
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
    InputNumberModule,
    CheckboxModule,
    ToastModule,
    TooltipModule,
    TranslocoPipe,
  ],
  templateUrl: './conservation-rules.component.html',
  styleUrl: './conservation-rules.component.scss',
  providers: [MessageService],
})
export class ConservationRulesComponent implements OnInit, AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchDebounced = new Subject<void>();

  private readonly api = `${environment.apiUrl}/api/conservation-rules`;
  private readonly documentTypesApi = `${environment.apiUrl}/api/document-types`;

  @ViewChild('dt') dt?: Table;

  readonly rows = signal<ConservationRuleRow[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(false);
  readonly documentTypeOptions = signal<DocumentTypeOption[]>([]);
  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportEntries'));

  searchText = signal('');
  showFilters = signal(false);
  /** '' = tous les statuts dans la même liste. */
  filterStatus = signal<string>('');
  /** '' = toutes les décisions finales. */
  filterFinalDecision = signal<string>('');

  sortField = signal<'id' | 'reference'>('id');
  sortOrder: 1 | -1 = 1;

  pageSize = 10;

  dialogVisible = signal(false);
  /** Après invalidation : référence et type repris de l'ancienne règle, non modifiables. */
  readonly createFormRefTypesLocked = signal(false);
  /** Remplacement avant invalidation : seul le type est figé. */
  readonly createFormTypeLocked = signal(false);

  viewDialogVisible = signal(false);
  viewRow = signal<ConservationRuleRow | null>(null);

  invalidateConfirmVisible = signal(false);
  invalidateLinkedVisible = signal(false);
  readonly invalidationPreview = signal<InvalidationPreview | null>(null);
  readonly invalidationPreviewLoading = signal(false);
  pendingInvalidate = signal<ConservationRuleRow | null>(null);
  /** Ancienne règle à invalider après création de la règle de remplacement. */
  readonly pendingReplacementSourceRule = signal<ConservationRuleRow | null>(null);

  invalidateFollowupVisible = signal(false);
  invalidatedForFollowup = signal<ConservationRuleRow | null>(null);

  formReference = '';
  /** Identifiant du type de document (une seule règle ↔ un seul type). */
  formDocumentTypeId: number | null = null;
  formFinalDecision: FinalDecisionValue = 'CONSERVER';
  formActiveUnknown = false;
  formActiveYears: number | null = 0;
  formSemiActiveUnknown = false;
  formSemiActiveYears: number | null = null;

  finalDecisionOptions: { label: string; value: FinalDecisionValue }[] = [];
  statusFilterOptions: { label: string; value: string }[] = [];
  finalDecisionFilterOptions: { label: string; value: string }[] = [];

  ngOnInit(): void {
    document.documentElement.style.removeProperty('--regles-toast-center-x');
    this.rebuildTranslatedOptions();
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.rebuildTranslatedOptions();
      this.pageReportTemplate.set(this.i18n.t('common.pageReportEntries'));
    });
    this.loadDocumentTypeOptions();
    this.searchDebounced
      .pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.refreshTable());
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.refreshTable());
  }

  private rebuildTranslatedOptions(): void {
    this.finalDecisionOptions = [
      { label: this.i18n.t('common.decisionKeep'), value: 'CONSERVER' },
      { label: this.i18n.t('common.decisionDestroy'), value: 'DETRUIRE' },
      { label: this.i18n.t('common.decisionTransfer'), value: 'TRANSFERER' },
    ];
    this.statusFilterOptions = [
      { label: this.i18n.t('regles.allStatuses'), value: '' },
      { label: this.i18n.t('common.statusValid'), value: 'VALIDE' },
      { label: this.i18n.t('common.statusInvalid'), value: 'INVALIDE' },
    ];
    this.finalDecisionFilterOptions = [
      { label: this.i18n.t('regles.allDecisions'), value: '' },
      ...this.finalDecisionOptions,
    ];
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  loadDocumentTypeOptions(): void {
    const params = new HttpParams().set('page', '0').set('size', '500').append('sort', 'title,asc');
    this.http.get<DocumentTypeListPage>(this.documentTypesApi, { params }).subscribe({
      next: (p) => {
        const rows = (p.content ?? []).map((r) => ({ id: r.id, title: r.title }));
        this.documentTypeOptions.set(rows);
      },
      error: (err) => {
        this.toastError(err, 'regles.loadDocTypesError');
      },
    });
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;

    let sort = 'id';
    let dir = 'asc';
    if (this.sortField() === 'reference') {
      sort = 'reference';
      dir = this.sortOrder === 1 ? 'asc' : 'desc';
    }

    let params = new HttpParams().set('page', String(page)).set('size', String(rows));
    params = params.append('sort', `${sort},${dir}`);
    params = addQuery(params, this.searchText());
    const st = this.filterStatus();
    if (st) {
      params = params.set('status', st);
    }
    const fd = this.filterFinalDecision();
    if (fd) {
      params = params.set('finalDecision', fd);
    }

    this.loading.set(true);
    this.http.get<ConservationRulePage>(this.api, { params }).subscribe({
      next: (p) => {
        this.rows.set(p.content ?? []);
        this.totalRecords.set(p.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'regles.loadError');
      },
    });
  }

  refreshTable(): void {
    queueMicrotask(() => this.dt?.reset());
  }

  toggleFilters(): void {
    this.showFilters.update((v) => !v);
  }

  cycleSort(): void {
    if (this.sortField() === 'id') {
      this.sortField.set('reference');
      this.sortOrder = 1;
    } else if (this.sortOrder === 1) {
      this.sortOrder = -1;
    } else {
      this.sortField.set('id');
      this.sortOrder = 1;
    }
    this.refreshTable();
  }

  clearSearch(): void {
    this.searchText.set('');
    this.filterStatus.set('');
    this.filterFinalDecision.set('');
    this.sortField.set('id');
    this.sortOrder = 1;
    this.refreshTable();
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  onFilterStatusChange(): void {
    this.refreshTable();
  }

  onFilterFinalDecisionChange(): void {
    this.refreshTable();
  }

  countLabel(): string {
    const n = this.totalRecords();
    if (n === 0) {
      return this.i18n.t('regles.countZero');
    }
    if (n === 1) {
      return this.i18n.t('regles.countOne');
    }
    return this.i18n.t('regles.countMany', { count: n });
  }

  sortTooltip(): string {
    if (this.sortField() === 'id') {
      return this.i18n.t('regles.sortByReferenceAsc');
    }
    if (this.sortOrder === 1) {
      return this.i18n.t('regles.sortByReferenceDesc');
    }
    return this.i18n.t('regles.sortById');
  }

  typeLabel(row: ConservationRuleRow): string {
    return row.documentType?.title?.trim() || this.emDash();
  }

  formatActive(row: ConservationRuleRow): string {
    if (row.activeUnknown) {
      return this.i18n.t('common.unknownDuration');
    }
    if (row.activeYears == null) {
      return this.emDash();
    }
    return this.formatYearsCount(row.activeYears);
  }

  formatSemiActive(row: ConservationRuleRow): string {
    if (this.isConserverFinalDecision(row.finalDecision)) {
      return this.emDash();
    }
    if (row.semiActiveUnknown) {
      return this.i18n.t('common.unknownDuration');
    }
    if (row.semiActiveYears == null) {
      return this.emDash();
    }
    return this.formatYearsCount(row.semiActiveYears);
  }

  decisionLabel(dec: string): string {
    switch (dec) {
      case 'CONSERVER':
        return this.i18n.t('common.decisionKeep');
      case 'DETRUIRE':
      case 'ELIMINER':
        return this.i18n.t('common.decisionDestroy');
      case 'TRANSFERER':
        return this.i18n.t('common.decisionTransfer');
      default:
        return dec;
    }
  }

  decisionClass(dec: string): string {
    if (dec === 'DETRUIRE' || dec === 'ELIMINER') {
      return 'decision-badge decision-badge--elim';
    }
    if (dec === 'TRANSFERER') {
      return 'decision-badge decision-badge--xfer';
    }
    return 'decision-badge decision-badge--cons';
  }

  statusLabel(st: string): string {
    switch (st) {
      case 'VALIDE':
        return this.i18n.t('common.statusValid');
      case 'INVALIDE':
        return this.i18n.t('common.statusInvalid');
      default:
        return st;
    }
  }

  statusClass(st: string): string {
    if (st === 'VALIDE') {
      return 'status-badge status-badge--ok';
    }
    if (st === 'INVALIDE') {
      return 'status-badge status-badge--inv';
    }
    return 'status-badge status-badge--muted';
  }

  isRuleValid(row: ConservationRuleRow): boolean {
    return row.status === 'VALIDE';
  }

  isCreateTypeLocked(): boolean {
    return this.createFormRefTypesLocked() || this.createFormTypeLocked();
  }

  /** Remplacement ou reprise après invalidation : référence + type figés. */
  isCreateIdentificationLocked(): boolean {
    return this.createFormRefTypesLocked();
  }

  showSemiActiveSection(): boolean {
    return this.formFinalDecision === 'DETRUIRE' || this.formFinalDecision === 'TRANSFERER';
  }

  onFinalDecisionChange(value: FinalDecisionValue): void {
    if (value === 'CONSERVER') {
      this.formSemiActiveUnknown = false;
      this.formSemiActiveYears = null;
    } else {
      this.formSemiActiveUnknown = false;
      if (this.formSemiActiveYears == null) {
        this.formSemiActiveYears = 0;
      }
    }
  }

  onActiveUnknownChange(unknown: boolean): void {
    if (unknown) {
      this.formActiveYears = null;
    } else if (this.formActiveYears == null) {
      this.formActiveYears = 0;
    }
  }

  onSemiActiveUnknownChange(unknown: boolean): void {
    if (unknown) {
      this.formSemiActiveYears = null;
    } else if (this.formSemiActiveYears == null) {
      this.formSemiActiveYears = 0;
    }
  }

  openCreate(): void {
    this.openCreateInternal(null);
  }

  openInvalidateConfirm(row: ConservationRuleRow): void {
    if (!this.isRuleValid(row)) {
      return;
    }
    this.pendingInvalidate.set(row);
    this.invalidationPreview.set(null);
    this.invalidationPreviewLoading.set(true);
    this.http.get<InvalidationPreview>(`${this.api}/${row.id}/invalidation-preview`).subscribe({
      next: (preview) => {
        this.invalidationPreview.set(preview);
        this.invalidationPreviewLoading.set(false);
        if (preview.totalBoites > 0) {
          this.invalidateLinkedVisible.set(true);
        } else {
          this.invalidateConfirmVisible.set(true);
        }
      },
      error: (err) => {
        this.invalidationPreviewLoading.set(false);
        this.pendingInvalidate.set(null);
        this.toastError(err, 'regles.linkedPreviewError');
      },
    });
  }

  onInvalidateConfirmVisibleChange(visible: boolean): void {
    this.invalidateConfirmVisible.set(visible);
  }

  onInvalidateLinkedVisibleChange(visible: boolean): void {
    this.invalidateLinkedVisible.set(visible);
  }

  onInvalidateConfirmHide(): void {
    if (!this.invalidateLinkedVisible()) {
      this.pendingInvalidate.set(null);
      this.invalidationPreview.set(null);
    }
  }

  onInvalidateLinkedHide(): void {
    this.pendingInvalidate.set(null);
    this.invalidationPreview.set(null);
  }

  cancelInvalidateConfirm(): void {
    this.invalidateConfirmVisible.set(false);
    this.pendingInvalidate.set(null);
  }

  cancelInvalidateLinked(): void {
    this.invalidateLinkedVisible.set(false);
    this.pendingInvalidate.set(null);
    this.invalidationPreview.set(null);
  }

  executeInvalidate(): void {
    const row = this.pendingInvalidate();
    if (!row) {
      return;
    }
    this.http
      .post<ConservationRuleRow>(`${this.api}/${row.id}/invalidate`, { boiteStrategy: 'KEEP_ON_OLD_RULE' })
      .subscribe({
        next: () => {
          const snapshot = row;
          this.invalidateConfirmVisible.set(false);
          this.afterRuleInvalidated(snapshot);
        },
        error: (err) => this.toastError(err, 'regles.invalidateError'),
      });
  }

  keepBoitesOnOldRuleAndInvalidate(): void {
    const row = this.pendingInvalidate();
    if (!row) {
      return;
    }
    this.http
      .post<ConservationRuleRow>(`${this.api}/${row.id}/invalidate`, { boiteStrategy: 'KEEP_ON_OLD_RULE' })
      .subscribe({
        next: () => {
          const snapshot = row;
          this.invalidateLinkedVisible.set(false);
          this.invalidationPreview.set(null);
          this.pendingInvalidate.set(null);
          this.afterRuleInvalidated(snapshot);
        },
        error: (err) => this.toastError(err, 'regles.invalidateError'),
      });
  }

  migrateBoitesToNewRule(): void {
    const row = this.pendingInvalidate();
    if (!row) {
      return;
    }
    this.pendingReplacementSourceRule.set(row);
    this.invalidateLinkedVisible.set(false);
    this.invalidationPreview.set(null);
    this.pendingInvalidate.set(null);
    this.openCreateReplacement(row);
    this.messages.add({
      severity: 'info',
      summary: this.i18n.t('regles.migrateInfoSummary'),
      detail: this.i18n.t('regles.migrateInfoDetail'),
      life: 10000,
    });
  }

  private afterRuleInvalidated(snapshot: ConservationRuleRow): void {
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t('common.success'),
      detail: this.i18n.t('regles.invalidateSuccessDetail'),
    });
    this.invalidatedForFollowup.set(snapshot);
    this.invalidateFollowupVisible.set(true);
    this.refreshTable();
  }

  onInvalidateFollowupVisibleChange(visible: boolean): void {
    this.invalidateFollowupVisible.set(visible);
    if (!visible) {
      this.invalidatedForFollowup.set(null);
    }
  }

  closeInvalidateFollowup(createReplacement: boolean): void {
    const row = this.invalidatedForFollowup();
    this.invalidateFollowupVisible.set(false);
    this.invalidatedForFollowup.set(null);
    if (createReplacement && row) {
      this.openCreateInternal(row);
      this.refreshTable();
    }
  }

  openView(row: ConservationRuleRow): void {
    this.viewRow.set(row);
    this.viewDialogVisible.set(true);
  }

  onViewDialogVisibleChange(visible: boolean): void {
    if (!visible) {
      this.viewDialogVisible.set(false);
      this.viewRow.set(null);
    }
  }

  durationCardValue(row: ConservationRuleRow, kind: 'active' | 'semi' | 'total'): string {
    if (kind === 'active') {
      return this.formatYearsUnit(row.activeUnknown, row.activeYears);
    }
    if (kind === 'semi') {
      if (this.isConserverFinalDecision(row.finalDecision)) {
        return this.emDash();
      }
      return this.formatYearsUnit(row.semiActiveUnknown, row.semiActiveYears);
    }
    if (row.activeUnknown) {
      return this.i18n.t('regles.indeterminateDuration');
    }
    if (!this.isConserverFinalDecision(row.finalDecision) && row.semiActiveUnknown) {
      return this.i18n.t('regles.indeterminateDuration');
    }
    const active = row.activeYears ?? 0;
    const semi = this.isConserverFinalDecision(row.finalDecision) ? 0 : (row.semiActiveYears ?? 0);
    const total = active + semi;
    if (total <= 0) {
      return this.emDash();
    }
    return this.formatYearsCount(total);
  }

  private formatYearsUnit(unknown: boolean, years: number | null): string {
    if (unknown) {
      return this.i18n.t('regles.indeterminateDuration');
    }
    if (years == null) {
      return this.emDash();
    }
    return this.formatYearsCount(years);
  }

  private formatYearsCount(years: number): string {
    return years === 1
      ? this.i18n.t('regles.yearOne')
      : this.i18n.t('regles.yearsCount', { count: years });
  }

  save(): void {
    const ref = this.formReference?.trim();
    if (!ref) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.referenceWarnSummary'),
        detail: this.i18n.t('regles.referenceRequired'),
      });
      return;
    }
    if (this.formDocumentTypeId == null) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.docTypeWarnSummary'),
        detail: this.i18n.t('regles.docTypeRequired'),
      });
      return;
    }
    if (!this.formActiveUnknown && (this.formActiveYears == null || this.formActiveYears < 0)) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.activeYearsWarnSummary'),
        detail: this.i18n.t('regles.activeYearsRequired'),
      });
      return;
    }
    if (
      this.showSemiActiveSection() &&
      !this.formSemiActiveUnknown &&
      (this.formSemiActiveYears == null || this.formSemiActiveYears < 0)
    ) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.semiActiveYearsWarnSummary'),
        detail: this.i18n.t('regles.semiActiveYearsRequired'),
      });
      return;
    }

    const replacementSource = this.pendingReplacementSourceRule();
    const body = {
      reference: ref,
      documentTypeId: this.formDocumentTypeId,
      finalDecision: this.formFinalDecision,
      activeUnknown: this.formActiveUnknown,
      activeYears: this.formActiveUnknown ? null : this.formActiveYears,
      semiActiveUnknown: this.showSemiActiveSection() ? this.formSemiActiveUnknown : false,
      semiActiveYears:
        this.showSemiActiveSection() && !this.formSemiActiveUnknown ? this.formSemiActiveYears : null,
    };
    const url = replacementSource ? `${this.api}/${replacementSource.id}/replace` : this.api;
    this.http.post<ConservationRuleRow>(url, body).subscribe({
      next: () => {
        if (replacementSource) {
          this.pendingReplacementSourceRule.set(null);
          this.createFormRefTypesLocked.set(false);
          this.createFormTypeLocked.set(false);
          this.messages.add({
            severity: 'success',
            summary: this.i18n.t('regles.replacementSuccessSummary'),
            detail: this.i18n.t('regles.replacementSuccessDetail', {
              reference: replacementSource.reference,
            }),
            life: 10000,
          });
        } else {
          this.messages.add({
            severity: 'success',
            summary: this.i18n.t('regles.savedSummary'),
            detail: this.buildCreateSuccessDetail(body),
            life: body.activeUnknown || body.semiActiveUnknown ? 12000 : 5000,
          });
        }
        this.dialogVisible.set(false);
        this.refreshTable();
      },
      error: (err) =>
        this.toastError(
          err,
          replacementSource ? this.i18n.t('regles.replaceError') : this.i18n.t('regles.createError'),
        ),
    });
  }

  private isConserverFinalDecision(dec: string): boolean {
    return dec === 'CONSERVER';
  }

  /** Message de succès après création selon les durées inconnues renseignées. */
  private buildCreateSuccessDetail(body: {
    activeUnknown: boolean;
    semiActiveUnknown: boolean;
  }): string {
    const activeUnknown = Boolean(body.activeUnknown);
    const semiUnknown =
      this.showSemiActiveSection() && Boolean(body.semiActiveUnknown);

    if (activeUnknown && semiUnknown) {
      return this.i18n.t('regles.createSuccessBothUnknown');
    }
    if (activeUnknown) {
      return this.i18n.t('regles.createSuccessActiveUnknown');
    }
    if (semiUnknown) {
      return this.i18n.t('regles.createSuccessSemiUnknown');
    }
    return this.i18n.t('regles.createSuccessDefault');
  }

  onCreateDialogVisibleChange(visible: boolean): void {
    this.dialogVisible.set(visible);
    if (!visible) {
      this.createFormRefTypesLocked.set(false);
      this.createFormTypeLocked.set(false);
      this.pendingReplacementSourceRule.set(null);
    }
  }

  createDialogTitle(): string {
    if (this.pendingReplacementSourceRule()) {
      return this.i18n.t('regles.createTitleReplacementLinked');
    }
    if (this.createFormRefTypesLocked()) {
      return this.i18n.t('regles.createTitleReplacement');
    }
    return this.i18n.t('regles.createTitleDefault');
  }

  createDialogSubtitle(): string {
    if (this.pendingReplacementSourceRule()) {
      return this.i18n.t('regles.createSubtitleReplacement');
    }
    return this.i18n.t('regles.createSubtitleDefault');
  }

  replacementBannerText(reference: string): string {
    return this.i18n.t('regles.replacementBanner', { reference });
  }

  linkedLeadText(reference: string, documentType: string, count: number): string {
    return this.i18n.t('regles.linkedLead', { reference, documentType, count });
  }

  invalidateConfirmTitle(reference: string): string {
    return this.i18n.t('regles.invalidateConfirmTitle', { reference });
  }

  unknownInputDisabledTitle(): string {
    return this.i18n.t('regles.unknownInputDisabledTitle');
  }

  private openCreateReplacement(source: ConservationRuleRow): void {
    this.createFormRefTypesLocked.set(true);
    this.createFormTypeLocked.set(false);
    this.formReference = (source.reference ?? '').trim();
    this.formDocumentTypeId = source.documentType?.id ?? null;
    this.formFinalDecision = 'CONSERVER';
    this.formActiveUnknown = false;
    this.formActiveYears = 0;
    this.formSemiActiveUnknown = false;
    this.formSemiActiveYears = null;
    this.dialogVisible.set(true);
  }

  private openCreateInternal(prefillFrom: ConservationRuleRow | null): void {
    this.pendingReplacementSourceRule.set(null);
    this.createFormTypeLocked.set(false);
    if (prefillFrom) {
      this.createFormRefTypesLocked.set(true);
      this.formReference = (prefillFrom.reference ?? '').trim();
      this.formDocumentTypeId = prefillFrom.documentType?.id ?? null;
    } else {
      this.createFormRefTypesLocked.set(false);
      this.formReference = '';
      this.formDocumentTypeId = null;
    }
    this.formFinalDecision = 'CONSERVER';
    this.formActiveUnknown = false;
    this.formActiveYears = 0;
    this.formSemiActiveUnknown = false;
    this.formSemiActiveYears = null;
    this.dialogVisible.set(true);
  }

  private toastError(err: unknown, fallbackKey: string): void {
    const code = (err as { error?: { code?: string } })?.error?.code;

    if (code === 'RULE_HAS_LINKED_BOITES') {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.linkedBoxesSummary'),
        detail: this.i18n.apiErrorDetail(err, 'regles.linkedBoxesFallback'),
        life: 12000,
      });
      return;
    }

    if (code === 'DUPLICATE_DOCUMENT_TYPE') {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('regles.duplicateDocTypeSummary'),
        detail: this.i18n.apiErrorDetail(err, 'regles.duplicateDocTypeFallback'),
        life: 12000,
      });
      return;
    }

    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 12000,
    });
  }
}
