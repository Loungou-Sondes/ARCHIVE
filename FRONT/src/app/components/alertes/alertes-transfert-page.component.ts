import { Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AlertesCountService } from '../../services/alertes-count.service';

/** Conteneur : liste alertes / nouveau / détail (titres via `data.title`). */
@Component({
  selector: 'app-alertes-transfert-page',
  standalone: true,
  imports: [RouterOutlet],
  template: `<div class="alertes-shell"><router-outlet /></div>`,
  styles: [
    `
      :host {
        display: block;
      }
      .alertes-shell {
        min-height: calc(100vh - 120px);
      }
    `,
  ],
})
export class AlertesTransfertPageComponent implements OnInit {
  private readonly alertesCount = inject(AlertesCountService);

  ngOnInit(): void {
    this.alertesCount.refresh();
  }
}
