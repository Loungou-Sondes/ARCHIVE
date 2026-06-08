import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { RippleModule } from 'primeng/ripple';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { BoiteConsultationService } from './boite-consultation.service';

@Component({
  selector: 'app-boite-consultation-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DialogModule,
    ButtonModule,
    RippleModule,
    TagModule,
    InputTextModule,
    TextareaModule,
    TranslocoPipe,
  ],
  templateUrl: './boite-consultation-dialog.component.html',
  styleUrl: './boite-consultation-dialog.component.scss',
})
export class BoiteConsultationDialogComponent {
  readonly consult = inject(BoiteConsultationService);
  private readonly i18n = inject(AppTranslateService);

  readonly newDossierTitre = signal('');
  readonly newDossierContenu = signal('');
  readonly newDossierAnnee = signal<number | null>(null);
  readonly dossierAnneeError = signal<string | null>(null);

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
        return 'danger';
      default:
        return 'secondary';
    }
  }

  dash(value: string | null | undefined): string {
    const s = this.formatEmplacement(value);
    return s.length > 0 ? s : this.i18n.t('common.emDash');
  }

  formatEmplacement(value: string | null | undefined): string {
    const s = typeof value === 'string' ? value.trim() : '';
    if (!s) {
      return '';
    }
    const dash = s.indexOf('-');
    if (dash > 0 && dash < s.length - 1) {
      const left = s.slice(0, dash).trim();
      const right = s.slice(dash + 1).trim();
      if (left === right) {
        return left;
      }
    }
    return s;
  }

  bordereauBoitesLabel(count: number): string {
    return count === 1
      ? this.i18n.t('boiteConsult.bordereauTotalOne')
      : this.i18n.t('boiteConsult.bordereauTotalMany', { count });
  }

  dossiersCountLabel(count: number): string {
    return count === 1
      ? this.i18n.t('boiteConsult.dossiersCountOne')
      : this.i18n.t('boiteConsult.dossiersCountMany', { count });
  }

  onDossierAnneeChange(value: number | null, anneeMin: number, anneeMax: number): void {
    this.newDossierAnnee.set(value);
    if (this.dossierAnneeError()) {
      this.dossierAnneeError.set(this.dossierAnneeValidationMessage(anneeMin, anneeMax, value));
    }
  }

  onDossierAnneeInput(raw: string | number | null, anneeMin: number, anneeMax: number): void {
    if (raw === '' || raw == null) {
      this.onDossierAnneeChange(null, anneeMin, anneeMax);
      return;
    }
    const parsed = typeof raw === 'number' ? raw : Number.parseInt(String(raw), 10);
    this.onDossierAnneeChange(Number.isFinite(parsed) ? parsed : null, anneeMin, anneeMax);
  }

  toggleAddForm(): void {
    this.consult.toggleAddDossierForm();
    if (!this.consult.showAddDossierForm()) {
      this.resetDossierForm();
    } else {
      const d = this.consult.detail();
      if (d) {
        this.resetDossierForm();
      }
    }
  }

  submitDossier(boiteId: number, anneeMin: number, anneeMax: number): void {
    const titre = this.newDossierTitre().trim();
    const annee = this.newDossierAnnee();
    if (!titre) {
      return;
    }
    const anneeError = this.dossierAnneeValidationMessage(anneeMin, anneeMax, annee);
    if (anneeError) {
      this.dossierAnneeError.set(anneeError);
      return;
    }
    const contenu = this.newDossierContenu().trim();
    this.consult.addDossier(
      boiteId,
      {
        titre,
        contenu: contenu.length > 0 ? contenu : null,
        annee: annee!,
      },
      () => this.resetDossierForm(),
    );
  }

  deleteDossier(boiteId: number, dossierId: number): void {
    this.consult.deleteDossier(boiteId, dossierId);
  }

  private resetDossierForm(): void {
    this.newDossierTitre.set('');
    this.newDossierContenu.set('');
    this.newDossierAnnee.set(null);
    this.dossierAnneeError.set(null);
  }

  private dossierAnneeValidationMessage(
    anneeMin: number,
    anneeMax: number,
    annee: number | null,
  ): string | null {
    if (annee == null) {
      return this.i18n.t('boiteConsult.yearRequired');
    }
    if (annee < anneeMin || annee > anneeMax) {
      return anneeMin === anneeMax
        ? this.i18n.t('boiteConsult.yearMustBe', { year: anneeMin })
        : this.i18n.t('boiteConsult.yearRange', { min: anneeMin, max: anneeMax });
    }
    return null;
  }
}
