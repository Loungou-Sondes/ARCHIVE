import { Injectable, inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { resolveApiErrorDetail, resolveApiErrorSummary } from './api-error';
import { LanguageService } from './language.service';

/** Raccourci pour textes dynamiques (toasts, tooltips TS). */
@Injectable({ providedIn: 'root' })
export class AppTranslateService {
  private readonly transloco = inject(TranslocoService);
  readonly lang = inject(LanguageService);

  t(key: string, params?: Record<string, unknown>): string {
    return this.transloco.translate(key, params);
  }

  localeId(): string {
    return this.lang.localeId();
  }

  /** Détail d'erreur API : code → errors.*, sinon clé de repli (jamais le message FR brut). */
  apiErrorDetail(err: unknown, fallbackKey: string): string {
    return resolveApiErrorDetail((key, params) => this.t(key, params), err, fallbackKey);
  }

  apiErrorSummary(err: unknown): string {
    return resolveApiErrorSummary((key) => this.t(key), err);
  }
}
