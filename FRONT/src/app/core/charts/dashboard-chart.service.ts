import { Injectable, inject } from '@angular/core';
import { Chart, registerables, type ChartConfiguration } from 'chart.js';
import { AppTranslateService } from '../i18n/app-translate.service';
import type { DashboardChartSlice, DashboardMonthCount } from './dashboard-chart.models';

export type { DashboardChartSlice, DashboardMonthCount } from './dashboard-chart.models';

const ETAT_COLORS: Record<string, string> = {
  SEMI_ACTIF: '#10b981',
  TRANSFERT: '#6366f1',
  DESTRUCTION: '#f59e0b',
  AFFECTE: '#4f46e5',
  EN_ATTENTE: '#f59e0b',
};

const ETAT_I18N_KEYS: Record<string, string> = {
  SEMI_ACTIF: 'common.stateSemiActive',
  TRANSFERT: 'common.stateTransferred',
  DESTRUCTION: 'common.stateDestroyed',
  AFFECTE: 'dashboard.etatAffecte',
  EN_ATTENTE: 'dashboard.etatEnAttente',
};

const ETAT_FALLBACK_PALETTE = ['#10b981', '#6366f1', '#f59e0b', '#ec4899', '#8b5cf6'];

@Injectable({ providedIn: 'root' })
export class DashboardChartService {
  private readonly i18n = inject(AppTranslateService);
  private registered = false;

  registerOnce(): void {
    if (this.registered) return;
    Chart.register(...registerables);
    this.registered = true;
  }

  sumSlices(slices: DashboardChartSlice[] | undefined | null): number {
    return (slices ?? []).reduce((sum, s) => sum + (Number(s.value) || 0), 0);
  }

  etatLabel(sl: DashboardChartSlice): string {
    const key = ETAT_I18N_KEYS[sl.code];
    if (key) {
      return this.i18n.t(key);
    }
    return sl.label;
  }

  colorForEtat(code: string, index = 0): string {
    return ETAT_COLORS[code] ?? ETAT_FALLBACK_PALETTE[index % ETAT_FALLBACK_PALETTE.length];
  }

  boiteShare(value: number, total: number): number {
    if (total <= 0) return 0;
    return Math.round((value / total) * 100);
  }

  /** Double rAF : canvas prêt après @if loading. */
  scheduleRefresh(render: () => void): void {
    requestAnimationFrame(() => requestAnimationFrame(() => render()));
  }

  boundToCanvas(chart: Chart | undefined, canvas: HTMLCanvasElement): boolean {
    return !!chart && chart.canvas === canvas;
  }

  upsert(
    canvas: HTMLCanvasElement,
    existing: Chart | undefined,
    config: ChartConfiguration,
  ): Chart {
    if (existing && existing.canvas === canvas) {
      existing.data = config.data!;
      existing.options = config.options!;
      existing.update();
      return existing;
    }
    existing?.destroy();
    return new Chart(canvas, config);
  }

  destroy(chart: Chart | undefined): void {
    chart?.destroy();
  }

