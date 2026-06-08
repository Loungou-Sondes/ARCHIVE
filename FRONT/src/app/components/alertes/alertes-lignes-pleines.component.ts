import { CommonModule } from '@angular/common';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Component, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppTranslateService } from '../../core/i18n/app-translate.service';

export interface TabletteFillAlert {
  tabletteId: string;
  epiId: string | null;
  epiNumero: string;
  tabletteNumero: string;
  rowLabel: string;
  linearCm: number;
  occupiedCount: number;
  totalCount: number;
  fillPercent: number;
}

interface TabletteFillAlertPage {
  content: TabletteFillAlert[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

@Component({
  selector: 'app-alertes-lignes-pleines',
  standalone: true,
  imports: [CommonModule, ButtonModule, RippleModule, TooltipModule, ToastModule, TranslocoPipe],
  templateUrl: './alertes-lignes-pleines.component.html',
  styleUrl: './alertes-lignes-pleines.component.scss',
  providers: [MessageService],
})
export class AlertesLignesPleinesComponent {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly api = `${environment.apiUrl}/api/emplacements/alertes-lignes-pleines`;

  readonly totalChange = output<number>();

  readonly rows = signal<TabletteFillAlert[]>([]);
  readonly loading = signal(false);
  readonly totalRecords = signal(0);
  readonly totalPages = signal(0);
  readonly currentPage = signal(0);
  pageSize = 10;

  reload(page = 0): void {
    this.loading.set(true);
    const params = new HttpParams()
      .set('seuil', '75')
      .set('page', String(page))
      .set('size', String(this.pageSize));
    this.http.get<TabletteFillAlertPage>(this.api, { params }).subscribe({
      next: (res) => {
        this.rows.set(res?.content ?? []);
        this.totalRecords.set(res?.totalElements ?? 0);
        this.totalPages.set(res?.totalPages ?? 0);
        this.currentPage.set(res?.page ?? page);
        this.totalChange.emit(res?.totalElements ?? 0);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.messages.add({
          severity: 'error',
          summary: this.i18n.apiErrorSummary(err),
          detail: this.i18n.apiErrorDetail(err, 'alertes.loadLignesError'),
          life: 8000,
        });
      },
    });
  }

  goPage(page: number): void {
    if (page < 0 || page >= this.totalPages() || page === this.currentPage()) {
      return;
    }
    this.reload(page);
  }

  fillBarClass(pct: number): string {
    if (pct >= 90) {
      return 'fill-bar__inner--critical';
    }
    if (pct >= 80) {
      return 'fill-bar__inner--high';
    }
    return 'fill-bar__inner--warn';
  }

  openEmplacement(_row: TabletteFillAlert): void {
    void this.router.navigate(['/home', 'emplacement']);
  }
}
