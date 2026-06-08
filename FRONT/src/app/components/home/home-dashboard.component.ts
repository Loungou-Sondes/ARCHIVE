import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Chart } from 'chart.js';
import { MessageService } from 'primeng/api';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { ToastModule } from 'primeng/toast';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import {
  DashboardChartService,
  type DashboardChartSlice,
  type DashboardMonthCount,
} from '../../core/charts/dashboard-chart.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { dashboardGreetingLine, dashboardTodayLabel } from '../../core/dashboard/dashboard-greeting.util';

export type DashboardPeriodKey = 'current' | '30d' | 'ytd';

interface DashboardKpi {
  bordereauxAffectes: number;
  bordereauxEnAttente: number;
  bordereauxSurPeriode: number | null;
  boitesSemiActif: number;
  boitesTransfert: number;
  boitesDestruction: number;
  epis: number;
  blocsTotal: number;
  blocsOccupes: number;
  occupationPourcent: number;
  agents: number;
}

interface AgentRank {
  userName: string;
  bordereauxCount: number;
}

interface DashboardStats {
  period: string;
  kpis: DashboardKpi;
  boitesParEtat: DashboardChartSlice[];
  activiteBordereaux: DashboardMonthCount[];
  agentsParVolume: AgentRank[];
}

interface PeriodOption {
  label: string;
  value: DashboardPeriodKey;
}

