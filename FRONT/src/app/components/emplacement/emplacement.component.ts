import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { ProgressBarModule } from 'primeng/progressbar';
import { RippleModule } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { ConfirmationService, MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { environment } from '../../../environments/environment';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { MenuStateService } from '../../services/menu-state.service';
import { Epi3dViewComponent } from './epi-3d-view.component';
import type {
  BlocBoiteSummary,
  BlocDto,
  EpiMatrixDto,
  EpiSummary,
  TabletteCellDto,
} from './emplacement-matrix.models';

export type { BlocBoiteSummary, BlocDto, EpiMatrixDto, EpiSummary, TabletteCellDto };

interface EpiListPage {
  content: EpiSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  totalLinearCm: number;
}

interface EpiBulkImportResult {
  createdCount: number;
  numeros: string[];
}

/** Segment affiché dans la bande de blocs (carte seule ou fusion de n blocs d’une même boîte). */
export type BlocStripSegment =
  | { kind: 'empty'; bloc: BlocDto }
  | { kind: 'occupied'; blocs: BlocDto[]; fused: boolean };

interface BordereauDetailForBoite {
  id: number;
  numeroBordereau: string;
  /** Compteur stocké sur BORDEREAUX.NOMBRE_BOITES */
  nombreBoites?: number;
  boites?: unknown[];
}

export interface NextEpiNumeroDto {
  numero: string;
}

interface SemanticSearchHit {
  boiteId: number;
  titre: string;
  score: number;
  epiNumero: string | null;
  emplacement: string | null;
  blocIds: string[];
  onRequestedEpi: boolean;
}

/** Ajout ou suppression d’une ligne / colonne de la matrice épi. */
type StructurePatchOp = 'addRow' | 'addColumn' | 'removeRow' | 'removeColumn';

@Component({
  selector: 'app-emplacement',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    RippleModule,
    DialogModule,
    InputTextModule,
    InputNumberModule,
    ProgressBarModule,
    ToastModule,
    TooltipModule,
    ConfirmDialogModule,
    TranslocoPipe,
    Epi3dViewComponent,
  ],
  templateUrl: './emplacement.component.html',
  styleUrl: './emplacement.component.scss',
  providers: [MessageService, ConfirmationService],
})
export class EmplacementComponent implements OnInit, AfterViewInit, OnDestroy {
  private static readonly TOAST_CENTER_X_VAR = '--emplacement-toast-center-x';

  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly confirm = inject(ConfirmationService);
  private readonly menuState = inject(MenuStateService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly api = `${environment.apiUrl}/api/emplacements`;

  /** Recalage après transition margin sidebar (~300ms dans `home.component.scss`). */
  private toastLayoutTimer: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    effect(() => {
      this.menuState.collapsed();
      this.scheduleSyncToastCenterX();
    });
  }

  readonly maxDim = 9;
  readonly maxBlocsTablette = 8;
  readonly defaultBlocCm = 10;

  view = signal<'list' | 'matrix'>('list');
  matrixViewMode = signal<'2d' | '3d'>('2d');
  semanticSearchText = '';
  readonly semanticSearching = signal(false);
  readonly semanticHighlightBlocIds = signal<string[]>([]);
  readonly semanticResults = signal<SemanticSearchHit[]>([]);
  loading = signal(false);
  loadingMatrix = signal(false);
  structureBusy = signal(false);

  epis = signal<EpiSummary[]>([]);
  readonly totalRecords = signal(0);
  readonly totalPages = signal(0);
  readonly currentPage = signal(0);
  readonly totalLinearCmAll = signal(0);
  pageSize = 12;
  searchText = signal('');
  private readonly searchDebounced = new Subject<void>();

  readonly emplacementListSummary = computed(() => {
    const searchActive = this.searchText().trim().length > 0;
    return {
      total: this.totalRecords(),
      displayed: this.epis().length,
      searchActive,
      totalCm: this.totalLinearCmAll(),
    };
  });

  matrixData = signal<EpiMatrixDto | null>(null);

  @ViewChild('epiImportInput') epiImportInput?: ElementRef<HTMLInputElement>;

  readonly importBusy = signal(false);

  showAddChoiceDialog = signal(false);
  showExcelImportDialog = signal(false);
  showCreateDialog = signal(false);
  /** Prochain numéro affiché en lecture seule (calcul serveur). */
  nextNumeroPreview = signal(this.i18n.t('common.loading'));
  newTravers: number | null = null;
  newTabletteRows: number | null = null;
  newBlocsPerTablette = 8;

  showResizeDialog = signal(false);
  resizeBusy = signal(false);
  resizeTarget = signal<EpiSummary | null>(null);
  resizeTravers = 3;
  resizeTabletteRows = 3;
  resizeBlocsPerTablette = 8;

  showTabletteDialog = signal(false);
  selectedCell = signal<TabletteCellDto | null>(null);

  showBoiteDetailDialog = signal(false);
  selectedBloc = signal<BlocDto | null>(null);
  readonly bordereauDetailStats = signal<{
    numero: string;
    total: number;
  } | null>(null);
  readonly bordereauStatsLoading = signal(false);

  /** Ligne (index 0-based) ou colonne nouvellement ajoutée : mise en évidence ~3 s. */
  private spotlightClearTimer: ReturnType<typeof setTimeout> | null = null;
  readonly spotlightRow = signal<number | null>(null);
  readonly spotlightCol = signal<number | null>(null);

