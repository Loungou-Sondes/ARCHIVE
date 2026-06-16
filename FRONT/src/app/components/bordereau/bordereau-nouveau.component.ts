import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { BordereauCreateFormComponent } from './bordereau-create-form.component';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Page dédiée : création ou reprise / validation d’un bordereau.
 */
@Component({
  selector: 'app-bordereau-nouveau',
  standalone: true,
  imports: [BordereauCreateFormComponent],
  templateUrl: './bordereau-nouveau.component.html',
  styleUrl: './bordereau-nouveau.component.scss',
  providers: [MessageService],
})
export class BordereauNouveauComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);

  readonly mettreEnAttente = signal(false);
  readonly resumeMode = signal(false);
  readonly fromValidation = signal(false);
  readonly bordereauId = signal<number | null>(null);

  ngOnInit(): void {
    this.fromValidation.set(
      this.route.snapshot.data['fromValidation'] === true
        || this.route.snapshot.queryParamMap.get('validationAgents') === '1',
    );
    const routeEnAttente = this.route.snapshot.data['mettreEnAttente'] === true;
    this.mettreEnAttente.set(routeEnAttente || this.auth.isAgent());
    this.resumeMode.set(this.route.snapshot.data['resume'] === true);
    if (this.resumeMode()) {
      const raw = this.route.snapshot.paramMap.get('id');
      const id = raw?.trim() ? Number(raw) : NaN;
      if (Number.isFinite(id) && id > 0) {
        this.bordereauId.set(id);
      } else {
        void this.router.navigate(this.listPath());
      }
    }
  }

  onCancel(): void {
    void this.router.navigate(this.listPath());
  }

  onSaved(): void {
    void this.router.navigate(this.listPath());
  }

  private listPath(): string[] {
    if (this.fromValidation() || this.router.url.includes('/alertes-echeances/')) {
      return ['/home', 'alertes-echeances'];
    }
    return ['/home', 'bordereau-transfert'];
  }
}
