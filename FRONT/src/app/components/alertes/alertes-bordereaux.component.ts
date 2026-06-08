import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, ViewChild, computed, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe } from '@jsverse/transloco';
import { AuthService } from '../../core/auth/auth.service';
import { AlertesCountService } from '../../services/alertes-count.service';
import { AlertesArchivesBoitesComponent } from './alertes-archives-boites.component';
import { AlertesLignesPleinesComponent } from './alertes-lignes-pleines.component';
import { AlertesBordereauxEnAttenteComponent } from './alertes-bordereaux-en-attente.component';
import { AlertesValidationAgentsComponent } from './alertes-validation-agents.component';
import { AlertesPasswordResetComponent } from './alertes-password-reset.component';

@Component({
  selector: 'app-alertes-bordereaux',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    RippleModule,
    ToastModule,
    TooltipModule,
    TranslocoPipe,
    AlertesArchivesBoitesComponent,
    AlertesLignesPleinesComponent,
    AlertesBordereauxEnAttenteComponent,
    AlertesValidationAgentsComponent,
    AlertesPasswordResetComponent,
  ],
  templateUrl: './alertes-bordereaux.component.html',
  styleUrl: './alertes-bordereaux.component.scss',
  providers: [MessageService],
})
export class AlertesBordereauxComponent implements AfterViewInit {
  protected readonly auth = inject(AuthService);
  private readonly alertesCount = inject(AlertesCountService);

  @ViewChild('archivesBoites') archivesBoites?: AlertesArchivesBoitesComponent;
  @ViewChild('lignesPleines') lignesPleines?: AlertesLignesPleinesComponent;
  @ViewChild('enAttente') enAttente?: AlertesBordereauxEnAttenteComponent;
  @ViewChild('validationAgents') validationAgents?: AlertesValidationAgentsComponent;
  @ViewChild('passwordReset') passwordReset?: AlertesPasswordResetComponent;

  readonly boxAlertCount = signal(0);
  readonly lignesAlertCount = signal(0);

  readonly pendingBordereauxCount = computed(() => this.alertesCount.counts().bordereauxEnAttente);

  readonly agentActionsCount = computed(
    () =>
      this.alertesCount.counts().bordereauxValidationAgents
      + this.alertesCount.counts().passwordResetRequests,
  );

  ngAfterViewInit(): void {
    queueMicrotask(() => this.refreshAll());
  }

  refreshAll(): void {
    this.archivesBoites?.reload();
    this.lignesPleines?.reload();
    if (this.auth.isAdmin()) {
      this.enAttente?.reload();
      this.passwordReset?.reload();
      this.validationAgents?.reload();
    }
    this.alertesCount.refresh();
  }
}