  activityBarConfig(
    rows: DashboardMonthCount[],
    options?: { datasetLabel?: string; tickWeight?: 'bold' | number },
  ): ChartConfiguration<'bar'> {
    const tickWeight = options?.tickWeight ?? 'bold';
    return {
      type: 'bar',
      data: {
        labels: rows.map((r) => r.label),
        datasets: [
          {
            label: options?.datasetLabel ?? this.i18n.t('dashboard.chartDatasetBordereaux'),
            data: rows.map((r) => r.count),
            backgroundColor: 'rgba(99, 102, 241, 0.82)',
            borderColor: '#4f46e5',
            borderWidth: 1,
            borderRadius: 8,
            maxBarThickness: 48,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => this.i18n.t('dashboard.chartTooltipBordereau', { count: ctx.parsed.y }),
            },
          },
        },
        scales: {
          x: { grid: { display: false }, ticks: { font: { size: 11, weight: tickWeight } } },
          y: {
            beginAtZero: true,
            ticks: { stepSize: 1, precision: 0 },
            grid: { color: 'rgba(148, 163, 184, 0.25)' },
          },
        },
      },
    };
  }

  activityLineConfig(
    rows: DashboardMonthCount[],
    options?: { datasetLabel?: string },
  ): ChartConfiguration<'line'> {
    return {
      type: 'line',
      data: {
        labels: rows.map((r) => r.label),
        datasets: [
          {
            label: options?.datasetLabel ?? this.i18n.t('dashboard.chartDatasetBordereauxCreated'),
            data: rows.map((r) => r.count),
            fill: true,
            tension: 0.35,
            backgroundColor: 'rgba(99, 102, 241, 0.12)',
            borderColor: '#4f46e5',
            borderWidth: 2.5,
            pointBackgroundColor: '#4f46e5',
            pointBorderColor: '#fff',
            pointBorderWidth: 2,
            pointRadius: 5,
            pointHoverRadius: 7,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => this.i18n.t('dashboard.chartTooltipBordereau', { count: ctx.parsed.y }),
            },
          },
        },
        scales: {
          x: { grid: { display: false }, ticks: { font: { size: 11, weight: 'bold' } } },
          y: {
            beginAtZero: true,
            ticks: { stepSize: 1, precision: 0 },
            grid: { color: 'rgba(148,163,184,0.25)' },
          },
        },
      },
    };
  }

  boitesDoughnutConfig(
    slices: DashboardChartSlice[],
    options?: { cutout?: string; labelsWithShare?: boolean },
  ): ChartConfiguration<'doughnut'> | null {
    const total = this.sumSlices(slices);
    if (total === 0) return null;

    const colors = slices.map((s, i) => this.colorForEtat(s.code, i));
    const labels = options?.labelsWithShare
      ? slices.map((s) => {
          const pct = this.boiteShare(s.value, total);
          return `${this.etatLabel(s)} — ${s.value} (${pct} %)`;
        })
      : slices.map((s) => s.label);

    return {
      type: 'doughnut',
      data: {
        labels,
        datasets: [
          {
            data: slices.map((s) => s.value),
            backgroundColor: colors,
            borderColor: '#ffffff',
            borderWidth: 2,
            hoverOffset: 6,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: options?.cutout ?? '62%',
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const v = Number(ctx.parsed) || 0;
                const pct = this.boiteShare(v, total);
                return `${ctx.label} : ${v} (${pct} %)`;
              },
            },
          },
        },
      },
    };
  }

  bordereauxPolarConfig(
    slices: DashboardChartSlice[],
    palette?: { fill: string[]; border: string[] },
  ): ChartConfiguration<'polarArea'> | null {
    const total = this.sumSlices(slices);
    if (total === 0) return null;

    const fill = palette?.fill ?? ['rgba(79, 70, 229, 0.75)', 'rgba(245, 158, 11, 0.75)'];
    const border = palette?.border ?? ['#4f46e5', '#d97706'];

    return {
      type: 'polarArea',
      data: {
        labels: slices.map((s) => this.etatLabel(s)),
        datasets: [
          {
            data: slices.map((s) => s.value),
            backgroundColor: fill.slice(0, slices.length),
            borderColor: border.slice(0, slices.length),
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'bottom',
            labels: { boxWidth: 12, padding: 14, font: { size: 12, weight: 'bold' } },
          },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const v = Number(ctx.parsed.r) || 0;
                const pct = this.boiteShare(v, total);
                return ` ${ctx.label}: ${v} (${pct} %)`;
              },
            },
          },
        },
        scales: {
          r: {
            beginAtZero: true,
            ticks: { display: false },
            grid: { color: 'rgba(148,163,184,0.2)' },
          },
        },
      },
    };
  }
}
