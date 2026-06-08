import { AppTranslateService } from '../i18n/app-translate.service';

export function dashboardGreetingLine(i18n: AppTranslateService, displayName: string): string {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 12) {
    return i18n.t('dashboard.greetingMorning', { name: displayName });
  }
  if (hour >= 12 && hour < 18) {
    return i18n.t('dashboard.greetingAfternoon', { name: displayName });
  }
  return i18n.t('dashboard.greetingEvening', { name: displayName });
}

export function dashboardTodayLabel(i18n: AppTranslateService): string {
  const formatted = new Intl.DateTimeFormat(i18n.localeId(), {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date());
  return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}
