import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { AfterViewInit, Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { DialogModule } from 'primeng/dialog';
import { MessageService } from 'primeng/api';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { AlertesCountService } from '../../services/alertes-count.service';
import { environment } from '../../../environments/environment';

interface PasswordResetAgentRow {
  id: string;
  userName?: string | null;
  role?: string | null;
  passwordResetRequested?: boolean;
}

interface AgentListPage {
  content: PasswordResetAgentRow[];
  totalElements: number;
}

@Component({
  selector: 'app-alertes-password-reset',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    RippleModule,
    ToastModule,
    TooltipModule,
    DialogModule,
    TranslocoPipe,
  ],
  templateUrl: './alertes-password-reset.component.html',
  styleUrl: './alertes-password-reset.component.scss',
  providers: [MessageService],
})
export class AlertesPasswordResetComponent implements AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly alertesCount = inject(AlertesCountService);

  readonly rows = signal<PasswordResetAgentRow[]>([]);
  readonly pendingCount = signal(0);
  readonly loading = signal(false);
  readonly approvingId = signal<string | null>(null);
  readonly dialogVisible = signal(false);
  readonly pendingAgent = signal<PasswordResetAgentRow | null>(null);

  ngAfterViewInit(): void {
    queueMicrotask(() => this.reload());
  }

  reload(): void {
    this.loading.set(true);
    const params = new HttpParams()
      .set('page', '0')
      .set('size', '50')
      .set('passwordResetOnly', 'true');
    this.http
      .get<AgentListPage>(`${environment.apiUrl}/api/agents`, { params })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (res) => {
          const rows = Array.isArray(res?.content) ? res.content : [];
          this.rows.set(rows);
          this.pendingCount.set(Math.max(0, Number(res?.totalElements) || rows.length));
        },
        error: (err) => {
          this.rows.set([]);
          this.pendingCount.set(0);
          this.messages.add({
            severity: 'error',
            summary: this.i18n.apiErrorSummary(err),
            detail: this.i18n.apiErrorDetail(err, 'alertes.passwordResetLoadError'),
          });
        },
      });
  }

  countLabel(): string {
    const n = this.rows().length;
    if (n <= 0) {
      return this.i18n.t('alertes.passwordResetCountZero');
    }
    if (n === 1) {
      return this.i18n.t('alertes.passwordResetCountOne');
    }
    return this.i18n.t('alertes.passwordResetCountMany', { count: n });
  }

  userInitial(row: PasswordResetAgentRow): string {
    const name = row.userName?.trim();
    if (!name) {
      return '?';
    }
    return name.replace(/\s+/g, '').slice(0, 2).toUpperCase();
  }

  roleLabel(role: string | null | undefined): string {
    if (!role?.trim()) {
      return this.i18n.t('common.emDash');
    }
    const r = role.trim().toUpperCase();
    if (r.includes('ADMIN')) {
      return this.i18n.t('agents.roleAdmin');
    }
    return this.i18n.t('agents.roleUser');
  }

  openApprove(row: PasswordResetAgentRow): void {
    this.pendingAgent.set(row);
    this.dialogVisible.set(true);
  }

  cancelApprove(): void {
    this.dialogVisible.set(false);
    this.pendingAgent.set(null);
  }

  confirmApprove(): void {
    const agent = this.pendingAgent();
    const id = agent?.id?.trim();
    if (!id) {
      return;
    }
    this.approvingId.set(id);
    this.http.post(`${environment.apiUrl}/api/auth/users/${encodeURIComponent(id)}/approve-password-reset`, {}).subscribe({
      next: () => {
        this.approvingId.set(null);
        this.dialogVisible.set(false);
        this.pendingAgent.set(null);
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('agents.approvePasswordResetSummary'),
          detail: this.i18n.t('agents.approvePasswordResetSuccess', { user: agent?.userName ?? id }),
          life: 8000,
        });
        this.reload();
        this.alertesCount.refresh();
      },
      error: (err) => {
        this.approvingId.set(null);
        this.messages.add({
          severity: 'error',
          summary: this.i18n.apiErrorSummary(err),
          detail: this.i18n.apiErrorDetail(err, 'agents.approvePasswordResetError'),
          life: 8000,
        });
      },
    });
  }
}
