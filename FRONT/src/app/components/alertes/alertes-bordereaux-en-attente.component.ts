import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { AlertesBordereauxTableComponent } from './alertes-bordereaux-table.component';
import { PENDING_BORDEREAUX_CONFIG } from './alertes-bordereaux-table.model';

@Component({
  selector: 'app-alertes-bordereaux-en-attente',
  standalone: true,
  imports: [AlertesBordereauxTableComponent],
  template: `<app-alertes-bordereaux-table #table [config]="config" />`,
})
export class AlertesBordereauxEnAttenteComponent implements AfterViewInit {
  readonly config = PENDING_BORDEREAUX_CONFIG;

  @ViewChild('table') private table?: AlertesBordereauxTableComponent;

  ngAfterViewInit(): void {
    queueMicrotask(() => this.table?.reload());
  }

  reload(): void {
    this.table?.reload();
  }
}
