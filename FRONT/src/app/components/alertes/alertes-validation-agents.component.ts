import { AfterViewInit, Component, ViewChild } from '@angular/core';
import { AlertesBordereauxTableComponent } from './alertes-bordereaux-table.component';
import { VALIDATION_BORDEREAUX_CONFIG } from './alertes-bordereaux-table.model';

@Component({
  selector: 'app-alertes-validation-agents',
  standalone: true,
  imports: [AlertesBordereauxTableComponent],
  template: `<app-alertes-bordereaux-table #table [config]="config" />`,
})
export class AlertesValidationAgentsComponent implements AfterViewInit {
  readonly config = VALIDATION_BORDEREAUX_CONFIG;

  @ViewChild('table') private table?: AlertesBordereauxTableComponent;

  ngAfterViewInit(): void {
    queueMicrotask(() => this.table?.reload());
  }

  reload(): void {
    this.table?.reload();
  }
}
