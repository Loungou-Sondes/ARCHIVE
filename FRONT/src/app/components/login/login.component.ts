import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';
import { ToastModule } from 'primeng/toast';
import { ButtonModule } from 'primeng/button';
import { RippleModule } from 'primeng/ripple';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../core/auth/auth.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { LangSelectComponent } from '../../core/i18n/lang-select.component';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule,
    ToastModule,
    ButtonModule,
    RippleModule,
    InputTextModule,
    TranslocoPipe,
    LangSelectComponent,
  ],
  providers: [MessageService],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
})
export class LoginComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly messageService = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);

  username = '';
  password = '';
  passwordVisible = false;
  badCred: string | null = null;
  submitting = false;

  ngOnInit(): void {
    const reason = this.route.snapshot.queryParamMap.get('reason');
    if (reason === 'expired') {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.sessionExpiredSummary'),
        detail: this.i18n.t('login.sessionExpiredDetail'),
        life: 9000,
      });
    } else if (reason === 'idle') {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.sessionIdleSummary'),
        detail: this.i18n.t('login.sessionIdleDetail'),
        life: 9000,
      });
    }
    if (reason) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { reason: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  onSubmit(): void {
    if (this.submitting) {
      return;
    }
    const u = this.username.trim();
    const p = this.password;
    if (!u || !p) {
      this.badCred = this.i18n.t('login.credentialsRequired');
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.fieldsRequired'),
        detail: this.badCred,
      });
      return;
    }
    this.badCred = null;
    this.submitting = true;
    this.auth
      .login(u, p)
      .pipe(finalize(() => (this.submitting = false)))
      .subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: this.i18n.t('login.loginSuccessSummary'),
            detail: this.i18n.t('login.loginSuccessDetail'),
          });
          const target = this.auth.isAdmin() ? ['/home'] : ['/home', 'bordereau-transfert'];
          void this.router.navigate(target);
        },
        error: (err) => {
          this.badCred = null;
          this.messageService.add({
            severity: 'error',
            summary: this.i18n.t('login.loginFailedSummary'),
            detail: this.i18n.apiErrorDetail(err, 'login.loginFailedDetail'),
            life: 9000,
          });
        },
      });
  }

  resetPassword(): void {
    this.messageService.add({
      severity: 'info',
      summary: this.i18n.t('login.forgotSummary'),
      detail: this.i18n.t('login.forgotContactAdminDetail'),
      life: 12000,
    });
  }
}
