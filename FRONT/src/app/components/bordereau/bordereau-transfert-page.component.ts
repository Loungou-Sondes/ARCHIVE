import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Conteneur : sous-routes liste / nouveau / détail (titres via `data.title` pour la topbar). */
@Component({
  selector: 'app-bordereau-transfert-page',
  standalone: true,
  imports: [RouterOutlet],
  template: `<div class="bordereau-shell"><router-outlet /></div>`,
  styles: [
    `
      :host {
        display: block;
      }
      .bordereau-shell {
        min-height: calc(100vh - 120px);
      }
    `,
  ],
})
export class BordereauTransfertPageComponent {}
