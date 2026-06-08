import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { environment } from '../../../environments/environment';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import type { AgentRow } from './gestion-agent.component';

interface DirectionOption {
  id: string;
  label: string;
}

@Component({
  selector: 'app-agent-view',
  standalone: true,
  imports: [CommonModule, ButtonModule, RippleModule, TranslocoPipe],
  templateUrl: './agent-view.component.html',
  styleUrl: './agent-view.component.scss',
})
export class AgentViewComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly i18n = inject(AppTranslateService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly agent = signal<AgentRow | null>(null);
  readonly directionOptions = signal<DirectionOption[]>([]);

  ngOnInit(): void {
    this.loadDirectionOptions();
    const id = this.route.snapshot.paramMap.get('id');
    if (!id?.trim()) {
      void this.router.navigate(['/home', 'gestion-agent']);
      return;
    }
    this.http.get<AgentRow>(`${environment.apiUrl}/api/agents/${encodeURIComponent(id)}`).subscribe({
      next: (row) => {
        this.agent.set(row ?? null);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(this.i18n.apiErrorDetail(err, 'agents.loadProfileError'));
      },
    });
  }

  private loadDirectionOptions(): void {
    this.http
      .get<DirectionOption[]>(`${environment.apiUrl}/api/document-types/direction-options`)
      .subscribe({
        next: (dirs) => this.directionOptions.set(dirs ?? []),
        error: () => this.directionOptions.set([]),
      });
  }

  backToList(): void {
    void this.router.navigate(['/home', 'gestion-agent']);
  }

  hasText(value: string | null | undefined): boolean {
    return !!value?.trim();
  }

  hasNum(value: number | null | undefined): boolean {
    return value !== null && value !== undefined;
  }

  hasDate(value: string | null | undefined): boolean {
    return !!value?.trim();
  }

  hasGender(gender: number | null | undefined): boolean {
    return gender === 0 || gender === 1;
  }

  hasStatus(agent: AgentRow): boolean {
    return agent.statusId !== null && agent.statusId !== undefined;
  }

  showIdentitySection(a: AgentRow): boolean {
    return (
      this.hasText(a.userRegistrationNumber) ||
      this.hasText(a.lastName) ||
      this.hasText(a.firstName) ||
      this.hasText(a.cin) ||
      this.hasGender(a.gender)
    );
  }

  showAccountSection(a: AgentRow): boolean {
    return (
      this.hasText(a.userName) ||
      this.hasText(a.email) ||
      this.hasText(a.phoneNumber) ||
      this.hasText(a.role)
    );
  }

  showAffectationSection(a: AgentRow): boolean {
    return (
      this.hasText(a.port) ||
      this.hasText(a.positionId) ||
      this.hasNum(a.jobId) ||
      this.hasText(a.directionId) ||
      this.hasStatus(a)
    );
  }

  showDatesSection(a: AgentRow): boolean {
    return this.hasDate(a.birthDate) || this.hasDate(a.recruitmentDate);
  }

  showSummaryPanel(a: AgentRow): boolean {
    return (
      this.hasText(a.userRegistrationNumber) ||
      this.hasText(a.userName) ||
      this.hasText(a.role) ||
      this.hasText(a.directionId) ||
      this.hasStatus(a) ||
      this.hasText(a.port) ||
      this.hasText(a.email) ||
      this.hasText(a.phoneNumber)
    );
  }

  text(value: string | null | undefined): string {
    return value?.trim() ?? '';
  }

  genderLabel(g: number): string {
    return g === 0 ? this.i18n.t('agents.genderMale') : this.i18n.t('agents.genderFemale');
  }

  roleDisplay(role: string | null | undefined): string {
    if (!role?.trim()) {
      return '';
    }
    const r = role.trim().toUpperCase();
    if (r.includes('ADMIN')) {
      return this.i18n.t('agents.roleAdmin');
    }
    return this.i18n.t('agents.roleUser');
  }

  directionLabel(agent: AgentRow): string {
    const id = agent.directionId?.trim();
    if (!id) {
      return '';
    }
    const opt = this.directionOptions().find((d) => d.id === id);
    return opt?.label ?? id;
  }

  formatDate(iso: string): string {
    const d = Date.parse(iso);
    if (Number.isNaN(d)) {
      return iso;
    }
    return new Date(d).toLocaleDateString(this.i18n.localeId(), { dateStyle: 'medium' });
  }

  statusDisplay(a: AgentRow): string {
    return a.statusId === 1 ? this.i18n.t('agents.statusActive') : this.i18n.t('agents.statusInactive');
  }

  statusIsActive(a: AgentRow): boolean {
    return a.statusId === 1;
  }

  agentInitials(a: AgentRow): string {
    const first = a.firstName?.trim();
    const last = a.lastName?.trim();
    if (first && last) {
      return `${first.charAt(0)}${last.charAt(0)}`.toUpperCase();
    }
    const single = first || last || a.userName?.trim();
    if (!single) {
      return '?';
    }
    const cleaned = single.replace(/\s+/g, '');
    return cleaned.slice(0, 2).toUpperCase();
  }

  displayFullName(a: AgentRow): string {
    const parts = [a.firstName?.trim(), a.lastName?.trim()].filter(Boolean) as string[];
    if (parts.length > 0) {
      return parts.join(' ');
    }
    return a.userName?.trim() ?? '';
  }
}