  ngOnInit(): void {
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshEpis(0));
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.showCreateDialog() && !this.nextNumeroPreview().trim()) {
        this.nextNumeroPreview.set(this.i18n.t('common.loading'));
      }
    });
    this.scheduleSyncToastCenterX();
    this.refreshEpis();
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  hasBordereauNumero(boite: BlocBoiteSummary): boolean {
    return this.bordereauNumeroDisplay(boite) !== this.emDash();
  }

  epiRegisteredLabel(count: number): string {
    return count === 1
      ? this.i18n.t('emplacement.epiRegisteredOne')
      : this.i18n.t('emplacement.epiRegisteredMany');
  }

  filteredDisplayedLabel(count: number): string {
    return this.i18n.t('emplacement.filteredDisplayed', { count });
  }

  totalLinearCmCumulativeLabel(cm: number): string {
    return this.i18n.t('emplacement.totalLinearCmCumulative', { cm });
  }

  traversHeaderAriaLabel(numero: string): string {
    return this.i18n.t('emplacement.traversHeaderAria', { numero });
  }

  blocFreeAriaLabel(numero: string): string {
    return this.i18n.t('emplacement.blocFreeAria', { numero });
  }

  fusedBlocsAriaLabel(blocs: BlocDto[]): string {
    return this.i18n.t('emplacement.fusedBlocsAria', { range: this.fusedBlocsRangeLabel(blocs) });
  }

  occupiedBlocAriaLabel(numero: string): string {
    return this.i18n.t('emplacement.occupiedBlocAria', { numero });
  }

  ngAfterViewInit(): void {
    this.scheduleSyncToastCenterX();
  }

  ngOnDestroy(): void {
    if (this.toastLayoutTimer !== null) {
      clearTimeout(this.toastLayoutTimer);
      this.toastLayoutTimer = null;
    }
    document.documentElement.style.removeProperty(EmplacementComponent.TOAST_CENTER_X_VAR);
    this.clearSpotlight();
  }

  @HostListener('window:resize')
  protected onWindowResize(): void {
    this.scheduleSyncToastCenterX();
  }

  /**
   * Centre le toast sur la zone blanche réelle : mesure de `.layout-content` (home),
   * pas une formule en vw + sidebar (souvent décalée avec margin-inline, transitions, etc.).
   */
  private scheduleSyncToastCenterX(): void {
    queueMicrotask(() => this.syncToastCenterXFromLayout());
    if (this.toastLayoutTimer !== null) {
      clearTimeout(this.toastLayoutTimer);
    }
    this.toastLayoutTimer = window.setTimeout(() => {
      this.toastLayoutTimer = null;
      this.syncToastCenterXFromLayout();
    }, 360);
  }

  private syncToastCenterXFromLayout(): void {
    const el = document.querySelector<HTMLElement>('.layout-wrapper .layout-content');
    if (!el) {
      document.documentElement.style.removeProperty(EmplacementComponent.TOAST_CENTER_X_VAR);
      return;
    }
    const r = el.getBoundingClientRect();
    const cx = r.left + r.width / 2;
    document.documentElement.style.setProperty(EmplacementComponent.TOAST_CENTER_X_VAR, `${cx}px`);
  }

  goEpiPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.currentPage()) {
      return;
    }
    this.refreshEpis(page);
  }

  refreshEpis(page = this.currentPage()): void {
    this.loading.set(true);
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(this.pageSize));
    params = addQuery(params, this.searchText());
    this.http.get<EpiListPage>(`${this.api}/epis`, { params }).subscribe({
      next: (res) => {
        this.epis.set(res?.content ?? []);
        this.totalRecords.set(res?.totalElements ?? 0);
        this.totalPages.set(res?.totalPages ?? 0);
        this.currentPage.set(res?.page ?? page);
        this.totalLinearCmAll.set(res?.totalLinearCm ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.toastError(err, 'emplacement.loadEpisError');
      },
    });
  }

  downloadEpiImportTemplate(): void {
    this.http
      .get(`${this.api}/epis/import-template`, {
        responseType: 'blob',
        observe: 'response',
      })
      .subscribe({
        next: async (res) => {
          const blob = res.body;
          if (!blob || blob.size < 100) {
            this.toastError(null, 'emplacement.templateEmptyError');
            return;
          }
          const type = res.headers.get('Content-Type') ?? blob.type;
          if (type.includes('json') || type.includes('text')) {
            const apiErr = await this.readApiErrorFromBlob(blob);
            this.toastError({ error: apiErr }, 'emplacement.templateDownloadError');
            return;
          }
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = 'modele-import-epis.xlsx';
          anchor.click();
          URL.revokeObjectURL(url);
        },
        error: (err) => this.toastError(err, 'emplacement.templateDownloadError'),
      });
  }

  private async readApiErrorFromBlob(blob: Blob): Promise<{ code?: string; message?: string }> {
    try {
      const text = await blob.text();
      const parsed = JSON.parse(text) as { code?: string; message?: string };
      return {
        code: typeof parsed?.code === 'string' ? parsed.code : undefined,
        message: typeof parsed?.message === 'string' ? parsed.message : text,
      };
    } catch {
      return {};
    }
  }

  triggerEpiImport(): void {
    this.epiImportInput?.nativeElement.click();
  }

  onEpiImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.importBusy()) {
      return;
    }
    const name = file.name.toLowerCase();
    if (!name.endsWith('.xlsx') && !name.endsWith('.xls')) {
      this.toastWarnMessage(this.i18n.t('emplacement.excelFileRequired'));
      return;
    }

    const form = new FormData();
    form.append('file', file, file.name);
    this.importBusy.set(true);
    this.http.post<EpiBulkImportResult>(`${this.api}/epis/import`, form).subscribe({
      next: (result) => {
        this.importBusy.set(false);
        this.showExcelImportDialog.set(false);
        const count = result?.createdCount ?? 0;
        const nums = result?.numeros ?? [];
        const list =
          nums.length <= 8
            ? nums.join(', ')
            : `${nums.slice(0, 8).join(', ')}… (+${nums.length - 8})`;
        this.toastSuccessMessage(
          count === 1
            ? this.i18n.t('emplacement.importSuccessOne')
            : this.i18n.t('emplacement.importSuccessMany', { count }),
          list ? this.i18n.t('emplacement.importNumerosDetail', { list }) : undefined,
          8000,
        );
        this.refreshEpis();
      },
      error: (err) => {
        this.importBusy.set(false);
        this.toastError(err, 'emplacement.importError');
      },
    });
  }

  openAddChoice(): void {
    this.showAddChoiceDialog.set(true);
  }

  chooseManualAdd(): void {
    this.showAddChoiceDialog.set(false);
    this.openCreate();
  }

  chooseExcelAdd(): void {
    this.showAddChoiceDialog.set(false);
    this.showExcelImportDialog.set(true);
  }

  openCreate(): void {
    this.newTravers = null;
    this.newTabletteRows = null;
    this.newBlocsPerTablette = 8;
    this.nextNumeroPreview.set(this.i18n.t('common.loading'));
    this.showCreateDialog.set(true);
    this.http.get<NextEpiNumeroDto>(`${this.api}/epis/next-numero`).subscribe({
      next: (r) => this.nextNumeroPreview.set(r.numero?.trim() ?? ''),
      error: (err) => {
        this.nextNumeroPreview.set('');
        this.toastError(err, 'emplacement.nextNumeroError');
      },
    });
  }

  openResizeFromMatrix(): void {
    const epi = this.currentEpi();
    if (!epi) {
      return;
    }
    this.openResize(epi, new Event('click'));
  }

  openResize(epi: EpiSummary, event: Event): void {
    event.stopPropagation();
    this.resizeTarget.set(epi);
    this.resizeTravers = epi.traversCount;
    this.resizeTabletteRows = epi.tabletteRows;
    this.resizeBlocsPerTablette = epi.blocsPerTablette;
    this.showResizeDialog.set(true);
  }

  submitResize(): void {
    const epi = this.resizeTarget();
    if (!epi || this.resizeBusy()) {
      return;
    }
    if (
      this.resizeTravers < 1 ||
      this.resizeTravers > this.maxDim ||
      this.resizeTabletteRows < 1 ||
      this.resizeTabletteRows > this.maxDim
    ) {
      this.toastWarnMessage(this.maxTraveesTablettesMessage());
      return;
    }
    if (this.resizeBlocsPerTablette < 1 || this.resizeBlocsPerTablette > this.maxBlocsTablette) {
      this.toastWarnMessage(
        this.i18n.t('emplacement.blocsPerTabletteRange', { max: this.maxBlocsTablette }),
      );
      return;
    }
    if (
      this.resizeTravers < epi.traversCount ||
      this.resizeTabletteRows < epi.tabletteRows ||
      this.resizeBlocsPerTablette < epi.blocsPerTablette
    ) {
      this.toastWarnMessage(this.i18n.t('emplacement.resizeExpansionOnly'));
      return;
    }
    const unchanged =
      this.resizeTravers === epi.traversCount &&
      this.resizeTabletteRows === epi.tabletteRows &&
      this.resizeBlocsPerTablette === epi.blocsPerTablette;
    if (unchanged) {
      this.showResizeDialog.set(false);
      this.toastWarnMessage(this.i18n.t('emplacement.resizeUnchanged'));
      return;
    }

    const body = {
      traversCount: Number(this.resizeTravers),
      tabletteRows: Number(this.resizeTabletteRows),
      blocsPerTablette: Number(this.resizeBlocsPerTablette),
    };

    this.resizeBusy.set(true);
    this.http
      .put<EpiSummary>(`${this.api}/epis/${encodeURIComponent(epi.id)}/resize`, body)
      .subscribe({
        next: (updated) => {
          this.resizeBusy.set(false);
          this.showResizeDialog.set(false);
          this.resizeTarget.set(null);
          this.toastSuccessMessage(this.i18n.t('emplacement.resizeSuccess', { numero: updated.numero }));
          if (this.view() === 'matrix' && this.currentEpi()?.id === epi.id) {
            this.loadMatrix(epi.id);
          } else {
            this.refreshEpis();
          }
        },
        error: (err) => {
          this.resizeBusy.set(false);
          this.toastError(err, 'emplacement.resizeError');
        },
      });
  }

  submitCreate(): void {
    const travers = this.parseEpiDimension(this.newTravers);
    const tablettes = this.parseEpiDimension(this.newTabletteRows);
    if (travers == null || tablettes == null) {
      this.toastWarnMessage(
        this.i18n.t('emplacement.createDimensionsRequired', { max: this.maxDim }),
      );
      return;
    }
    if (travers > this.maxDim || tablettes > this.maxDim) {
      this.toastWarnMessage(this.maxTraveesTablettesMessage());
      return;
    }
    if (this.newBlocsPerTablette < 1 || this.newBlocsPerTablette > this.maxBlocsTablette) {
      this.toastWarnMessage(
        this.i18n.t('emplacement.blocsPerTabletteRange', { max: this.maxBlocsTablette }),
      );
      return;
    }

    const body = {
      typeLabel: null as string | null,
      traversCount: travers,
      tabletteRows: tablettes,
      blocsPerTablette: Number(this.newBlocsPerTablette),
      blocLinearCm: this.defaultBlocCm,
    };

    this.http.post<EpiSummary>(`${this.api}/epis`, body).subscribe({
      next: (created) => {
        this.showCreateDialog.set(false);
        this.toastSuccessMessage(this.i18n.t('emplacement.createSuccess', { numero: created.numero }));
        this.refreshEpis();
      },
      error: (err) => this.toastError(err, 'emplacement.createError'),
    });
  }

  confirmDeleteLastEpi(): void {
    if (this.epis().length === 0) {
      return;
    }
    this.confirm.confirm({
      message: this.i18n.t('emplacement.deleteLastConfirmMessage'),
      header: this.i18n.t('emplacement.deleteLastConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('common.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => this.executeDeleteLastEpi(),
    });
  }

  private executeDeleteLastEpi(): void {
    this.http.delete<EpiSummary>(`${this.api}/epis/last`).subscribe({
      next: (removed) => {
        this.toastSuccessMessage(
          this.i18n.t('emplacement.deleteSuccessSummary'),
          this.i18n.t('emplacement.deleteSuccessDetail', { numero: removed.numero }),
        );
        this.refreshEpis();
      },
      error: (err) => this.toastError(err, 'emplacement.deleteError'),
    });
  }

  openEpi(epi: EpiSummary): void {
    this.clearSpotlight();
    this.clearSemanticSearch();
    this.matrixData.set(null);
    this.matrixViewMode.set('2d');
    this.view.set('matrix');
    this.loadMatrix(epi.id);
  }

  backToList(): void {
    this.clearSpotlight();
    this.clearSemanticSearch();
    this.view.set('list');
    this.matrixData.set(null);
    this.refreshEpis();
  }

  loadMatrix(epiId: string): void {
    this.clearSpotlight();
    this.loadingMatrix.set(true);
    this.http.get<EpiMatrixDto>(`${this.api}/epis/${encodeURIComponent(epiId)}/matrix`).subscribe({
      next: (data) => {
        this.matrixData.set(data);
        this.loadingMatrix.set(false);
      },
      error: (err) => {
        this.loadingMatrix.set(false);
        this.toastError(err, 'emplacement.loadMatrixError');
        this.view.set('list');
      },
    });
  }

  currentEpi(): EpiSummary | null {
    return this.matrixData()?.epi ?? null;
  }

  matrixRows(): number {
    return this.currentEpi()?.tabletteRows ?? 0;
  }

  matrixCols(): number {
    return this.currentEpi()?.traversCount ?? 0;
  }

  computedEpieLinearCm(): number {
    return this.currentEpi()?.totalLinearCm ?? 0;
  }

  traversLinearCm(): number {
    const e = this.currentEpi();
    if (!e) {
      return 0;
    }
    return e.tabletteRows * e.blocsPerTablette * e.blocLinearCm;
  }

  /** Code travée qui sera attribué à la prochaine colonne (aligné sur le calcul serveur). */
  private previewNextTraversCode(): string {
    const e = this.currentEpi();
    if (!e) {
      return this.emDash();
    }
    const base = (e.numero ?? '').trim();
    return `${base}${this.matrixCols() + 1}`;
  }

  /** Première tablette de la prochaine ligne (colonne 1 / travée 0 en base 0). */
  private previewFirstTabletteNewRowCode(): string {
    const e = this.currentEpi();
    if (!e) {
      return this.emDash();
    }
    const base = (e.numero ?? '').trim();
    const trav1 = 1;
    const line1 = this.matrixRows() + 1;
    return `${base}${trav1}${line1}`;
  }

  /** Première tablette de la ligne {@code line0based} (travée 0). */
  private previewFirstTabletteAtRow(line0based: number): string {
    const e = this.currentEpi();
    if (!e || line0based < 0) {
      return this.emDash();
    }
    const base = (e.numero ?? '').trim();
    return `${base}1${line0based + 1}`;
  }

  /** Code de la dernière travée (colonne la plus à droite). */
  private previewLastTraversCode(): string {
    const e = this.currentEpi();
    if (!e || this.matrixCols() < 1) {
      return this.emDash();
    }
    const base = (e.numero ?? '').trim();
    const lastTrav0 = this.matrixCols() - 1;
    return `${base}${lastTrav0 + 1}`;
  }

  tabletteLinearCm(cell: TabletteCellDto): number {
    const e = this.currentEpi();
    if (!e) {
      return 0;
    }
    return cell.totalCount * e.blocLinearCm;
  }

  addRow(): void {
    const id = this.currentEpi()?.id;
    if (!id || this.structureBusy()) {
      return;
    }
    if (this.matrixRows() >= this.maxDim) {
      this.toastWarnMessage(this.maxTraveesTablettesMessage());
      return;
    }
    const code = this.previewFirstTabletteNewRowCode();
    this.confirm.confirm({
      message: this.i18n.t('emplacement.addTabletteConfirmMessage', { code }),
      header: this.i18n.t('emplacement.addTabletteConfirmHeader'),
      icon: 'pi pi-plus-circle',
      acceptLabel: this.i18n.t('common.add'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'empl-confirm-accept-success',
      rejectButtonStyleClass: 'empl-confirm-reject-danger',
      accept: () =>
        this.patchStructure(`${this.api}/epis/${encodeURIComponent(id)}/rows`, 'POST', 'addRow'),
    });
  }

  removeLastRow(): void {
    const id = this.currentEpi()?.id;
    if (!id || this.structureBusy() || this.matrixRows() <= 1) {
      return;
    }
    const num = this.previewFirstTabletteAtRow(this.matrixRows() - 1);
    this.confirm.confirm({
      message: this.i18n.t('emplacement.removeLastTabletteConfirmMessage', { num }),
      header: this.i18n.t('emplacement.removeLastTabletteConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('common.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'empl-confirm-reject-success',
      accept: () =>
        this.patchStructure(`${this.api}/epis/${encodeURIComponent(id)}/rows/last`, 'DELETE', 'removeRow'),
    });
  }

  addColumn(): void {
    const id = this.currentEpi()?.id;
    if (!id || this.structureBusy()) {
      return;
    }
    if (this.matrixCols() >= this.maxDim) {
      this.toastWarnMessage(this.maxTraveesTablettesMessage());
      return;
    }
    const trav = this.previewNextTraversCode();
    this.confirm.confirm({
      message: this.i18n.t('emplacement.addTraverseConfirmMessage', { trav }),
      header: this.i18n.t('emplacement.addTraverseConfirmHeader'),
      icon: 'pi pi-plus-circle',
      acceptLabel: this.i18n.t('common.add'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'empl-confirm-accept-success',
      rejectButtonStyleClass: 'empl-confirm-reject-danger',
      accept: () =>
        this.patchStructure(`${this.api}/epis/${encodeURIComponent(id)}/columns`, 'POST', 'addColumn'),
    });
  }

  removeLastColumn(): void {
    const id = this.currentEpi()?.id;
    if (!id || this.structureBusy() || this.matrixCols() <= 1) {
      return;
    }
    const num = this.previewLastTraversCode();
    this.confirm.confirm({
      message: this.i18n.t('emplacement.removeLastTraverseConfirmMessage', { num }),
      header: this.i18n.t('emplacement.removeLastTraverseConfirmHeader'),
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: this.i18n.t('common.delete'),
      rejectLabel: this.i18n.t('common.cancel'),
      acceptButtonStyleClass: 'p-button-danger',
      rejectButtonStyleClass: 'empl-confirm-reject-success',
      accept: () =>
        this.patchStructure(`${this.api}/epis/${encodeURIComponent(id)}/columns/last`, 'DELETE', 'removeColumn'),
    });
  }

  private patchStructure(url: string, method: 'POST' | 'DELETE', op: StructurePatchOp): void {
    this.structureBusy.set(true);
    const req = method === 'POST' ? this.http.post<EpiMatrixDto>(url, {}) : this.http.delete<EpiMatrixDto>(url);
    req.subscribe({
      next: (data) => {
        this.resetSpotlightState();
        if (op === 'addRow' && method === 'POST') {
          this.spotlightRow.set(Math.max(0, data.epi.tabletteRows - 1));
          this.scheduleSpotlightClear();
        } else if (op === 'addColumn' && method === 'POST') {
          this.spotlightCol.set(Math.max(0, data.epi.traversCount - 1));
          this.scheduleSpotlightClear();
        }
        this.matrixData.set(data);
        this.structureBusy.set(false);
        this.refreshEpis();
        this.toastStructureSuccess(op, data);
      },
      error: (err) => {
        this.structureBusy.set(false);
        this.toastError(err, 'emplacement.structureModifyError');
      },
    });
  }

  private toastStructureSuccess(op: StructurePatchOp, data: EpiMatrixDto): void {
    const e = data.epi;
    const life = 5500;
    switch (op) {
      case 'addRow':
        this.toastSuccessMessage(this.i18n.t('emplacement.structureAddRowSuccess'), life);
        break;
      case 'addColumn':
        this.toastSuccessMessage(this.i18n.t('emplacement.structureAddColumnSuccess'), life);
        break;
      case 'removeRow':
        this.toastSuccessMessage(
          this.i18n.t('emplacement.structureRemoveRowSuccess', { count: e.tabletteRows }),
          life,
        );
        break;
      case 'removeColumn':
        this.toastSuccessMessage(
          this.i18n.t('emplacement.structureRemoveColumnSuccess', { count: e.traversCount }),
          life,
        );
        break;
    }
  }

  openTablette(cell: TabletteCellDto): void {
    this.selectedCell.set(cell);
    this.showTabletteDialog.set(true);
  }

  runSemanticSearch(): void {
    const query = this.semanticSearchText?.trim();
    if (!query) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('emplacement.semanticSearchTitle'),
        detail: this.i18n.t('emplacement.semanticSearchInputRequired'),
        life: 7000,
      });
      return;
    }
    const epiNumero = this.matrixData()?.epi?.numero ?? null;
    this.semanticSearching.set(true);
    this.http
      .post<{
        query: string;
        results: SemanticSearchHit[];
        highlightBlocIds: string[];
      }>(`${environment.apiUrl}/api/boites/recherche-semantique`, {
        query,
        epiNumero,
        limit: 15,
      })
      .subscribe({
        next: (res) => {
          this.semanticSearching.set(false);
          this.semanticResults.set(res?.results ?? []);
          this.semanticHighlightBlocIds.set(res?.highlightBlocIds ?? []);
          if (!res?.results?.length) {
            this.messages.add({
              severity: 'info',
              summary: this.i18n.t('emplacement.semanticSearchTitle'),
              detail: this.i18n.t('emplacement.semanticSearchNoResults'),
              life: 8000,
            });
          }
        },
        error: (err) => {
          this.semanticSearching.set(false);
          this.toastError(err, 'emplacement.semanticSearchError');
        },
      });
  }

  clearSemanticSearch(): void {
    this.semanticSearchText = '';
    this.semanticResults.set([]);
    this.semanticHighlightBlocIds.set([]);
  }

  semanticScorePercent(score: number): number {
    return Math.round((score ?? 0) * 100);
  }

  focusSemanticHit(hit: SemanticSearchHit): void {
    if (!hit.onRequestedEpi || !hit.blocIds?.length) {
      return;
    }
    this.semanticHighlightBlocIds.set([...hit.blocIds]);
  }

  closeTablette(): void {
    this.closeBoiteDetail();
    this.showTabletteDialog.set(false);
    this.selectedCell.set(null);
  }

  openBoiteDetail(bloc: BlocDto): void {
    if (!this.blocOccupied(bloc) || !bloc.boite) {
      return;
    }
    this.selectedBloc.set(bloc);
    this.bordereauDetailStats.set(null);
    this.loadBordereauDetailStats(bloc.boite);
    this.showBoiteDetailDialog.set(true);
  }

  private loadBordereauDetailStats(boite: BlocBoiteSummary): void {
    const numero = this.bordereauNumeroDisplay(boite);
    const bordereauId = boite.bordereauId;
    if (bordereauId == null || !Number.isFinite(bordereauId)) {
      const total = boite.bordereauBoiteCount;
      if (typeof total === 'number' && total > 0) {
        this.bordereauDetailStats.set({ numero, total: Math.floor(total) });
      }
      return;
    }
    this.bordereauStatsLoading.set(true);
    this.http
      .get<BordereauDetailForBoite>(`${environment.apiUrl}/api/bordereaux/${bordereauId}`)
      .subscribe({
        next: (detail) => {
          const total =
            typeof detail.nombreBoites === 'number'
              ? Math.floor(detail.nombreBoites)
              : Math.floor(boite.bordereauBoiteCount ?? 0);
          this.bordereauDetailStats.set({
            numero: detail.numeroBordereau?.trim() || numero,
            total,
          });
          this.bordereauStatsLoading.set(false);
        },
        error: () => {
          this.bordereauStatsLoading.set(false);
          const total = boite.bordereauBoiteCount;
          if (typeof total === 'number' && total > 0) {
            this.bordereauDetailStats.set({
              numero,
              total: Math.floor(total),
            });
          }
        },
      });
  }

  /** N° affiché du bordereau (matrice API). « — » si absent (rebuild / redémarrage backend requis). */
  bordereauNumeroDisplay(boite: BlocBoiteSummary): string {
    const raw = boite.bordereauNumeroAffiche;
    const s = typeof raw === 'string' ? raw.trim() : '';
    return s.length > 0 ? s : this.emDash();
  }

  /** Mots-clés affichés en pastilles (séparateurs , ; ou retour ligne). */
  motsClesTags(motsCles: string | null | undefined): string[] {
    if (motsCles == null || !motsCles.trim()) {
      return [];
    }
    return motsCles
      .split(/[,;\n]+/)
      .map((s) => s.trim())
      .filter((s) => s.length > 0);
  }

  closeBoiteDetail(): void {
    this.showBoiteDetailDialog.set(false);
    this.selectedBloc.set(null);
    this.bordereauDetailStats.set(null);
    this.bordereauStatsLoading.set(false);
  }

  onBoiteDetailVisibleChange(visible: boolean): void {
    this.showBoiteDetailDialog.set(visible);
    if (!visible) {
      this.selectedBloc.set(null);
    }
  }

  blocOccupied(bloc: BlocDto): boolean {
    return bloc.boiteId != null;
  }

  private static tablettePrefix(numero: string): string {
    return numero.length >= 2 ? numero.slice(0, -1) : numero;
  }

  private static slotIndexFromNumero(numero: string): number {
    return numero.charCodeAt(numero.length - 1) - '1'.charCodeAt(0);
  }

  private static areConsecutiveBlocsOnTablette(blocs: BlocDto[]): boolean {
    if (blocs.length === 0) {
      return true;
    }
    const sorted = [...blocs].sort((a, b) => a.numero.localeCompare(b.numero));
    const prefix = EmplacementComponent.tablettePrefix(sorted[0].numero);
    const startSlot = EmplacementComponent.slotIndexFromNumero(sorted[0].numero);
    for (let i = 0; i < sorted.length; i++) {
      const num = sorted[i].numero;
      if (EmplacementComponent.tablettePrefix(num) !== prefix) {
        return false;
      }
      if (EmplacementComponent.slotIndexFromNumero(num) !== startSlot + i) {
        return false;
      }
    }
    return true;
  }

  /** Regroupe les blocs consécutifs occupés par la même boîte en une seule carte fusionnée. */
  blocStripSegments(blocs: BlocDto[]): BlocStripSegment[] {
    const segments: BlocStripSegment[] = [];
    let i = 0;
    while (i < blocs.length) {
      const current = blocs[i];
      if (!this.blocOccupied(current)) {
        segments.push({ kind: 'empty', bloc: current });
        i++;
        continue;
      }
      const group: BlocDto[] = [current];
      let j = i + 1;
      while (j < blocs.length) {
        const next = blocs[j];
        if (
          next.boiteId == null ||
          next.boiteId !== current.boiteId ||
          next.positionIndex !== blocs[j - 1].positionIndex + 1
        ) {
          break;
        }
        group.push(next);
        j++;
      }
      segments.push({ kind: 'occupied', blocs: group, fused: group.length > 1 });
      i = j;
    }
    return segments;
  }

  fusedBlocsNumeros(blocs: BlocDto[]): string {
    return blocs.map((b) => b.numero).join(' · ');
  }

  /**
   * Libellé des blocs : liste explicite si non contigus (ex. 01117 · 01118 · 01124),
   * sinon plage début → fin pour un segment unique.
   */
  fusedBlocsRangeLabel(blocs: BlocDto[]): string {
    if (blocs.length <= 1) {
      return blocs[0]?.numero ?? '';
    }
    const sorted = [...blocs].sort((a, b) => a.numero.localeCompare(b.numero));
    if (!EmplacementComponent.areConsecutiveBlocsOnTablette(sorted)) {
      return this.fusedBlocsNumeros(sorted);
    }
    return `${sorted[0].numero} → ${sorted[sorted.length - 1].numero}`;
  }

  /** Nombre réel de boîtes du bordereau (détail API prioritaire sur le compteur matrice). */
  bordereauTotalBoites(boite: BlocBoiteSummary): number {
    const fromDetail = this.bordereauDetailStats()?.total;
    if (typeof fromDetail === 'number') {
      return fromDetail;
    }
    if (typeof boite.bordereauBoiteCount === 'number') {
      return boite.bordereauBoiteCount;
    }
    return 0;
  }

  isBordereauBoitesNonContigues(boite: BlocBoiteSummary): boolean {
    if (boite.bordereauBoitesNonContigues === true) {
      return true;
    }
    if (this.bordereauTotalBoites(boite) < 2) {
      return false;
    }
    return this.computeBordereauBoitesNonContiguesFromMatrix(boite.bordereauId ?? null);
  }

  /** Boîte occupant au moins deux plages non consécutives sur cette tablette (ex. fragmentation 01115 + 01117). */
  isBoiteNonRegroupeeSurTablette(boite: BlocBoiteSummary, cell: TabletteCellDto): boolean {
    const blocs = cell.blocs.filter((b) => b.boite?.id === boite.id);
    if (blocs.length <= 1) {
      return false;
    }
    return !EmplacementComponent.areConsecutiveBlocsOnTablette(blocs);
  }

  /** Style cyan « Fragmenté » : bordereau éclaté sur l’épi ou boîte en plusieurs plages sur la tablette. */
  shouldShowDispersBlocStyle(boite: BlocBoiteSummary, cell: TabletteCellDto): boolean {
    return this.isBordereauBoitesNonContigues(boite) || this.isBoiteNonRegroupeeSurTablette(boite, cell);
  }

  private boitePlageCountOnTablette(boite: BlocBoiteSummary, cell: TabletteCellDto): number {
    const sorted = cell.blocs
      .filter((b) => b.boite?.id === boite.id)
      .sort((a, b) => a.positionIndex - b.positionIndex);
    if (sorted.length <= 1) {
      return 1;
    }
    let runs = 1;
    for (let i = 1; i < sorted.length; i++) {
      if (sorted[i].positionIndex !== sorted[i - 1].positionIndex + 1) {
        runs++;
      }
    }
    return runs;
  }

  occupiedStateLabel(boite: BlocBoiteSummary, fusedBlocCount?: number, cell?: TabletteCellDto): string {
    if (cell && this.isBoiteNonRegroupeeSurTablette(boite, cell) && !this.isBordereauBoitesNonContigues(boite)) {
      const plages = this.boitePlageCountOnTablette(boite, cell);
      if (fusedBlocCount && fusedBlocCount > 1) {
        return this.i18n.t('emplacement.stateFragmentedBlocs', { count: fusedBlocCount });
      }
      return plages > 1
        ? this.i18n.t('emplacement.stateFragmentedPlages', { count: plages })
        : this.i18n.t('emplacement.stateFragmentedOnTablette');
    }
    if (this.isBordereauBoitesNonContigues(boite)) {
      return fusedBlocCount && fusedBlocCount > 1
        ? this.i18n.t('emplacement.stateFragmentedBlocs', { count: fusedBlocCount })
        : this.i18n.t('emplacement.stateBordereauFragmented');
    }
    if (fusedBlocCount && fusedBlocCount > 1) {
      return this.i18n.t('emplacement.stateSameBoxBlocs', { count: fusedBlocCount });
    }
    return this.i18n.t('emplacement.stateOccupied');
  }

  bordereauNonContiguesDetailLabel(boite: BlocBoiteSummary): string {
    const numero = this.bordereauNumeroDisplay(boite);
    const n = this.bordereauTotalBoites(boite);
    return this.i18n.t('emplacement.bordereauNonContiguesDetail', { count: n, numero });
  }

  boiteNonRegroupeeDetailLabel(boite: BlocBoiteSummary): string {
    const blocs = this.blocsForBoiteDetail();
    const nums = blocs.map((b) => b.numero).join(', ');
    return this.i18n.t('emplacement.boiteNonRegroupeeDetail', { nums });
  }

  isBoiteNonRegroupeeInDetail(boite: BlocBoiteSummary): boolean {
    const blocs = this.blocsForBoiteDetail();
    if (blocs.length <= 1) {
      return false;
    }
    return !EmplacementComponent.areConsecutiveBlocsOnTablette(blocs);
  }

  private computeBordereauBoitesNonContiguesFromMatrix(bordereauId: number | null): boolean {
    if (bordereauId == null) {
      return false;
    }
    const data = this.matrixData();
    if (!data?.rows?.length) {
      return false;
    }
    const sorted: BlocDto[] = [];
    for (const row of data.rows) {
      for (const cell of row) {
        for (const b of cell.blocs) {
          sorted.push(b);
        }
      }
    }
    sorted.sort((a, b) => a.numero.localeCompare(b.numero));
    let runs = 0;
    let inRun = false;
    for (const bloc of sorted) {
      const brId = bloc.boite?.bordereauId ?? null;
      const belongs = brId === bordereauId;
      if (belongs) {
        if (!inRun) {
          runs++;
          inRun = true;
        }
      } else {
        inRun = false;
      }
    }
    return runs > 1;
  }

  /** Tous les blocs de l’épi occupés par la boîte. */
  blocsForBoiteDetail(): BlocDto[] {
    const bloc = this.selectedBloc();
    if (!bloc) {
      return [];
    }
    if (bloc.boiteId == null) {
      return [bloc];
    }
    const data = this.matrixData();
    if (!data?.rows?.length) {
      return [bloc];
    }
    const group: BlocDto[] = [];
    for (const row of data.rows) {
      for (const cell of row) {
        for (const b of cell.blocs) {
          if (b.boiteId === bloc.boiteId) {
            group.push(b);
          }
        }
      }
    }
    if (group.length === 0) {
      return [bloc];
    }
    return [...group].sort((a, b) => a.numero.localeCompare(b.numero));
  }

  /** Pastille de la grille : nouvelle ligne ou nouvelle colonne (ajout structure). */
  cellSpotlighted(cell: TabletteCellDto): boolean {
    const sr = this.spotlightRow();
    const sc = this.spotlightCol();
    return (sr !== null && cell.rowIndex === sr) || (sc !== null && cell.colIndex === sc);
  }

  traversHeadSpotlighted(colIndex: number): boolean {
    const sc = this.spotlightCol();
    return sc !== null && colIndex === sc;
  }

  private scheduleSpotlightClear(): void {
    if (this.spotlightClearTimer !== null) {
      clearTimeout(this.spotlightClearTimer);
    }
    this.spotlightClearTimer = setTimeout(() => {
      this.spotlightClearTimer = null;
      this.spotlightRow.set(null);
      this.spotlightCol.set(null);
    }, 3000);
  }

  private resetSpotlightState(): void {
    if (this.spotlightClearTimer !== null) {
      clearTimeout(this.spotlightClearTimer);
      this.spotlightClearTimer = null;
    }
    this.spotlightRow.set(null);
    this.spotlightCol.set(null);
  }

  private clearSpotlight(): void {
    this.resetSpotlightState();
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.alignToastToLayoutContent();
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 12000,
    });
    this.alignToastToLayoutContent();
  }

  private parseEpiDimension(value: number | null | undefined): number | null {
    if (value == null) {
      return null;
    }
    const n = Number(value);
    if (!Number.isFinite(n) || n < 1) {
      return null;
    }
    return Math.floor(n);
  }

  /** Limite travées / tablettes par épi (message utilisateur). */
  private maxTraveesTablettesMessage(): string {
    return this.i18n.t('emplacement.maxTraversTablettes', { max: this.maxDim });
  }

  private toastWarnMessage(message: string, life = 7000): void {
    this.alignToastToLayoutContent();
    this.messages.add({ severity: 'warn', summary: message, life });
    this.alignToastToLayoutContent();
  }

  /**
   * Toast succès : `toastSuccessMessage(résumé)` ou `toastSuccessMessage(résumé, durée)`,
   * ou `toastSuccessMessage(résumé, détail)` / `toastSuccessMessage(résumé, détail, durée)`.
   */
  private toastSuccessMessage(summary: string, detailOrLife?: string | number, life?: number): void {
    let detail: string | undefined;
    let resolvedLife = 5500;
    if (typeof detailOrLife === 'number') {
      resolvedLife = detailOrLife;
    } else if (typeof detailOrLife === 'string') {
      detail = detailOrLife;
      resolvedLife = life ?? 5500;
    }
    this.alignToastToLayoutContent();
    this.messages.add({
      severity: 'success',
      summary,
      ...(detail ? { detail } : {}),
      life: resolvedLife,
    });
    this.alignToastToLayoutContent();
  }

  /** Recalcul --emplacement-toast-center-x avant/après rendu du toast (portail body). */
  private alignToastToLayoutContent(): void {
    this.syncToastCenterXFromLayout();
    queueMicrotask(() => this.syncToastCenterXFromLayout());
    requestAnimationFrame(() => this.syncToastCenterXFromLayout());
  }
}
