import { APP_INITIALIZER, ApplicationConfig, provideBrowserGlobalErrorListeners, isDevMode } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withEnabledBlockingInitialNavigation } from '@angular/router';
import { providePrimeNG } from 'primeng/config';
import { MessageService } from 'primeng/api';
import Lara from '@primeng/themes/lara';
import { provideTransloco } from '@jsverse/transloco';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { credentialsInterceptor } from './core/auth/credentials.interceptor';
import { AuthService } from './core/auth/auth.service';
import { LanguageService } from './core/i18n/language.service';
import { TranslocoHttpLoader } from './core/i18n/transloco-loader';

function initAuth(auth: AuthService) {
  return () => auth.hydrateProfile();
}

function initLanguage(lang: LanguageService) {
  return () => lang.init();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideAnimationsAsync(),
    providePrimeNG({
      theme: { preset: Lara },
    }),
    provideHttpClient(withInterceptors([credentialsInterceptor, authInterceptor])),
    provideTransloco({
      config: {
        availableLangs: ['fr', 'ar'],
        defaultLang: 'fr',
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: TranslocoHttpLoader,
    }),
    MessageService,
    {
      provide: APP_INITIALIZER,
      useFactory: initLanguage,
      deps: [LanguageService],
      multi: true,
    },
    {
      provide: APP_INITIALIZER,
      useFactory: initAuth,
      deps: [AuthService],
      multi: true,
    },
    provideRouter(routes, withEnabledBlockingInitialNavigation()),
  ],
};
