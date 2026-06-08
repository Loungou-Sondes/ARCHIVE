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
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { NgIf } from '@angular/common';

type LoginMode = 'signin' | 'setPassword';

interface PasswordResetEligibilityResponse {
  eligible: boolean;
}

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    NgIf,
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
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly messageService = inject(MessageService);
  private readonly i18n = inject(AppTranslateService);
  loginMode: LoginMode = 'signin';
  username = '';
  password = '';
  newPassword = '';
  confirmNewPassword = '';
  passwordVisible = false;
  newPasswordVisible = false;
  confirmNewPasswordVisible = false;
  passwordResetApprovedVisible = false;
  badCred: string | null = null;
  submitting = false;
  settingPassword = false;
  forgotPasswordLoading = false;

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

  onUsernameChange(): void {
    this.passwordResetApprovedVisible = false;
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  toggleNewPasswordVisibility(): void {
    this.newPasswordVisible = !this.newPasswordVisible;
  }

  toggleConfirmNewPasswordVisibility(): void {
    this.confirmNewPasswordVisible = !this.confirmNewPasswordVisible;
  }

  showSignInForm(): void {
    this.loginMode = 'signin';
    this.newPassword = '';
    this.confirmNewPassword = '';
    this.newPasswordVisible = false;
    this.confirmNewPasswordVisible = false;
  }

  showSetPasswordForm(): void {
    if (!this.passwordResetApprovedVisible || !this.username.trim()) {
      return;
    }
    this.loginMode = 'setPassword';
    this.password = '';
    this.passwordVisible = false;
    this.badCred = null;
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
    if (this.forgotPasswordLoading) {
      return;
    }
    const u = this.username.trim();
    if (!u) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.forgotSummary'),
        detail: this.i18n.t('login.forgotUsernameRequired'),
      });
      return;
    }

    this.forgotPasswordLoading = true;
    this.http
      .post<PasswordResetEligibilityResponse>(
        `${environment.apiUrl}/api/auth/password-reset-eligibility/public`,
        { username: u },
      )
      .pipe(finalize(() => (this.forgotPasswordLoading = false)))
      .subscribe({
        next: (res) => {
          if (res.eligible === true) {
            this.passwordResetApprovedVisible = true;
            this.messageService.add({
              severity: 'success',
              summary: this.i18n.t('login.forgotSummary'),
              detail: this.i18n.t('login.forgotApprovedDetail'),
              life: 10000,
            });
            return;
          }
          this.passwordResetApprovedVisible = false;
          this.submitPasswordResetRequest(u);
        },
        error: () => {
          this.passwordResetApprovedVisible = false;
          this.submitPasswordResetRequest(u);
        },
      });
  }

  private submitPasswordResetRequest(username: string): void {
    this.http.post(`${environment.apiUrl}/api/auth/password-reset-request/public`, { username }).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'info',
          summary: this.i18n.t('login.forgotSummary'),
          detail: this.i18n.t('login.forgotDetail'),
          life: 12000,
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'info',
          summary: this.i18n.t('login.forgotSummary'),
          detail: this.i18n.t('login.forgotDetail'),
          life: 12000,
        });
      },
    });
  }

  completePasswordReset(): void {
    if (this.settingPassword) {
      return;
    }
    const u = this.username.trim();
    const p1 = this.newPassword;
    const p2 = this.confirmNewPassword;
    if (!u || !p1 || !p2) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.setNewPasswordTitle'),
        detail: this.i18n.t('login.setNewPasswordFieldsRequired'),
      });
      return;
    }
    if (p1.length < 4) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.setNewPasswordTitle'),
        detail: this.i18n.t('login.setNewPasswordTooShort'),
      });
      return;
    }
    if (p1 !== p2) {
      this.messageService.add({
        severity: 'warn',
        summary: this.i18n.t('login.setNewPasswordTitle'),
        detail: this.i18n.t('login.setNewPasswordMismatch'),
      });
      return;
    }

    this.settingPassword = true;
    this.http
      .post(`${environment.apiUrl}/api/auth/password-reset-complete/public`, {
        username: u,
        newPassword: p1,
        confirmPassword: p2,
      })
      .pipe(finalize(() => (this.settingPassword = false)))
      .subscribe({
        next: () => {
          this.newPassword = '';
          this.confirmNewPassword = '';
          this.loginMode = 'signin';
          this.password = '';
          this.passwordResetApprovedVisible = false;
          this.messageService.add({
            severity: 'success',
            summary: this.i18n.t('login.setNewPasswordSuccessSummary'),
            detail: this.i18n.t('login.setNewPasswordSuccessDetail'),
            life: 10000,
          });
        },
        error: (err) => {
          this.messageService.add({
            severity: 'error',
            summary: this.i18n.t('login.setNewPasswordTitle'),
            detail: this.i18n.apiErrorDetail(err, 'login.setNewPasswordNotAllowed'),
            life: 10000,
          });
        },
      });
  }
}
