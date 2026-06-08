import { CommonModule } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AppLang, LanguageService } from './language.service';

interface LangOption {
  value: AppLang;
  labelKey: string;
}

@Component({
  selector: 'app-lang-select',
  standalone: true,
  imports: [CommonModule, TranslocoPipe],
  template: `
    <div
      class="lang-switch topbar-pill"
      [class.lang-switch--login]="variant() === 'login'"
      role="group"
      [attr.aria-label]="'lang.switchTo' | transloco">
      <i class="pi pi-globe lang-switch__globe" aria-hidden="true"></i>
      @for (option of options; track option.value) {
        <button
          type="button"
          class="lang-switch__btn"
          [class.lang-switch__btn--active]="lang.current() === option.value"
          [attr.lang]="option.value === 'ar' ? 'ar' : 'fr'"
          [attr.aria-pressed]="lang.current() === option.value"
          (click)="select($event, option.value)">
          {{ option.labelKey | transloco }}
        </button>
      }
    </div>
  `,
})
export class LangSelectComponent {
  readonly variant = input<'topbar' | 'login'>('topbar');

  protected readonly lang = inject(LanguageService);

  protected readonly options: LangOption[] = [
    { value: 'fr', labelKey: 'lang.fr' },
    { value: 'ar', labelKey: 'lang.ar' },
  ];

  protected select(event: Event, value: AppLang): void {
    event.stopPropagation();
    if (this.lang.current() !== value) {
      this.lang.setLanguage(value);
    }
  }
}
