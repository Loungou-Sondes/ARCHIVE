import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

export type AppLang = 'fr' | 'ar';

const STORAGE_KEY = 'app.lang';

@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private switchSeq = 0;

  readonly current = signal<AppLang>('fr');

  init(): Promise<void> {
    const stored = localStorage.getItem(STORAGE_KEY);
    const lang: AppLang = stored === 'ar' ? 'ar' : 'fr';

    return firstValueFrom(this.transloco.load(lang)).then(() => {
      this.transloco.setDefaultLang('fr');
      this.activate(lang);
    });
  }

  setLanguage(lang: AppLang): void {
    if (this.current() === lang) {
      return;
    }

    const seq = ++this.switchSeq;
    void firstValueFrom(this.transloco.load(lang)).then(() => {
      if (seq !== this.switchSeq) {
        return;
      }
      this.activate(lang);
      localStorage.setItem(STORAGE_KEY, lang);
    });
  }

  toggle(): void {
    this.setLanguage(this.current() === 'fr' ? 'ar' : 'fr');
  }

  localeId(): string {
    return this.current() === 'ar' ? 'ar-MA' : 'fr-FR';
  }

  private activate(lang: AppLang): void {
    this.current.set(lang);
    document.documentElement.lang = lang;
    document.documentElement.dir = lang === 'ar' ? 'rtl' : 'ltr';
    this.transloco.setActiveLang(lang);
  }
}
