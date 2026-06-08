import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Badge visuel pour un bordereau sans numéro officiel (statut EN_ATTENTE). */
@Component({
  selector: 'app-bordereau-pending-badge',
  standalone: true,
  imports: [TranslocoPipe],
  template: `
    <span class="bordereau-pending-badge" role="status">
      <i class="pi pi-clock" aria-hidden="true"></i>
      <span>{{ 'bordereau.pendingBadge' | transloco }}</span>
    </span>
  `,
  styleUrl: './bordereau-pending-badge.component.scss',
})
export class BordereauPendingBadgeComponent {}