@Component({
  selector: 'app-home-dashboard',
  standalone: true,
  imports: [CommonModule, ProgressSpinnerModule, ToastModule, TranslocoPipe],
  templateUrl: './home-dashboard.component.html',
  styleUrl: './home-dashboard.component.scss',
  providers: [MessageService],
})
export class HomeDashboardComponent implements AfterViewInit {
  private readonly http = inject(HttpClient);
  private readonly messages = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly charts = inject(DashboardChartService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  protected readonly auth = inject(AuthService);

  @ViewChild('activityCanvas') private activityCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('boitesCanvas') private boitesCanvas?: ElementRef<HTMLCanvasElement>;

  private activityChart?: Chart;
  private boitesChart?: Chart;
  private chartsReady = false;

  private readonly api = `${environment.apiUrl}/api/dashboard/stats`;
  private readonly langTick = signal(0);

  readonly loading = signal(true);
  readonly stats = signal<DashboardStats | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly period = signal<DashboardPeriodKey>('current');

  readonly periodOptions = computed((): PeriodOption[] => {
    this.langTick();
    return [
      { label: this.i18n.t('dashboard.periodCurrent'), value: 'current' },
      { label: this.i18n.t('dashboard.period30d'), value: '30d' },
      { label: this.i18n.t('dashboard.periodYtd'), value: 'ytd' },
    ];
  });

  readonly boiteTotal = computed(() => this.charts.sumSlices(this.stats()?.boitesParEtat));

  readonly activiteAffiche = computed(() => {
    const rows = this.stats()?.activiteBordereaux ?? [];
    const nonZero = rows.filter((r) => r.count > 0);
    return nonZero.length > 0 ? nonZero : rows.slice(-3);
  });

  readonly activiteTotal = computed(() =>
    (this.stats()?.activiteBordereaux ?? []).reduce((s, r) => s + r.count, 0),
  );

  readonly periodKpiLabel = computed(() => {
    this.langTick();
    switch (this.period()) {
      case '30d':
        return this.i18n.t('dashboard.periodKpi30d');
      case 'ytd':
        return this.i18n.t('dashboard.periodKpiYtd');
      default:
        return '';
    }
  });

  readonly chartPeriodHint = computed(() => {
    this.langTick();
    switch (this.period()) {
      case '30d':
        return this.i18n.t('dashboard.chartHint30d');
      case 'ytd':
        return this.i18n.t('dashboard.chartHintYtd');
      default:
        return this.i18n.t('dashboard.chartHintCurrent');
    }
  });

  readonly bordereauxTotal = computed(() => {
    const k = this.stats()?.kpis;
    if (!k) return 0;
    return k.bordereauxAffectes + k.bordereauxEnAttente;
  });

  readonly greetingLine = computed(() => {
    this.langTick();
    return dashboardGreetingLine(this.i18n, this.auth.displayName());
  });

  readonly roleShortLabel = computed(() => {
    this.langTick();
    return this.auth.isAdmin()
      ? this.i18n.t('dashboard.roleAdminShort')
      : this.i18n.t('agents.roleUser');
  });

  readonly todayLabel = computed(() => {
    this.langTick();
    return dashboardTodayLabel(this.i18n);
  });

  constructor() {
    this.charts.registerOnce();
    this.load();
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.langTick.update((v) => v + 1);
      if (this.chartsReady && !this.loading() && this.stats()) {
        this.charts.scheduleRefresh(() => this.refreshCharts());
      }
    });
    effect(() => {
      this.stats();
      this.period();
      const isLoading = this.loading();
      if (!this.chartsReady || isLoading) return;
      this.charts.scheduleRefresh(() => this.refreshCharts());
    });
    this.destroyRef.onDestroy(() => this.destroyCharts());
  }

  ngAfterViewInit(): void {
    this.chartsReady = true;
    if (!this.loading() && this.stats()) {
      this.charts.scheduleRefresh(() => this.refreshCharts());
    }
  }

  protected etatLabel(sl: DashboardChartSlice): string {
    return this.charts.etatLabel(sl);
  }

  protected etatColor(code: string, index: number): string {
    return this.charts.colorForEtat(code, index);
  }

  private destroyCharts(): void {
    this.charts.destroy(this.activityChart);
    this.charts.destroy(this.boitesChart);
    this.activityChart = undefined;
    this.boitesChart = undefined;
  }

  private refreshCharts(): void {
    this.renderActivityChart();
    this.renderBoitesChart();
  }

  private renderActivityChart(): void {
    const canvas = this.activityCanvas?.nativeElement;
    if (!canvas) return;

    const rows = this.activiteAffiche();
    if (this.activiteTotal() === 0) {
      this.charts.destroy(this.activityChart);
      this.activityChart = undefined;
      return;
    }

    const config = this.charts.activityBarConfig(rows);
    this.activityChart = this.charts.upsert(canvas, this.activityChart, config);
  }

  private renderBoitesChart(): void {
    const canvas = this.boitesCanvas?.nativeElement;
    if (!canvas) return;

    const slices = this.stats()?.boitesParEtat ?? [];
    const config = this.charts.boitesDoughnutConfig(slices, { labelsWithShare: true });
    if (!config) {
      this.charts.destroy(this.boitesChart);
      this.boitesChart = undefined;
      return;
    }
    this.boitesChart = this.charts.upsert(canvas, this.boitesChart, config);
  }

  onPeriodChange(value: DashboardPeriodKey): void {
    this.period.set(value);
    this.load();
  }

  load(): void {
    this.destroyCharts();
    this.loading.set(true);
    this.loadError.set(null);
    const p = this.period();
    this.http
      .get<DashboardStats>(this.api, { params: { period: p } })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.stats.set(data);
          this.loading.set(false);
          this.loadError.set(null);
          if (this.chartsReady) {
            this.charts.scheduleRefresh(() => this.refreshCharts());
          }
        },
        error: (err) => {
          this.stats.set(null);
          this.loading.set(false);
          const msg = this.resolveLoadError(err);
          this.loadError.set(msg);
          this.messages.add({ severity: 'error', summary: msg, life: 10000 });
        },
      });
  }

  boiteShare(value: number): number {
    return this.charts.boiteShare(value, this.boiteTotal());
  }

  private resolveLoadError(err: unknown): string {
    const status = (err as { status?: number })?.status;
    if (status === 0) {
      return this.i18n.t('dashboard.loadErrorServer');
    }
    if (status === 404) {
      return this.i18n.t('dashboard.loadError404');
    }
    if (status === 403) {
      return this.i18n.t('dashboard.loadError403');
    }
    return this.i18n.apiErrorDetail(err, 'dashboard.loadError');
  }
}
