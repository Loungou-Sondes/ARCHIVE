import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { debounceTime } from 'rxjs/operators';
import { addQuery } from '../../core/http/http-query';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { RippleModule } from 'primeng/ripple';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { AlertesCountService } from '../../services/alertes-count.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

interface DirectionOption {
  id: string;
  label: string;
}

interface AgentListPage {
  content: AgentRow[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  activeCount: number;
  inactiveCount: number;
  passwordResetPendingCount: number;
}

/** Aligné sur {@code AgentResponse} (API {@code /api/agents}). */
export interface AgentRow {
  id: string | null;
  userName: string | null;
  firstName: string | null;
  lastName: string | null;
  email: string | null;
  gender: number | null;
  phoneNumber: string | null;
  role: string | null;
  userRegistrationNumber: string | null;
  cin?: string | null;
  birthDate?: string | null;
  recruitmentDate?: string | null;
  jobId?: number | null;
  positionId?: string | null;
  statusId: number | null;
  directionId: string | null;
  /** Libellé port ({@code USERS.HARBOR}). */
  port: string | null;
  passwordResetRequested?: boolean;
}

@Component({
  selector: 'app-gestion-agent',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, RippleModule, TableModule, DialogModule, TooltipModule, InputTextModule, TranslocoPipe],
  templateUrl: './gestion-agent.component.html',
  styleUrl: './gestion-agent.component.scss',
})
export class GestionAgentComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly messageService = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly alertesCount = inject(AlertesCountService);

  readonly pageReportTemplate = signal(this.i18n.t('common.pageReportEntries'));

  readonly agents = signal<AgentRow[]>([]);
  readonly totalRecords = signal(0);
  readonly totalAgents = signal(0);
  readonly activeCount = signal(0);
  readonly inactiveCount = signal(0);
  readonly passwordResetCount = signal(0);
  readonly directionOptions = signal<DirectionOption[]>([]);
  readonly loadingTable = signal(false);
  pageSize = 10;
  readonly togglingStatusId = signal<string | null>(null);
  readonly searchText = signal('');

  private readonly searchDebounced = new Subject<void>();

  statusConfirmVisible = false;
  pendingStatusAgent: AgentRow | null = null;
  pendingStatusActivate = false;

  passwordResetDialogVisible = false;
  pendingPasswordResetAgent: AgentRow | null = null;
  resettingPasswordId = signal<string | null>(null);

  ngOnInit(): void {
    this.searchDebounced.pipe(debounceTime(380), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refreshTable(0));
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.pageReportTemplate.set(this.i18n.t('common.pageReportEntries'));
    });
    this.loadDirectionOptions();
  }

  onLazyLoad(event: TableLazyLoadEvent): void {
    const first = event.first ?? 0;
    const rows = event.rows ?? this.pageSize;
    const page = Math.floor(first / rows);
    this.pageSize = rows;
    this.refreshTable(page);
  }

  onSearchValueChange(): void {
    this.searchDebounced.next();
  }

  agentCountLabel(): string {
    const n = this.totalAgents();
    return n <= 1 ? this.i18n.t('agents.agentCountOne', { count: n }) : this.i18n.t('agents.agentCountMany', { count: n });
  }

  activeCountLabel(): string {
    const n = this.activeCount();
    return n <= 1 ? this.i18n.t('agents.activeCountOne', { count: n }) : this.i18n.t('agents.activeCountMany', { count: n });
  }

  inactiveCountLabel(): string {
    const n = this.inactiveCount();
    return n <= 1 ? this.i18n.t('agents.inactiveCountOne', { count: n }) : this.i18n.t('agents.inactiveCountMany', { count: n });
  }

  emDash(): string {
    return this.i18n.t('common.emDash');
  }

  registrationMeta(value: string | null | undefined): string {
    return this.i18n.t('agents.registrationNumberMeta', {
      value: value?.trim() || this.emDash(),
    });
  }

  toggleAccountTooltip(agent: AgentRow): string {
    return this.i18n.t('agents.toggleAccount', { action: this.statusToggleLabel(agent) });
  }

  private loadDirectionOptions(): void {
    this.http
      .get<DirectionOption[]>(`${environment.apiUrl}/api/document-types/direction-options`)
      .subscribe({
        next: (dirs) => this.directionOptions.set(dirs ?? []),
        error: () => this.directionOptions.set([]),
      });
  }

  directionLabel(agent: AgentRow): string {
    const id = agent.directionId?.trim();
    if (!id) {
      return this.emDash();
    }
    const opt = this.directionOptions().find((d) => d.id === id);
    return opt?.label ?? id;
  }

  viewAgent(agent: AgentRow): void {
    const id = agent.id?.trim();
    if (!id) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('agents.consultSummary'),
        detail: this.i18n.t('agents.missingUserId'),
      });
      return;
    }
    void this.router.navigate(['/home', 'agent', id]);
  }

  refreshTable(page = 0): void {
    this.loadingTable.set(true);
    let params = new HttpParams()
      .set('page', String(page))
      .set('size', String(this.pageSize));
    params = addQuery(params, this.searchText());
    this.http.get<AgentListPage>(`${environment.apiUrl}/api/agents`, { params }).subscribe({
      next: (res) => {
        this.agents.set(res?.content ?? []);
        this.totalRecords.set(res?.totalElements ?? 0);
        const active = res?.activeCount ?? 0;
        const inactive = res?.inactiveCount ?? 0;
        this.totalAgents.set(active + inactive);
        this.activeCount.set(active);
        this.inactiveCount.set(inactive);
        this.passwordResetCount.set(res?.passwordResetPendingCount ?? 0);
        this.loadingTable.set(false);
      },
      error: (err) => {
        this.loadingTable.set(false);
        this.messageService.add({
          severity: 'error',
          summary: this.i18n.apiErrorSummary(err),
          detail: this.i18n.apiErrorDetail(err, 'agents.loadError'),
          life: 8000,
        });
      },
    });
  }

  toggleAgentStatus(agent: AgentRow): void {
    const id = agent.id?.trim();
    if (!id) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('agents.statusSummary'),
        detail: this.i18n.t('agents.missingUserId'),
      });
      return;
    }
    this.pendingStatusAgent = agent;
    this.pendingStatusActivate = !this.isAgentActive(agent);
    this.statusConfirmVisible = true;
  }

  cancelStatusConfirm(): void {
    this.statusConfirmVisible = false;
    this.pendingStatusAgent = null;
  }

  confirmStatusChange(): void {
    const agent = this.pendingStatusAgent;
    if (!agent?.id?.trim()) {
      this.cancelStatusConfirm();
      return;
    }
    const id = agent.id.trim();
    const activate = this.pendingStatusActivate;
    const label = agent.userName ?? id;
    this.togglingStatusId.set(id);
    this.http
      .patch<AgentRow>(`${environment.apiUrl}/api/agents/${encodeURIComponent(id)}/active`, {
        active: activate,
      })
      .subscribe({
        next: (updated) => {
          this.togglingStatusId.set(null);
          this.statusConfirmVisible = false;
          this.pendingStatusAgent = null;
          this.agents.update((rows) =>
            rows.map((row) => (row.id === updated.id ? { ...row, ...updated } : row))
          );
          this.messageService.add({
            severity: 'success',
            summary: this.i18n.t('common.success'),
            detail: activate
              ? this.i18n.t('agents.reactivatedDetail', { label })
              : this.i18n.t('agents.deactivatedDetail', { label }),
          });
        },
        error: (err) => {
          this.togglingStatusId.set(null);
          this.messageService.add({
            severity: 'error',
            summary: this.i18n.apiErrorSummary(err),
            detail: this.i18n.apiErrorDetail(
              err,
              activate ? 'agents.reactivationError' : 'agents.deactivationError',
            ),
            life: 8000,
          });
        },
      });
  }

  statusConfirmTitle(): string {
    return this.pendingStatusActivate
      ? this.i18n.t('agents.confirmReactivateTitle')
      : this.i18n.t('agents.confirmDeactivateTitle');
  }

  statusConfirmLead(): string {
    return this.pendingStatusActivate
      ? this.i18n.t('agents.confirmReactivateLead')
      : this.i18n.t('agents.confirmDeactivateLead');
  }

  statusConfirmAgentLabel(): string {
    const a = this.pendingStatusAgent;
    if (!a) {
      return '';
    }
    const name = [a.firstName, a.lastName].filter(Boolean).join(' ').trim();
    return name || a.userName || a.id || '—';
  }

  roleLabel(role: string | null): string {
    if (!role?.trim()) {
      return this.i18n.t('common.emDash');
    }
    const r = role.trim().toUpperCase();
    if (r.includes('ADMIN')) {
      return this.i18n.t('agents.roleAdmin');
    }
    return this.i18n.t('agents.roleUser');
  }

  isAgentActive(agent: AgentRow): boolean {
    return agent.statusId == null || agent.statusId === 1;
  }

  statusLabel(agent: AgentRow): string {
    return this.isAgentActive(agent) ? this.i18n.t('agents.statusActive') : this.i18n.t('agents.statusInactive');
  }

  statusClass(agent: AgentRow): string {
    return this.isAgentActive(agent) ? 'status-active' : 'status-inactive';
  }

  portLabel(agent: AgentRow): string {
    return agent.port?.trim() ? agent.port.trim() : '';
  }

  hasPort(agent: AgentRow): boolean {
    return !!agent.port?.trim();
  }

  hasText(value: string | null | undefined): boolean {
    return !!value?.trim();
  }

  agentInitials(agent: AgentRow): string {
    const first = agent.firstName?.trim();
    const last = agent.lastName?.trim();
    if (first && last) {
      return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
    }
    const single = first || last || agent.userName?.trim();
    if (!single) {
      return '?';
    }
    return single.replace(/\s+/g, '').slice(0, 2).toUpperCase();
  }

  displayName(agent: AgentRow): string {
    const parts = [agent.firstName?.trim(), agent.lastName?.trim()].filter(Boolean) as string[];
    if (parts.length > 0) {
      return parts.join(' ');
    }
    return agent.userName?.trim() || this.emDash();
  }

  statusToggleLabel(agent: AgentRow): string {
    return this.isAgentActive(agent) ? this.i18n.t('agents.deactivate') : this.i18n.t('agents.reactivate');
  }

  openPasswordReset(agent: AgentRow): void {
    this.pendingPasswordResetAgent = agent;
    this.passwordResetDialogVisible = true;
  }

  cancelPasswordReset(): void {
    this.passwordResetDialogVisible = false;
    this.pendingPasswordResetAgent = null;
  }

  confirmPasswordReset(): void {
    const agent = this.pendingPasswordResetAgent;
    const id = agent?.id?.trim();
    if (!id) {
      return;
    }
    this.resettingPasswordId.set(id);
    this.http.post(`${environment.apiUrl}/api/auth/users/${encodeURIComponent(id)}/approve-password-reset`, {}).subscribe({
      next: () => {
        this.resettingPasswordId.set(null);
        this.passwordResetDialogVisible = false;
        this.pendingPasswordResetAgent = null;
        this.messageService.add({
          severity: 'success',
          summary: this.i18n.t('agents.approvePasswordResetSummary'),
          detail: this.i18n.t('agents.approvePasswordResetSuccess', { user: agent?.userName ?? id }),
          life: 8000,
        });
        this.refreshTable();
        this.alertesCount.refresh();
      },
      error: (err) => {
        this.resettingPasswordId.set(null);
        this.messageService.add({
          severity: 'error',
          summary: this.i18n.apiErrorSummary(err),
          detail: this.i18n.apiErrorDetail(err, 'agents.approvePasswordResetError'),
          life: 8000,
        });
      },
    });
  }
}
