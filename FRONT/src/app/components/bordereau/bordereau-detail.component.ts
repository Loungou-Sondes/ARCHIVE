import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TranslocoPipe } from '@jsverse/transloco';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { BordereauPendingBadgeComponent } from './bordereau-pending-badge.component';

export interface BoiteDetailRow {
  id: number;
  titre: string;
  anneeMin: number;
  anneeMax: number;
  metrageCm: number;
  contenu: string | null;
  motsCles: string | null;
  documentTypeId: number;
  documentTypeTitle: string;
  emplacementBlocDebutId?: string | null;
  emplacementBlocFinId?: string | null;
  emplacementBlocPlage?: string | null;
  emplacementBlocId?: string | null;
  emplacementBlocNumero?: string | null;
  typeEtat?: string | null;
  typeEtatLabel?: string | null;
  dateEtat?: string | null;
}

export interface BordereauDetail {
  id: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionId: string | null;
  directionLabel: string | null;
  agentUserName: string;
  observation: string | null;
  statut?: string;
  boites: BoiteDetailRow[];
}

@Component({
  selector: 'app-bordereau-detail',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    RippleModule,
    TagModule,
    ToastModule,
    DialogModule,
    BordereauPendingBadgeComponent,
    TranslocoPipe,
  ],
  templateUrl: './bordereau-detail.component.html',
  styleUrl: './bordereau-detail.component.scss',
  providers: [MessageService],
})
export class BordereauDetailComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  protected readonly auth = inject(AuthService);

  private readonly api = `${environment.apiUrl}/api/bordereaux`;

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly detail = signal<BordereauDetail | null>(null);
  readonly fromAlertes = signal(false);
  readonly fromValidation = signal(false);
  readonly deleting = signal(false);
  readonly supprimerDialogVisible = signal(false);

  ngOnInit(): void {
    this.fromAlertes.set(this.route.snapshot.data['fromAlertes'] === true);
    this.fromValidation.set(
      this.route.snapshot.data['fromValidation'] === true
        || this.route.snapshot.queryParamMap.get('validationAgents') === '1',
    );
    const raw = this.route.snapshot.paramMap.get('id');
    const id = raw?.trim() ? Number(raw) : NaN;
    if (!Number.isFinite(id) || id < 1) {
      void this.router.navigate(this.fromAlertes() ? ['/home', 'alertes-echeances'] : ['/home', 'bordereau-transfert']);
      return;
    }
    this.loadDetail(id);
  }

  private loadDetail(id: number): void {
    this.loading.set(true);
    this.http.get<BordereauDetail>(`${this.api}/${id}`).subscribe({
      next: (d) => {
        if (
          d?.statut === 'EN_ATTENTE'
          && this.auth.isAdmin()
          && this.fromAlertes()
          && !this.fromValidation()
        ) {
          void this.router.navigate(['/home', 'alertes-echeances', 'reprendre', String(id)], {
            replaceUrl: true,
          });
          return;
        }
        this.detail.set(d ?? null);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.i18n.apiErrorDetail(err, 'bordereau.loadDetailError'));
      },
    });
  }

  backToList(): void {
    void this.router.navigate(this.backPath());
  }

  private backPath(): string[] {
    if (this.fromValidation()) {
      return ['/home', 'alertes-echeances'];
    }
    if (this.fromAlertes()) {
      return ['/home', 'alertes-echeances'];
    }
    return ['/home', 'bordereau-transfert'];
  }

  isEnAttente(): boolean {
    return this.detail()?.statut === 'EN_ATTENTE';
  }

  /** Reprise du formulaire avec dialogue d'affectation (blocs obligatoires). */
  allerAssignerEmplacements(): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    void this.router.navigate(['/home', 'alertes-echeances', 'reprendre', d.id], {
      queryParams: this.fromValidation() ? { validationAgents: '1' } : {},
    });
  }

  /** Agent : modifier un bordereau en attente (sans affectation). */
  modifierBordereauEnAttente(): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    void this.router.navigate(['/home', 'bordereau-transfert', 'reprendre', d.id]);
  }

  openSupprimerDialog(): void {
    this.supprimerDialogVisible.set(true);
  }

  supprimerBordereau(): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.deleting.set(true);
    this.http.delete(`${this.api}/${d.id}`).subscribe({
      next: () => {
        this.deleting.set(false);
        this.supprimerDialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('alertes.slipDeleted'),
          detail: this.i18n.t('alertes.slipDeletedDetail', { numero: d.numeroBordereau }),
          life: 6000,
        });
        void this.router.navigate(['/home', 'alertes-echeances']);
      },
      error: (err) => {
        this.deleting.set(false);
        this.messages.add({
          severity: 'error',
          summary: this.i18n.apiErrorSummary(err),
          detail: this.i18n.apiErrorDetail(err, 'alertes.deleteSlipError'),
          life: 10000,
        });
      },
    });
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  dash(value: string | null | undefined): string {
    const s = value?.trim();
    return s ? s : this.emDash();
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

  isInHistorique(box: BoiteDetailRow): boolean {
    return box.typeEtat === 'TRANSFERT' || box.typeEtat === 'DESTRUCTION';
  }

  historiqueBanner(box: BoiteDetailRow): string {
    if (box.typeEtat === 'TRANSFERT') {
      return this.i18n.t('bordereau.historiqueTransferred');
    }
    if (box.typeEtat === 'DESTRUCTION') {
      return this.i18n.t('bordereau.historiqueDestroyed');
    }
    return '';
  }

  etatSeverity(typeEtat: string | null | undefined): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    switch (typeEtat) {
      case 'SEMI_ACTIF':
        return 'success';
      case 'TRANSFERT':
        return 'info';
      case 'DESTRUCTION':
        return 'danger';
      default:
        return 'secondary';
    }
  }
}
