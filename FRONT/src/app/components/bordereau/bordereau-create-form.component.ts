import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { catchError, finalize, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';
import { addQuery } from '../../core/http/http-query';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { BordereauPendingBadgeComponent } from './bordereau-pending-badge.component';

export interface DirectionOption {
  id: string;
  label: string;
}

export interface DocumentTypeOption {
  id: number;
  title: string;
  directionId: string | null;
  directionLabel: string | null;
}

interface BordereauFormOptionsDto {
  directions: DirectionOption[];
  documentTypes: DocumentTypeOption[];
  prochainNumeroAffiche?: string | null;
  userDirectionId?: string | null;
  userDirectionLabel?: string | null;
  directionLocked?: boolean;
}

export type RegleStatus = 'idle' | 'loading' | 'ok' | 'error';

export interface RegleConservationValidePreview {
  reference: string;
  finalDecision: string;
  activeUnknown: boolean;
  activeYears: number | null;
  semiActiveUnknown: boolean;
  semiActiveYears: number | null;
}

export interface BlocEmplacementSuggestion {
  id: string;
  numero: string;
}

export interface BoiteBlocsSuggestion {
  metrageCm: number;
  blocsRequis: number;
  blocs: BlocEmplacementSuggestion[];
  /** Proposition en deux plages sur la même tablette (avec blocs occupés entre les deux). */
  splitRecommendation?: boolean;
  /** Proposition répartie sur deux tablettes (ou plus segments ordonnées). */
  multiTabletteRecommendation?: boolean;
  /** Index de coupe du premier segment (split ou multi). */
  premierePlageBlocs?: number | null;
}

interface BordereauDetailResponse {
  id: number;
  numeroBordereau: string;
}

interface BordereauResumeBoite {
  id: number;
  titre: string;
  anneeMin: number;
  anneeMax: number;
  metrageCm: number;
  contenu: string | null;
  motsCles: string | null;
  documentTypeId: number;
  documentTypeTitle: string;
}

interface BordereauResumeDetail {
  id: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionId: string | null;
  observation: string | null;
  statut: string;
  regleDureeActiveInconnue?: boolean;
  reglesEnAlerteResume?: string | null;
  boites: BordereauResumeBoite[];
}

export interface BordereauBoiteDraft {
  tempId: string;
  /** ID de la boîte persistée (présent uniquement en mode reprise). */
  boiteId: number | null;
  titre: string;
  anneeMin: number | null;
  anneeMax: number | null;
  metrageCm: number | null;
  contenu: string;
  motsCles: string;
  documentType: DocumentTypeOption | null;
  regleStatus: RegleStatus;
  reglePreview: RegleConservationValidePreview | null;
  /** Renseigné si la règle liée a une durée active inconnue. */
  renseignerAnneesActives: number | null;
  /** Validé explicitement par l'utilisateur (obligatoire si durée active inconnue). */
  anneesActivesConfirmees: boolean;
}

/** Formulaire de création : page « Nouveau bordereau » (et réutilisable ailleurs si besoin). */
@Component({
  selector: 'app-bordereau-create-form',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    DialogModule,
    RippleModule,
    InputTextModule,
    InputNumberModule,
    SelectModule,
    DatePickerModule,
    TextareaModule,
    BordereauPendingBadgeComponent,
    TranslocoPipe,
  ],
  templateUrl: './bordereau-create-form.component.html',
  styleUrl: './bordereau-create-form.component.scss',
})
export class BordereauCreateFormComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  /** `true` = alerte (statut en attente), depuis la page Alertes. */
  readonly mettreEnAttente = input.required<boolean>();
  /** Reprise d’un bordereau en attente existant. */
  readonly bordereauId = input<number | null>(null);

  readonly cancel = output<void>();
  readonly saved = output<void>();

  private readonly resumeIdFromRoute = signal<number | null>(null);

  readonly effectiveResumeId = computed(() => {
    const fromParent = this.bordereauId();
    if (fromParent != null && fromParent > 0) {
      return fromParent;
    }
    return this.resumeIdFromRoute();
  });

  readonly isResume = computed(() => {
    const id = this.effectiveResumeId();
    return id != null && id > 0;
  });

  private readonly api = `${environment.apiUrl}/api/bordereaux`;
  private readonly documentTypesApi = `${environment.apiUrl}/api/document-types`;
  private nextBoxKey = 1;

  readonly directionOptions = signal<DirectionOption[]>([]);
  readonly documentTypeOptions = signal<DocumentTypeOption[]>([]);
  readonly submitting = signal(false);

  /** Agent : pas d’affectation d’emplacements (réservée à l’administrateur). */
  readonly canAssignEmplacements = computed(() => this.auth.isAdmin());

  readonly agentWorkflow = computed(() => this.auth.isAgent());

  readonly enregistrerEnAttenteLabel = (): string => {
    if (this.isResume()) {
      return this.i18n.t('bordereau.saveModifications');
    }
    if (this.agentWorkflow()) {
      return this.i18n.t('bordereau.savePendingValidation');
    }
    return this.mettreEnAttente() ? this.i18n.t('bordereau.savePending') : this.i18n.t('bordereau.putPending');
  };

  readonly assignerButtonLabel = (): string =>
    this.isResume() ? this.i18n.t('bordereau.validateAssignment') : this.i18n.t('bordereau.assignLocations');

  /** Libellé du champ numéro selon le mode (brouillon vs affectation directe). */
  numeroFieldLabel(): string {
    if (this.isResume()) {
      return this.i18n.t('bordereau.numeroReferencePending');
    }
    if (this.agentWorkflow() || this.mettreEnAttente()) {
      return this.i18n.t('bordereau.numeroNextOfficial');
    }
    return this.i18n.t('bordereau.numeroLabel');
  }

  numeroReadonlyDisplay(): string {
    if (this.numeroPreviewLoading()) {
      return this.i18n.t('common.loading');
    }
    const v = this.numeroPreview().trim();
    return v.length > 0 ? v : this.emDash();
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  directionId: string | null = null;
  readonly directionLocked = signal(false);
  readonly directionReadonlyLabel = signal('');
  dateTransfert: Date = new Date();
  observation = '';

  readonly boxes = signal<BordereauBoiteDraft[]>([]);
  readonly numeroPreview = signal<string>('');
  readonly numeroPreviewLoading = signal(false);

  readonly affectDialogVisible = signal(false);
  /** Dialogue dédié quand au moins une boîte requiert des blocs sur plusieurs tablettes. */
  readonly crossTabletteDialogVisible = signal(false);
  readonly affectSuggestionsLoading = signal(false);
  readonly affectSuggestions = signal<BoiteBlocsSuggestion[]>([]);
  /** Par index de boîte : l’utilisateur a confirmé une proposition « deux segments ». */
  readonly resumeLoading = signal(false);
  readonly validatingAffectation = signal(false);

  /**
   * Le dialogue d'affectation est ouvert en contexte de validation finale
   * (reprise d'un bordereau en attente → passage à AFFECTE).
   */
  readonly validatingMode = signal(false);

  /** Mode d'affectation manuel : l'utilisateur choisit lui-même les blocs libres. */
  readonly manualMode = signal(false);
  /** Tous les blocs libres de l'épi-cellier, chargés à l'ouverture du mode manuel. */
  readonly freeBlocsAvailable = signal<BlocEmplacementSuggestion[]>([]);
  readonly freeBlocsLoading = signal(false);
  readonly freeBlocsTotal = signal(0);
  readonly freeBlocsSearch = signal('');
  /** Index de boîte → liste de blocs IDs sélectionnés manuellement. */
  readonly manualSelections = signal<Record<number, string[]>>({});
  /** Index de boîte actuellement « actif » pour cliquer un bloc libre. */
  readonly manualActiveBoxIndex = signal<number | null>(null);
  /** Bloque la validation d’affectation (règle avec durée active inconnue non renseignée). */
  readonly regleDureeActiveInconnue = signal(false);
  readonly reglesEnAlerteResume = signal<string | null>(null);

  readonly hasRegleActiveUnknownPending = computed(() =>
    this.boxes().some((b) => this.isActiveYearsLinkPending(b)),
  );

  readonly activeYearsConfirmVisible = signal(false);
  readonly pendingActiveYearsBox = signal<BordereauBoiteDraft | null>(null);

  /** Durée semi-active inconnue (Détruire / Transférer) : le bordereau peut passer, alerte boîtes ensuite. */
  readonly hasRegleSemiActiveUnknown = computed(() =>
    this.boxes().some(
      (b) =>
        b.reglePreview?.semiActiveUnknown === true &&
        this.isSemiActifDecision(b.reglePreview.finalDecision),
    ),
  );

  readonly reglesSemiActifEnAlerteResume = computed(() => {
    const refs = new Set<string>();
    for (const b of this.boxes()) {
      const p = b.reglePreview;
      if (p?.semiActiveUnknown && this.isSemiActifDecision(p.finalDecision) && p.reference?.trim()) {
        refs.add(p.reference.trim());
      }
    }
    return refs.size > 0 ? [...refs].join(', ') : null;
  });

  private static readonly DEFAULT_METRAGE_CM = 10;

  readonly metrageOptions = signal<{ label: string; value: number }[]>([]);

  ngOnInit(): void {
    this.rebuildMetrageOptions();
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.rebuildMetrageOptions();
      if (!this.directionReadonlyLabel()) {
        this.directionReadonlyLabel.set(this.emDash());
      }
    });
    const routeId = this.parseResumeIdFromRoute();
    if (routeId != null) {
      this.resumeIdFromRoute.set(routeId);
    }
    if (this.isResume()) {
      this.loadFormOptionsForResume();
    } else {
      this.loadFormOptions();
    }
  }

  private rebuildMetrageOptions(): void {
    this.metrageOptions.set(
      [10, 20, 30, 40].map((n) => ({
        label: this.i18n.t('bordereau.metrageCm', { cm: n }),
        value: n,
      })),
    );
  }

  private parseResumeIdFromRoute(): number | null {
    let r: ActivatedRoute | null = this.route;
    while (r) {
      const path = r.snapshot.routeConfig?.path ?? '';
      if (path.startsWith('reprendre')) {
        const raw = r.snapshot.paramMap.get('id');
        const id = raw?.trim() ? Number(raw) : NaN;
        if (Number.isFinite(id) && id > 0) {
          return id;
        }
      }
      r = r.parent;
    }
    return null;
  }

  private yearFromDateTransfert(): number {
    const d = this.dateTransfert;
    if (!d || Number.isNaN(new Date(d).getTime())) {
      return new Date().getFullYear();
    }
    return new Date(d).getFullYear();
  }

  private loadFormOptions(): void {
    const annee = this.yearFromDateTransfert();
    const params = new HttpParams().set('anneeProchainNumero', String(annee));
    this.numeroPreviewLoading.set(true);
    this.http
      .get<BordereauFormOptionsDto>(`${this.api}/form-options`, { params })
      .pipe(finalize(() => this.numeroPreviewLoading.set(false)))
      .subscribe({
        next: (dto) => {
          this.applyNumeroPreviewFromFormOptions(dto);
          this.applyUserDirectionFromFormOptions(dto);
          const types = (dto.documentTypes ?? []).map((t) => ({
            ...t,
            id: typeof t.id === 'string' ? Number(t.id) : t.id,
          }));
          this.documentTypeOptions.set(types);
          this.http
            .get<DirectionOption[]>(`${this.documentTypesApi}/direction-options`)
            .pipe(catchError(() => of(dto.directions ?? [])))
            .subscribe((dirs) => this.directionOptions.set(dirs ?? []));
        },
        error: (err) => {
          this.directionOptions.set([]);
          this.documentTypeOptions.set([]);
          this.numeroPreview.set('');
          this.toastError(err, 'bordereau.loadFormError');
        },
      });
  }

  private applyUserDirectionFromFormOptions(dto: BordereauFormOptionsDto): void {
    const locked = dto.directionLocked === true;
    this.directionLocked.set(locked);
    if (!locked) {
      return;
    }
    const id = dto.userDirectionId?.trim() || null;
    this.directionId = id;
    const label = dto.userDirectionLabel?.trim();
    this.directionReadonlyLabel.set(label && label.length > 0 ? label : id ?? this.emDash());
  }

  private applyNumeroPreviewFromFormOptions(dto: BordereauFormOptionsDto): void {
    const camel = dto.prochainNumeroAffiche;
    if (typeof camel === 'string' && camel.trim().length > 0) {
      this.numeroPreview.set(camel.trim());
      return;
    }
    const snake = (dto as unknown as Record<string, unknown>)['prochain_numero_affiche'];
    if (typeof snake === 'string' && snake.trim().length > 0) {
      this.numeroPreview.set(snake.trim());
      return;
    }
    this.numeroPreview.set('');
  }

  ajouterBoite(): void {
    const tempId = `b-${this.nextBoxKey++}`;
    this.boxes.update((list) => [
      ...list,
      {
        tempId,
        boiteId: null,
        titre: '',
        anneeMin: null,
        anneeMax: null,
        metrageCm: BordereauCreateFormComponent.DEFAULT_METRAGE_CM,
        contenu: '',
        motsCles: '',
        documentType: null,
        regleStatus: 'idle',
        reglePreview: null,
        renseignerAnneesActives: null,
        anneesActivesConfirmees: false,
      },
    ]);
  }

  retirerBoite(tempId: string): void {
    this.boxes.update((list) => list.filter((b) => b.tempId !== tempId));
  }

  onDateTransfertChange(): void {
    if (this.isResume()) {
      return;
    }
    this.loadFormOptions();
  }

  private loadFormOptionsForResume(): void {
    const annee = this.yearFromDateTransfert();
    const params = new HttpParams().set('anneeProchainNumero', String(annee));
    this.numeroPreviewLoading.set(true);
    this.resumeLoading.set(true);
    this.http
      .get<BordereauFormOptionsDto>(`${this.api}/form-options`, { params })
      .pipe(finalize(() => this.numeroPreviewLoading.set(false)))
      .subscribe({
        next: (dto) => {
          this.applyUserDirectionFromFormOptions(dto);
          const types = (dto.documentTypes ?? []).map((t) => ({
            ...t,
            id: typeof t.id === 'string' ? Number(t.id) : t.id,
          }));
          this.documentTypeOptions.set(types);
          this.http
            .get<DirectionOption[]>(`${this.documentTypesApi}/direction-options`)
            .pipe(catchError(() => of(dto.directions ?? [])))
            .subscribe((dirs) => {
              this.directionOptions.set(dirs ?? []);
              const id = this.effectiveResumeId();
              if (id != null && id > 0) {
                this.loadBordereauForResume(id);
              }
            });
        },
        error: (err) => {
          this.resumeLoading.set(false);
          this.directionOptions.set([]);
          this.documentTypeOptions.set([]);
          this.toastError(err, 'bordereau.loadFormResumeError');
          this.cancel.emit();
        },
      });
  }

  private loadBordereauForResume(id: number): void {
    this.http.get<BordereauResumeDetail>(`${this.api}/${id}`).subscribe({
      next: (d) => {
        this.resumeLoading.set(false);
        if (d.statut !== 'EN_ATTENTE') {
          this.toastError(
            { error: { message: this.i18n.t('bordereau.notPendingAnymore') } },
            this.i18n.t('bordereau.resumeImpossible'),
          );
          this.cancel.emit();
          return;
        }
        this.numeroPreview.set(d.numeroBordereau);
        this.regleDureeActiveInconnue.set(!!d.regleDureeActiveInconnue);
        this.reglesEnAlerteResume.set(d.reglesEnAlerteResume ?? null);
        if (!this.directionLocked()) {
          this.directionId = d.directionId;
        }
        this.dateTransfert = new Date(`${d.dateTransfert}T12:00:00`);
        this.observation = d.observation ?? '';

        const types = this.documentTypeOptions();
        const drafts: BordereauBoiteDraft[] = (d.boites ?? []).map((b) => {
          const dt =
            types.find((t) => t.id === b.documentTypeId) ??
            ({
              id: b.documentTypeId,
              title: b.documentTypeTitle,
              directionId: null,
              directionLabel: null,
            } satisfies DocumentTypeOption);
          return {
            tempId: `b-${this.nextBoxKey++}`,
            boiteId: b.id,
            titre: b.titre,
            anneeMin: b.anneeMin,
            anneeMax: b.anneeMax,
            metrageCm: b.metrageCm ?? BordereauCreateFormComponent.DEFAULT_METRAGE_CM,
            contenu: b.contenu ?? '',
            motsCles: b.motsCles ?? '',
            documentType: dt,
            regleStatus: 'loading' as RegleStatus,
            reglePreview: null,
            renseignerAnneesActives: null,
            anneesActivesConfirmees: false,
          };
        });
        this.boxes.set(drafts);
        this.affectSuggestions.set([]);
        for (const box of drafts) {
          if (box.documentType) {
            this.onBoiteDocumentTypeChange(box.tempId, box.documentType);
          }
        }
      },
      error: (err) => {
        this.resumeLoading.set(false);
        this.toastError(err, 'bordereau.loadResumeError');
        this.cancel.emit();
      },
    });
  }

  onBoiteDocumentTypeChange(tempId: string, dt: DocumentTypeOption | null): void {
    if (!dt) {
      this.boxes.update((list) =>
        list.map((x) =>
          x.tempId === tempId
            ? {
                ...x,
                documentType: null,
                regleStatus: 'idle',
                reglePreview: null,
                renseignerAnneesActives: null,
                anneesActivesConfirmees: false,
              }
            : x,
        ),
      );
      return;
    }
    const typeId = dt.id;
    this.boxes.update((list) =>
      list.map((x) =>
        x.tempId === tempId
          ? {
              ...x,
              documentType: dt,
              regleStatus: 'loading',
              reglePreview: null,
              renseignerAnneesActives: null,
              anneesActivesConfirmees: false,
            }
          : x,
      ),
    );
    this.http.get<RegleConservationValidePreview>(`${this.api}/document-types/${typeId}/regle-conservation-valide`).subscribe({
      next: (preview) => {
        this.boxes.update((list) =>
          list.map((x) => {
            if (x.tempId !== tempId) {
              return x;
            }
            if (x.documentType?.id !== typeId) {
              return x;
            }
            return {
              ...x,
              reglePreview: preview,
              regleStatus: 'ok',
              renseignerAnneesActives: preview.activeUnknown ? x.renseignerAnneesActives : null,
              anneesActivesConfirmees: false,
            };
          }),
        );
      },
      error: () => {
        this.boxes.update((list) =>
          list.map((x) => {
            if (x.tempId !== tempId) {
              return x;
            }
            if (x.documentType?.id !== typeId) {
              return x;
            }
            return {
              ...x,
              reglePreview: null,
              regleStatus: 'error',
              renseignerAnneesActives: null,
              anneesActivesConfirmees: false,
            };
          }),
        );
      },
    });
  }

  decisionLabel(code: string): string {
    switch (code) {
      case 'CONSERVER':
        return this.i18n.t('common.decisionKeep');
      case 'DETRUIRE':
      case 'ELIMINER':
        return this.i18n.t('common.decisionDestroy');
      case 'TRANSFERER':
        return this.i18n.t('common.decisionTransfer');
      default:
        return code;
    }
  }

  confirmAssignationLabel(): string {
    return this.validatingMode() ? this.i18n.t('bordereau.validateAssignment') : this.i18n.t('bordereau.confirm');
  }

  private isSemiActifDecision(finalDecision: string): boolean {
    return finalDecision === 'DETRUIRE' || finalDecision === 'ELIMINER' || finalDecision === 'TRANSFERER';
  }

  private isActiveYearsLinkPending(b: BordereauBoiteDraft): boolean {
    return (
      b.reglePreview?.activeUnknown === true &&
      (b.renseignerAnneesActives == null ||
        b.renseignerAnneesActives < 0 ||
        !b.anneesActivesConfirmees)
    );
  }

  onActiveYearsChange(tempId: string, value: number | null): void {
    this.boxes.update((list) =>
      list.map((x) =>
        x.tempId === tempId
          ? { ...x, renseignerAnneesActives: value, anneesActivesConfirmees: false }
          : x,
      ),
    );
  }

  openConfirmActiveYears(box: BordereauBoiteDraft): void {
    if (box.renseignerAnneesActives == null || box.renseignerAnneesActives < 0) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.activeYearsWarnSummary'),
        detail: this.i18n.t('bordereau.activeYearsRequiredDetail'),
        life: 8000,
      });
      return;
    }
    this.pendingActiveYearsBox.set(box);
    this.activeYearsConfirmVisible.set(true);
  }

  onActiveYearsConfirmVisibleChange(visible: boolean): void {
    if (visible) {
      return;
    }
    this.activeYearsConfirmVisible.set(false);
    this.pendingActiveYearsBox.set(null);
  }

  confirmActiveYearsLink(): void {
    const box = this.pendingActiveYearsBox();
    if (!box || box.renseignerAnneesActives == null || box.renseignerAnneesActives < 0) {
      return;
    }
    const tempId = box.tempId;
    this.boxes.update((list) =>
      list.map((x) => (x.tempId === tempId ? { ...x, anneesActivesConfirmees: true } : x)),
    );
    this.activeYearsConfirmVisible.set(false);
    this.pendingActiveYearsBox.set(null);
    this.messages.add({
      severity: 'success',
      summary: this.i18n.t('bordereau.activeYearsConfirmedSummary'),
      detail: this.i18n.t('bordereau.activeYearsConfirmedDetail', {
        titre: box.titre.trim() || this.emDash(),
        annees: box.renseignerAnneesActives,
      }),
      life: 6000,
    });
  }

  annuler(): void {
    this.cancel.emit();
  }

  /** Enregistre le bordereau en attente sans charger ni réserver d'emplacements. */
  enregistrerEnAttente(): void {
    const err = this.validateAll();
    if (err) {
      this.messages.add({ severity: 'warn', summary: this.i18n.t('bordereau.incompleteInputSummary'), detail: err, life: 8000 });
      return;
    }
    if (this.submitting() || this.validatingAffectation()) {
      return;
    }
    this.executerCreation(true);
  }

  /**
   * Ouvre l'affectation (suggestions chargées à la demande) — même logique qu'un bordereau normal.
   * Utilisé par les boutons « Assigner » / « Confirmer ».
   */
  preparerAssignation(): void {
    if (!this.canAssignEmplacements()) {
      return;
    }
    const err = this.validateAll();
    if (err) {
      this.messages.add({ severity: 'warn', summary: this.i18n.t('bordereau.incompleteInputSummary'), detail: err, life: 8000 });
      return;
    }
    if (this.hasRegleActiveUnknownPending()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.assignmentBlockedSummary'),
        detail: this.i18n.t('bordereau.activeYearsRequiredForLink'),
        life: 12000,
      });
      return;
    }
    if (this.submitting() || this.validatingAffectation()) {
      return;
    }
    this.validatingMode.set(this.isResume());
    this.affectDialogVisible.set(false);
    this.crossTabletteDialogVisible.set(false);
    this.affectSuggestionsLoading.set(true);
    this.affectSuggestions.set([]);
    this.manualMode.set(false);
    this.manualSelections.set({});
    this.manualActiveBoxIndex.set(null);
    let params = new HttpParams();
    for (const b of this.boxes()) {
      const m = b.metrageCm ?? 10;
      params = params.append('metrageCm', String(m));
    }
    this.http.get<BoiteBlocsSuggestion[]>(`${this.api}/suggestion-emplacements-blocs`, { params }).subscribe({
      next: (list) => {
        const arr = Array.isArray(list) ? list : [];
        this.affectSuggestions.set(arr);
        this.affectSuggestionsLoading.set(false);
        if (this.shouldOpenFragmentationDialog(arr)) {
          this.crossTabletteDialogVisible.set(true);
        } else {
          this.affectDialogVisible.set(true);
        }
      },
      error: () => {
        this.affectSuggestions.set([]);
        this.affectSuggestionsLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: this.i18n.t('bordereau.locationsSummary'),
          detail: this.i18n.t('bordereau.loadBlocsError'),
          life: 9000,
        });
      },
    });
  }

  /** Confirme l'affectation depuis le dialogue (création ou reprise). */
  confirmerAssignation(): void {
    const err = this.validateAll();
    if (err) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.incompleteInputSummary'),
        detail: err,
        life: 8000,
      });
      return;
    }
    if (this.hasRegleActiveUnknownPending()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.assignmentBlockedSummary'),
        detail: this.i18n.t('bordereau.activeYearsRequiredForLink'),
        life: 12000,
      });
      return;
    }
    if (this.validatingMode()) {
      this.validerAffectation();
    } else {
      this.executerCreation(false);
    }
  }

  fermerAffectDialog(): void {
    if (this.submitting() || this.validatingAffectation()) {
      return;
    }
    this.affectDialogVisible.set(false);
    this.validatingMode.set(false);
  }

  fermerCrossTabletteDialog(): void {
    if (this.submitting() || this.validatingAffectation()) {
      return;
    }
    this.crossTabletteDialogVisible.set(false);
    this.validatingMode.set(false);
  }

  blocProposeMultiTabletteDetail(index: number): { first: string; second: string } | null {
    const row = this.affectSuggestions()[index];
    if (!row?.multiTabletteRecommendation || row.premierePlageBlocs == null) {
      return null;
    }
    const x = row.premierePlageBlocs;
    const all = row.blocs ?? [];
    if (x <= 0 || x >= all.length) {
      return null;
    }
    return {
      first: all
        .slice(0, x)
        .map((b) => b.numero)
        .join(', '),
      second: all
        .slice(x)
        .map((b) => b.numero)
        .join(', '),
    };
  }

  /**
   * Popup « Suggestion de fragmentation » : pas assez de places consécutives pour le bordereau
   * (split / multi-tablettes API, ou blocs proposés non contigus entre eux, ex. 01117-01118 + 01124).
   */
  private shouldOpenFragmentationDialog(suggestions: BoiteBlocsSuggestion[]): boolean {
    if (suggestions.some((row) => row.splitRecommendation === true || row.multiTabletteRecommendation === true)) {
      return true;
    }
    for (let i = 0; i < suggestions.length; i++) {
      const row = suggestions[i];
      const required = row.blocsRequis ?? this.blocsRequisForMetrage(this.boxes()[i]?.metrageCm ?? row.metrageCm);
      if (!this.rowHasFullConsecutivePlacement(row, required)) {
        return true;
      }
    }
    return !this.allProposedBlocsFormOneConsecutiveRange(suggestions);
  }

  private rowHasFullConsecutivePlacement(row: BoiteBlocsSuggestion, required: number): boolean {
    const blocs = row.blocs ?? [];
    if (blocs.length !== required) {
      return false;
    }
    return this.areSuggestionBlocsConsecutiveOnOneTablette(blocs);
  }

  private areSuggestionBlocsConsecutiveOnOneTablette(blocs: BlocEmplacementSuggestion[]): boolean {
    if (blocs.length <= 1) {
      return true;
    }
    const sorted = [...blocs].sort((a, b) => a.numero.localeCompare(b.numero));
    const tablettePrefix = sorted[0].numero.slice(0, -1);
    const startSlot = this.slotFromBlocNumero(sorted[0].numero);
    for (let i = 0; i < sorted.length; i++) {
      const num = sorted[i].numero;
      if (num.slice(0, -1) !== tablettePrefix) {
        return false;
      }
      if (this.slotFromBlocNumero(num) !== startSlot + i) {
        return false;
      }
    }
    return true;
  }

  /** Tous les blocs du bordereau forment une seule plage continue sur la même tablette. */
  private allProposedBlocsFormOneConsecutiveRange(suggestions: BoiteBlocsSuggestion[]): boolean {
    const numeros: string[] = [];
    for (const row of suggestions) {
      for (const b of row.blocs ?? []) {
        numeros.push(b.numero);
      }
    }
    if (numeros.length === 0) {
      return true;
    }
    numeros.sort((a, b) => a.localeCompare(b));
    const tablettePrefix = numeros[0].slice(0, -1);
    if (!numeros.every((n) => n.slice(0, -1) === tablettePrefix)) {
      return false;
    }
    for (let i = 1; i < numeros.length; i++) {
      if (this.slotFromBlocNumero(numeros[i]) !== this.slotFromBlocNumero(numeros[i - 1]) + 1) {
        return false;
      }
    }
    return true;
  }

  private slotFromBlocNumero(numero: string): number {
    return numero.charCodeAt(numero.length - 1) - '1'.charCodeAt(0);
  }

  /**
   * Bascule depuis la fenêtre « Suggestion de fragmentation » vers le mode manuel
   * du dialogue d'affectation standard : l'utilisateur voit tous les blocs libres et choisit.
   */
  assignerManuellementFragmentation(): void {
    this.crossTabletteDialogVisible.set(false);
    this.affectDialogVisible.set(true);
    this.activerModeManuel();
  }

  /** Active le mode manuel : charge les blocs libres et active la première boîte. La sélection démarre vide. */
  activerModeManuel(): void {
    this.manualMode.set(true);
    this.manualSelections.set({});
    this.manualActiveBoxIndex.set(this.boxes().length > 0 ? 0 : null);
    this.loadFreeBlocsLibres();
  }

  /** Revient au mode automatique. La sélection manuelle est effacée. */
  desactiverModeManuel(): void {
    this.manualMode.set(false);
    this.manualSelections.set({});
    this.manualActiveBoxIndex.set(null);
  }

  basculerModeManuel(): void {
    const next = !this.manualMode();
    if (next) {
      this.activerModeManuel();
    } else {
      this.desactiverModeManuel();
    }
  }

  private loadFreeBlocsLibres(): void {
    if (this.freeBlocsLoading()) {
      return;
    }
    this.freeBlocsLoading.set(true);
    let params = new HttpParams().set('page', '0').set('size', '100');
    params = addQuery(params, this.freeBlocsSearch());
    this.http
      .get<{ content?: BlocEmplacementSuggestion[]; totalElements?: number }>(`${this.api}/blocs-libres`, { params })
      .subscribe({
      next: (page) => {
        const arr = Array.isArray(page?.content) ? page.content : [];
        this.freeBlocsAvailable.set(arr);
        this.freeBlocsTotal.set(page?.totalElements ?? arr.length);
        this.freeBlocsLoading.set(false);
      },
      error: () => {
        this.freeBlocsAvailable.set([]);
        this.freeBlocsLoading.set(false);
        this.messages.add({
          severity: 'warn',
          summary: this.i18n.t('bordereau.locationsSummary'),
          detail: this.i18n.t('bordereau.loadFreeBlocsError'),
          life: 6000,
        });
      },
    });
  }

  setManualActiveBoxIndex(index: number): void {
    if (index < 0 || index >= this.boxes().length) {
      return;
    }
    this.manualActiveBoxIndex.set(index);
  }

  /** Récupère les IDs de blocs sélectionnés manuellement pour la boîte. */
  manualSelectedBlocIds(index: number): string[] {
    return this.manualSelections()[index] ?? [];
  }

  /** Libellé (numéros) des blocs sélectionnés manuellement pour la boîte. */
  manualSelectedBlocNumeros(index: number): string[] {
    const ids = this.manualSelectedBlocIds(index);
    const free = this.freeBlocsAvailable();
    const byId = new Map<string, string>();
    for (const b of free) {
      byId.set(b.id, b.numero);
    }
    return ids.map((id) => byId.get(id) ?? id);
  }

  manualBoxBlocsValid(index: number): boolean {
    const box = this.boxes()[index];
    if (!box) {
      return false;
    }
    const required = this.blocsRequisForMetrage(box.metrageCm);
    return this.manualSelectedBlocIds(index).length === required;
  }

  manualAllBoxesValid(): boolean {
    const n = this.boxes().length;
    if (n === 0) {
      return false;
    }
    for (let i = 0; i < n; i++) {
      if (!this.manualBoxBlocsValid(i)) {
        return false;
      }
      if (this.manualBoxLayoutInvalid(i)) {
        return false;
      }
    }
    return true;
  }

  /** ID de bloc déjà sélectionné par une autre boîte (donc cliquable seulement pour le retirer). */
  manualBlocOwnerIndex(blocId: string): number | null {
    const map = this.manualSelections();
    for (const key of Object.keys(map)) {
      const idx = Number(key);
      if ((map[idx] ?? []).includes(blocId)) {
        return idx;
      }
    }
    return null;
  }

  manualBlocSelectedByActiveBox(blocId: string): boolean {
    const idx = this.manualActiveBoxIndex();
    if (idx == null) {
      return false;
    }
    return (this.manualSelections()[idx] ?? []).includes(blocId);
  }

  /** Clique sur un bloc libre : ajoute ou retire de la sélection de la boîte active. */
  toggleManualBloc(blocId: string): void {
    const idx = this.manualActiveBoxIndex();
    if (idx == null) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.manualSelectionSummary'),
        detail: this.i18n.t('bordereau.chooseActiveBoxFirst'),
        life: 5000,
      });
      return;
    }
    const owner = this.manualBlocOwnerIndex(blocId);
    if (owner != null && owner !== idx) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.blocAlreadySelectedSummary'),
        detail: this.i18n.t('bordereau.blocAlreadySelectedDetail', { index: owner + 1 }),
        life: 5000,
      });
      return;
    }
    this.manualSelections.update((map) => {
      const next: Record<number, string[]> = { ...map };
      const current = [...(next[idx] ?? [])];
      const pos = current.indexOf(blocId);
      if (pos >= 0) {
        current.splice(pos, 1);
        next[idx] = current;
        return next;
      }
      const box = this.boxes()[idx];
      const required = this.blocsRequisForMetrage(box?.metrageCm ?? 10);
      if (current.length >= required) {
        this.messages.add({
          severity: 'warn',
          summary: this.i18n.t('bordereau.capacityReachedSummary'),
          detail: this.i18n.t('bordereau.capacityReachedDetail', { count: required }),
          life: 5000,
        });
        return map;
      }
      current.push(blocId);
      next[idx] = current;
      return next;
    });
  }

  retirerManualBloc(boxIndex: number, blocId: string): void {
    this.manualSelections.update((map) => {
      const next: Record<number, string[]> = { ...map };
      const current = [...(next[boxIndex] ?? [])];
      const pos = current.indexOf(blocId);
      if (pos >= 0) {
        current.splice(pos, 1);
        next[boxIndex] = current;
      }
      return next;
    });
  }

  /** Groupes (par tablette) des blocs libres pour affichage en grille dans le mode manuel. */
  freeBlocsGroupedByTablette(): { tablette: string; blocs: BlocEmplacementSuggestion[] }[] {
    const free = this.freeBlocsAvailable();
    const groups = new Map<string, BlocEmplacementSuggestion[]>();
    for (const b of free) {
      const t = b.numero.length >= 2 ? b.numero.slice(0, b.numero.length - 1) : b.numero;
      const arr = groups.get(t) ?? [];
      arr.push(b);
      groups.set(t, arr);
    }
    const result = [...groups.entries()].map(([tablette, blocs]) => ({
      tablette,
      blocs: [...blocs].sort((a, b) => a.numero.localeCompare(b.numero)),
    }));
    result.sort((a, b) => a.tablette.localeCompare(b.tablette));
    return result;
  }

  /** Détermine si la sélection manuelle d'une boîte forme un layout non valide. */
  manualBoxLayoutInvalid(index: number): boolean {
    const layout = this.manualBoxLayout(index);
    return layout === 'invalid';
  }

  /** 'consecutive' (même tablette), 'split' (deux plages même tablette), 'multi' (multi-tablettes), 'invalid', ou 'empty'. */
  manualBoxLayout(index: number): 'consecutive' | 'split' | 'multi' | 'invalid' | 'empty' {
    const numeros = this.manualSelectedBlocNumeros(index);
    if (numeros.length === 0) {
      return 'empty';
    }
    if (numeros.length === 1) {
      return 'consecutive';
    }
    const sorted = [...numeros].sort((a, b) => a.localeCompare(b));
    const groupsByTablette = new Map<string, number[]>();
    for (const n of sorted) {
      const prefix = n.slice(0, -1);
      const slot = this.slotFromBlocNumero(n);
      const arr = groupsByTablette.get(prefix) ?? [];
      arr.push(slot);
      groupsByTablette.set(prefix, arr);
    }
    const tablettes = [...groupsByTablette.keys()].sort();
    if (tablettes.length === 1) {
      const slots = (groupsByTablette.get(tablettes[0]) ?? []).slice().sort((a, b) => a - b);
      let consecutive = true;
      for (let i = 1; i < slots.length; i++) {
        if (slots[i] !== slots[i - 1] + 1) {
          consecutive = false;
          break;
        }
      }
      if (consecutive) {
        return 'consecutive';
      }
      const groups: number[][] = [[slots[0]]];
      for (let i = 1; i < slots.length; i++) {
        const last = groups[groups.length - 1];
        if (slots[i] === last[last.length - 1] + 1) {
          last.push(slots[i]);
        } else {
          groups.push([slots[i]]);
        }
      }
      if (groups.length === 2) {
        return 'split';
      }
      return 'invalid';
    }
    for (const t of tablettes) {
      const slots = (groupsByTablette.get(t) ?? []).slice().sort((a, b) => a - b);
      for (let i = 1; i < slots.length; i++) {
        if (slots[i] !== slots[i - 1] + 1) {
          return 'invalid';
        }
      }
    }
    return 'multi';
  }

  manualBoxLayoutLabel(index: number): string {
    switch (this.manualBoxLayout(index)) {
      case 'empty':
        return this.i18n.t('bordereau.layoutEmpty');
      case 'consecutive':
        return this.i18n.t('bordereau.layoutConsecutive');
      case 'split':
        return this.i18n.t('bordereau.layoutSplit');
      case 'multi':
        return this.i18n.t('bordereau.layoutMulti');
      case 'invalid':
      default:
        return this.i18n.t('bordereau.layoutInvalid');
    }
  }

  executerCreation(mettreEnAttenteFlag: boolean): void {
    if (this.submitting()) {
      return;
    }
    const err = this.validateAll();
    if (err) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.incompleteInputSummary'),
        detail: err,
        life: 8000,
      });
      return;
    }
    if (this.hasRegleActiveUnknownPending()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.assignmentBlockedSummary'),
        detail: this.i18n.t('bordereau.activeYearsRequiredForLink'),
        life: 12000,
      });
      return;
    }
    if (this.agentWorkflow() && !mettreEnAttenteFlag) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.assignmentReservedSummary'),
        detail: this.i18n.t('bordereau.assignmentReservedDetail'),
        life: 8000,
      });
      return;
    }
    if (!mettreEnAttenteFlag) {
      if (this.manualMode() && !this.manualAllBoxesValid()) {
        this.messages.add({
          severity: 'warn',
          summary: this.i18n.t('bordereau.manualIncompleteSummary'),
          detail: this.i18n.t('bordereau.manualIncompleteDetail'),
          life: 8000,
        });
        return;
      }
    }
    const enAttente = this.agentWorkflow() ? true : mettreEnAttenteFlag;
    this.submitting.set(true);
    const payload = this.buildPayload(enAttente, this.affectSuggestions());
    const resumeId = this.effectiveResumeId();
    const save$ =
      this.isResume() && resumeId != null && resumeId > 0
        ? this.http.put<BordereauDetailResponse>(`${this.api}/${resumeId}`, payload)
        : this.http.post<BordereauDetailResponse>(this.api, payload);
    const semiAlertAfterSave = !enAttente && this.hasRegleSemiActiveUnknown();
    save$.subscribe({
      next: (res) => {
        this.submitting.set(false);
        this.affectDialogVisible.set(false);
        this.crossTabletteDialogVisible.set(false);
        let detail: string;
        if (enAttente) {
          detail = this.isResume()
            ? this.i18n.t('bordereau.savedPendingResume')
            : this.agentWorkflow()
              ? this.i18n.t('bordereau.savedPendingAgent')
              : this.i18n.t('bordereau.savedPendingAdmin');
        } else {
          detail = this.isResume()
            ? this.i18n.t('bordereau.savedAssignedResume', { numero: res.numeroBordereau })
            : this.i18n.t('bordereau.savedAssignedCreate', { numero: res.numeroBordereau });
        }
        if (semiAlertAfterSave) {
          detail += this.i18n.t('bordereau.semiActiveAlertSuffix');
        }
        this.messages.add({
          severity: 'success',
          summary: this.isResume() ? this.i18n.t('bordereau.updatedSummary') : this.i18n.t('bordereau.createdSummary'),
          detail,
          life: semiAlertAfterSave ? 9000 : 6000,
        });
        this.saved.emit();
      },
      error: (err) => {
        this.submitting.set(false);
        this.toastError(err, 'bordereau.saveError');
      },
    });
  }

  blocsRequisForMetrage(metrageCm: number | null): number {
    const m = metrageCm ?? 10;
    return m / 10;
  }

  blocProposeLabel(index: number): string {
    const row = this.affectSuggestions()[index];
    if (!row?.blocs?.length) {
      return this.emDash();
    }
    return row.blocs.map((b) => b.numero).join(', ');
  }

  /** Libellé détaillé quand la suggestion est en deux plages sur la même tablette. */
  blocProposeSplitDetail(index: number): { first: string; second: string } | null {
    const row = this.affectSuggestions()[index];
    if (!row?.splitRecommendation || row.multiTabletteRecommendation || row.premierePlageBlocs == null) {
      return null;
    }
    const x = row.premierePlageBlocs;
    const all = row.blocs ?? [];
    if (x <= 0 || x >= all.length) {
      return null;
    }
    return {
      first: all.slice(0, x).map((b) => b.numero).join(', '),
      second: all.slice(x).map((b) => b.numero).join(', '),
    };
  }

  affectSuggestionIncomplete(index: number): boolean {
    const row = this.affectSuggestions()[index];
    const box = this.boxes()[index];
    if (!row || !box) {
      return true;
    }
    const required = this.blocsRequisForMetrage(box.metrageCm);
    return row.blocs.length < required;
  }

  anyAffectSuggestionIncomplete(): boolean {
    const n = this.boxes().length;
    for (let i = 0; i < n; i++) {
      if (this.affectSuggestionIncomplete(i)) {
        return true;
      }
    }
    return this.affectSuggestions().length < n;
  }

  private appendActiveYearsIfNeeded(b: BordereauBoiteDraft, target: Record<string, unknown>): void {
    if (
      b.reglePreview?.activeUnknown &&
      b.anneesActivesConfirmees &&
      b.renseignerAnneesActives != null
    ) {
      target['renseignerAnneesActives'] = b.renseignerAnneesActives;
    }
  }

  private buildPayload(mettreEnAttenteFlag: boolean, suggestions: BoiteBlocsSuggestion[]): object {
    const dir = this.directionId?.trim();
    const enAttente = this.agentWorkflow() ? true : mettreEnAttenteFlag;
    const useManual = !enAttente && this.manualMode();
    return {
      directionId: dir && dir.length > 0 ? dir : null,
      dateTransfert: this.toLocalIsoDate(this.dateTransfert),
      observation: this.observation.trim() || null,
      mettreEnAttente: enAttente,
      boites: this.boxes().map((b, i) => {
        if (enAttente) {
          const boite: Record<string, unknown> = {
            titre: b.titre.trim(),
            anneeMin: b.anneeMin,
            anneeMax: b.anneeMax,
            metrageCm: b.metrageCm ?? BordereauCreateFormComponent.DEFAULT_METRAGE_CM,
            contenu: b.contenu.trim() || null,
            motsCles: b.motsCles.trim(),
            documentTypeId: b.documentType?.id ?? null,
            emplacementBlocIds: [] as string[],
          };
          this.appendActiveYearsIfNeeded(b, boite);
          return boite;
        }
        const sugg = suggestions[i];
        const requiredBlocs = this.blocsRequisForMetrage(b.metrageCm);

        let blocIds: string[];
        let splitAccepted = false;
        let multiAccepted = false;
        if (useManual) {
          blocIds = this.manualSelectedBlocIds(i);
          const layout = this.manualBoxLayout(i);
          if (layout === 'split') {
            splitAccepted = true;
          } else if (layout === 'multi') {
            multiAccepted = true;
          }
        } else {
          blocIds = (sugg?.blocs ?? []).map((bl) => bl.id);
          splitAccepted = !!(sugg?.splitRecommendation && !sugg?.multiTabletteRecommendation);
          multiAccepted =
            !!sugg?.multiTabletteRecommendation && (sugg?.blocs?.length ?? 0) === requiredBlocs;
        }

        const boite: Record<string, unknown> = {
          titre: b.titre.trim(),
          anneeMin: b.anneeMin,
          anneeMax: b.anneeMax,
          metrageCm: b.metrageCm ?? BordereauCreateFormComponent.DEFAULT_METRAGE_CM,
          contenu: b.contenu.trim() || null,
          motsCles: b.motsCles.trim(),
          documentTypeId: b.documentType?.id ?? null,
          emplacementBlocIds: blocIds,
        };
        if (multiAccepted) {
          boite['acceptMultiTabletteBlocAssignment'] = true;
        }
        if (splitAccepted) {
          boite['acceptSplitBlocAssignment'] = true;
        }
        this.appendActiveYearsIfNeeded(b, boite);
        return boite;
      }),
    };
  }

  private toLocalIsoDate(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private validateAll(): string | null {
    if (!this.dateTransfert || Number.isNaN(new Date(this.dateTransfert).getTime())) {
      return this.i18n.t('bordereau.validateDateRequired');
    }
    if (this.boxes().length === 0) {
      return this.i18n.t('bordereau.validateAtLeastOneBox');
    }
    for (const b of this.boxes()) {
      const line = this.validateBoite(b);
      if (line) {
        return line;
      }
    }
    return null;
  }

  private validateBoite(b: BordereauBoiteDraft): string | null {
    if (!b.titre?.trim()) {
      return this.i18n.t('bordereau.validateBoxTitleRequired');
    }
    if (b.anneeMin == null || b.anneeMax == null) {
      return this.i18n.t('bordereau.validateYearsRequired');
    }
    if (!Number.isInteger(b.anneeMin) || !Number.isInteger(b.anneeMax)) {
      return this.i18n.t('bordereau.validateYearsInteger');
    }
    if (b.anneeMin < 1900 || b.anneeMin > 2100 || b.anneeMax < 1900 || b.anneeMax > 2100) {
      return this.i18n.t('bordereau.validateYearsRange');
    }
    if (b.anneeMin > b.anneeMax) {
      return this.i18n.t('bordereau.validateYearMinMax', {
        title: b.titre.trim() || this.emDash(),
      });
    }
    if (b.metrageCm == null || ![10, 20, 30, 40].includes(b.metrageCm)) {
      return this.i18n.t('bordereau.validateMetrage');
    }
    if (!b.motsCles?.trim()) {
      return this.i18n.t('bordereau.validateKeywords');
    }
    if (b.documentType == null) {
      return this.i18n.t('bordereau.validateDocType');
    }
    if (b.regleStatus === 'loading') {
      return this.i18n.t('bordereau.validateRuleLoading');
    }
    if (b.regleStatus === 'error') {
      return this.i18n.t('bordereau.validateNoValidRule', { type: b.documentType.title });
    }
    if (b.regleStatus !== 'ok' || !b.reglePreview) {
      return this.i18n.t('bordereau.validateRuleRequired');
    }
    if (b.reglePreview.activeUnknown) {
      if (b.renseignerAnneesActives == null || b.renseignerAnneesActives < 0) {
        return this.i18n.t('bordereau.validateActiveYearsRequired', {
          title: b.titre.trim() || this.emDash(),
          type: b.documentType.title,
        });
      }
      if (!b.anneesActivesConfirmees) {
        return this.i18n.t('bordereau.validateActiveYearsNotConfirmed', {
          title: b.titre.trim() || this.emDash(),
        });
      }
    }
    return null;
  }

  /**
   * Valide définitivement l'affectation depuis le dialogue principal (en reprise).
   * Envoie pour chaque boîte les blocs choisis (manuel) ou la proposition courante (auto).
   */
  validerAffectation(): void {
    const id = this.effectiveResumeId();
    if (id == null || id < 1) {
      return;
    }
    const err = this.validateAll();
    if (err) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.incompleteInputSummary'),
        detail: err,
        life: 8000,
      });
      return;
    }
    if (this.hasRegleActiveUnknownPending()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.assignmentBlockedSummary'),
        detail: this.i18n.t('bordereau.activeYearsRequiredForLink'),
        life: 12000,
      });
      return;
    }
    if (!this.canAssignEmplacements()) {
      return;
    }
    if (this.manualMode() && !this.manualAllBoxesValid()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.manualIncompleteSummary'),
        detail: this.i18n.t('bordereau.manualIncompleteValidateDetail'),
        life: 8000,
      });
      return;
    }
    const useManual = this.manualMode();
    if (!useManual && this.anyAffectSuggestionIncomplete()) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.locationsIncompleteSummary'),
        detail: this.i18n.t('bordereau.locationsIncompleteDetail'),
        life: 9000,
      });
      return;
    }
    const drafts = this.boxes();
    if (drafts.some((b) => b.boiteId == null)) {
      this.messages.add({
        severity: 'warn',
        summary: this.i18n.t('bordereau.boxesNotFoundSummary'),
        detail: this.i18n.t('bordereau.boxesNotFoundDetail'),
        life: 8000,
      });
      return;
    }
    const items: { boiteId: number; emplacementBlocIds: string[]; renseignerAnneesActives?: number }[] =
      drafts.map((b, i) => {
        const blocIds = useManual
          ? this.manualSelectedBlocIds(i)
          : (this.affectSuggestions()[i]?.blocs ?? []).map((bl) => bl.id);
        const item: { boiteId: number; emplacementBlocIds: string[]; renseignerAnneesActives?: number } = {
          boiteId: b.boiteId as number,
          emplacementBlocIds: blocIds,
        };
        if (b.reglePreview?.activeUnknown && b.anneesActivesConfirmees && b.renseignerAnneesActives != null) {
          item.renseignerAnneesActives = b.renseignerAnneesActives;
        }
        return item;
      });
    for (let i = 0; i < drafts.length; i++) {
      const required = this.blocsRequisForMetrage(drafts[i].metrageCm);
      if (items[i].emplacementBlocIds.length < required) {
        this.messages.add({
          severity: 'warn',
          summary: this.i18n.t('bordereau.locationsIncompleteSummary'),
          detail: this.i18n.t('bordereau.boxBlocsRequired', {
            title: drafts[i].titre.trim() || this.emDash(),
            count: required,
          }),
          life: 8000,
        });
        return;
      }
    }

    this.validatingAffectation.set(true);
    this.http
      .post<BordereauDetailResponse>(`${this.api}/${id}/valider-affectation`, { boites: items })
      .subscribe({
      next: (res) => {
        this.validatingAffectation.set(false);
        this.validatingMode.set(false);
        this.affectDialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('bordereau.assignmentValidatedSummary'),
          detail: this.i18n.t('bordereau.assignmentValidatedDetail', { numero: res.numeroBordereau }),
          life: 6000,
        });
        this.saved.emit();
      },
      error: (err) => {
        this.validatingAffectation.set(false);
        this.toastError(err, 'bordereau.validateAssignmentError');
      },
    });
  }

  private toastError(err: unknown, fallbackKey: string): void {
    this.messages.add({
      severity: 'error',
      summary: this.i18n.apiErrorSummary(err),
      detail: this.i18n.apiErrorDetail(err, fallbackKey),
      life: 14000,
    });
  }
}
