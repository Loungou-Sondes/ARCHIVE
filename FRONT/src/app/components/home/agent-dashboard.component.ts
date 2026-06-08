import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  AfterViewInit,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { Chart } from 'chart.js';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/auth/auth.service';
import {
  DashboardChartService,
  type DashboardChartSlice,
  type DashboardMonthCount,
} from '../../core/charts/dashboard-chart.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { dashboardGreetingLine, dashboardTodayLabel } from '../../core/dashboard/dashboard-greeting.util';

interface AgentDashboardStats {
  bordereauxAffectes: number;
  bordereauxEnAttente: number;
  totalBoites: number;
  bordereauxParStatut: DashboardChartSlice[];
  boitesParEtat: DashboardChartSlice[];
  activiteBordereaux: DashboardMonthCount[];
  directionLabel: string | null;
}

@Component({
  selector: 'app-agent-dashboard',
  standalone: true,
  imports: [CommonModule, ProgressSpinnerModule, TranslocoPipe],
  templateUrl: './agent-dashboard.component.html',
  styleUrl: './agent-dashboard.component.scss',
})
export class AgentDashboardComponent implements AfterViewInit {
  protected readonly Math = Math;

  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly charts = inject(DashboardChartService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  protected readonly auth = inject(AuthService);

  @ViewChild('statusCanvas') private statusCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('etatCanvas') private etatCanvas?: ElementRef<HTMLCanvasElement>;
  @ViewChild('activityCanvas') private activityCanvas?: ElementRef<HTMLCanvasElement>;

  private statusChart?: Chart;
  private etatChart?: Chart;
  private activityChart?: Chart;
  private chartsReady = false;

  private readonly langTick = signal(0);

  readonly loading = signal(true);
  readonly stats = signal<AgentDashboardStats | null>(null);
  readonly loadError = signal<string | null>(null);

  readonly bordereauxTotal = computed(() => {
    const s = this.stats();
    return s ? s.bordereauxAffectes + s.bordereauxEnAttente : 0;
  });

  readonly activiteTotal = computed(() =>
    (this.stats()?.activiteBordereaux ?? []).reduce((sum, r) => sum + r.count, 0),
  );

  readonly bordereauxChartTotal = computed(() => this.charts.sumSlices(this.stats()?.bordereauxParStatut));
  readonly boitesChartTotal = computed(() => this.charts.sumSlices(this.stats()?.boitesParEtat));

  readonly greetingLine = computed(() => {
    this.langTick();
    return dashboardGreetingLine(this.i18n, this.auth.displayName());
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
        this.charts.scheduleRefresh(() => this.renderAllCharts());
      }
    });
    this.destroyRef.onDestroy(() => {
      this.charts.destroy(this.statusChart);
      this.charts.destroy(this.etatChart);
      this.charts.destroy(this.activityChart);
    });
  }

  ngAfterViewInit(): void {
    this.chartsReady = true;
    if (!this.loading() && this.stats()) {
      this.renderAllCharts();
    }
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.http
      .get<AgentDashboardStats>(`${environment.apiUrl}/api/dashboard/agent-stats`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (data) => {
          this.stats.set(data);
          this.loading.set(false);
          if (this.chartsReady) {
            this.charts.scheduleRefresh(() => this.renderAllCharts());
          }
        },
        error: (err) => {
          this.loading.set(false);
          const status = err?.status as number | undefined;
          const msg =
            status === 0
              ? this.i18n.t('dashboard.loadErrorServerShort')
              : this.i18n.t('dashboard.loadError');
          this.loadError.set(msg);
        },
      });
  }

  protected etatLabel(sl: DashboardChartSlice): string {
    return this.charts.etatLabel(sl);
  }

  protected etatColor(code: string, index: number): string {
    return this.charts.colorForEtat(code, index);
  }

  protected etatIcon(code: string): string {
    switch (code) {
      case 'SEMI_ACTIF':
        return 'fa-solid fa-box-open';
      case 'TRANSFERT':
        return 'fa-solid fa-truck-fast';
      case 'DESTRUCTION':
        return 'fa-solid fa-fire-flame-curved';
      default:
        return 'fa-solid fa-box';
    }
  }

  protected boiteShare(value: number): number {
    return this.charts.boiteShare(value, this.boitesChartTotal());
  }

  private renderAllCharts(): void {
    this.renderStatusChart();
    this.renderEtatChart();
    this.renderActivityChart();
  }

  private renderStatusChart(): void {
    const canvas = this.statusCanvas?.nativeElement;
    if (!canvas) return;
    const config = this.charts.bordereauxPolarConfig(this.stats()?.bordereauxParStatut ?? []);
    if (!config) {
      this.charts.destroy(this.statusChart);
      this.statusChart = undefined;
      return;
    }
    this.statusChart = this.charts.upsert(canvas, this.statusChart, config);
  }

  private renderEtatChart(): void {
    const canvas = this.etatCanvas?.nativeElement;
    if (!canvas) return;
    const config = this.charts.boitesDoughnutConfig(this.stats()?.boitesParEtat ?? [], {
      cutout: '72%',
      labelsWithShare: false,
    });
    if (!config) {
      this.charts.destroy(this.etatChart);
      this.etatChart = undefined;
      return;
    }
    this.etatChart = this.charts.upsert(canvas, this.etatChart, config);
  }

  private renderActivityChart(): void {
    const canvas = this.activityCanvas?.nativeElement;
    if (!canvas) return;
    const rows = this.stats()?.activiteBordereaux ?? [];
    const total = rows.reduce((s, r) => s + r.count, 0);
    if (total === 0) {
      this.charts.destroy(this.activityChart);
      this.activityChart = undefined;
      return;
    }
    const config = this.charts.activityLineConfig(rows);
    this.activityChart = this.charts.upsert(canvas, this.activityChart, config);
  }
}
