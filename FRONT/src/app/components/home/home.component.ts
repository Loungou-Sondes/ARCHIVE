import { Component, DestroyRef, HostListener, effect, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { RouterOutlet, Router, NavigationEnd, ActivatedRoute } from '@angular/router';
import { filter } from 'rxjs/operators';
import { MenuComponent } from '../menu/menu.component';
import { MenuStateService } from '../../services/menu-state.service';
import { AlertesCountService } from '../../services/alertes-count.service';
import { AgentNotificationsService } from '../../services/agent-notifications.service';
import { AuthService } from '../../core/auth/auth.service';
import { AppTranslateService } from '../../core/i18n/app-translate.service';
import { LanguageService } from '../../core/i18n/language.service';
import { LangSelectComponent } from '../../core/i18n/lang-select.component';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

export interface HomeNotification {
  id: string;
  title: string;
  message: string;
  icon: string;
  type: 'info' | 'warning' | 'error' | 'success';
  bordereauId?: number;
  agentNotificationId?: number;
  numeroBordereau?: string;
  createdAt?: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterOutlet, MenuComponent, TranslocoPipe, LangSelectComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  protected readonly menuState = inject(MenuStateService);
  protected readonly auth = inject(AuthService);
  protected readonly alertesCount = inject(AlertesCountService);
  protected readonly agentNotifications = inject(AgentNotificationsService);
  protected readonly lang = inject(LanguageService);
  private readonly i18n = inject(AppTranslateService);
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  currentPageTitle = 'Tableau de bord';
  useProfileImage = false;

  notificationsActive = false;
  profileActive = false;

  constructor() {
    effect(() => {
      if (this.auth.isAgent()) {
        this.agentNotifications.data();
        this.syncAgentNotifications();
      } else {
        this.alertesCount.unread();
        this.syncNotificationsFromCounts();
      }
    });
  }

  ngOnInit(): void {
    this.refreshAlertesCountForUrl(this.router.url);
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e) => {
        this.updateTitle();
        this.refreshAlertesCountForUrl(e.urlAfterRedirects);
      });
    this.transloco.langChanges$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.updateTitle();
      if (this.auth.isAgent()) {
        this.syncAgentNotifications();
      } else {
        this.syncNotificationsFromCounts();
      }
    });
    this.updateTitle();
  }

  protected notificationBellAria(): string {
    if (this.notificationCount <= 0) {
      return this.auth.isAgent()
        ? this.i18n.t('home.bellAriaEmptyAgent')
        : this.i18n.t('home.bellAriaEmptyAdmin');
    }
    const unit = this.auth.isAgent()
      ? this.notificationCount === 1
        ? this.i18n.t('home.notificationOne')
        : this.i18n.t('home.notificationMany')
      : this.notificationCount === 1
        ? this.i18n.t('home.alertOne')
        : this.i18n.t('home.alertMany');
    return this.auth.isAgent()
      ? this.i18n.t('home.bellAriaAgent', { count: this.notificationCount, unit })
      : this.i18n.t('home.bellAriaAdmin', { count: this.notificationCount, unit });
  }

  protected profileRoleLabel(): string {
    return this.auth.isAdmin() ? this.i18n.t('agents.roleAdmin') : this.i18n.t('agents.agent');
  }

  protected notificationCountUnit(): string {
    if (this.notificationCount <= 0) {
      return '';
    }
    return this.auth.isAgent()
      ? this.notificationCount === 1
        ? this.i18n.t('home.notificationOne')
        : this.i18n.t('home.notificationMany')
      : this.notificationCount === 1
        ? this.i18n.t('home.alertOne')
        : this.i18n.t('home.alertMany');
  }

  private refreshAlertesCountForUrl(url: string): void {
    if (this.auth.isAgent()) {
      this.agentNotifications.refresh();
      return;
    }
    this.alertesCount.refresh();
  }

  protected get notificationCount(): number {
    return this.auth.isAgent()
      ? this.agentNotifications.unreadCount()
      : this.alertesCount.unread().total;
  }

  protected get notificationBadgeLabel(): string {
    return this.auth.isAgent()
      ? this.agentNotifications.badgeLabel()
      : this.alertesCount.badgeLabel();
  }

  protected get notificationsPanelTitle(): string {
    return this.auth.isAgent()
      ? this.transloco.translate('home.notifications')
      : this.transloco.translate('home.activeAlerts');
  }

  protected notifications: HomeNotification[] = [];
  private updateTitle(): void {
    let r = this.route.root;
    while (r.firstChild) {
      r = r.firstChild;
    }
    const titleKey = r.snapshot.data['titleKey'];
    this.currentPageTitle =
      typeof titleKey === 'string'
        ? this.transloco.translate(titleKey)
        : this.transloco.translate('routes.dashboard');
  }

  @HostListener('document:click')
  onDocumentClick(): void {
    this.notificationsActive = false;
    this.profileActive = false;
  }

  toggleNotifications(event: Event): void {
    event.stopPropagation();
    this.notificationsActive = !this.notificationsActive;
    this.profileActive = false;
    if (this.notificationsActive) {
      if (this.auth.isAgent()) {
        this.agentNotifications.refresh();
      } else {
        this.alertesCount.refresh();
      }
    }
  }

  viewNotifications(): void {
    this.notificationsActive = false;
    void this.router.navigate(['/home/alertes-echeances']);
  }

  openNotification(event: Event, notif: HomeNotification): void {
    event.stopPropagation();
    this.notificationsActive = false;
    if (this.auth.isAgent() && notif.bordereauId != null) {
      void this.router.navigate(['/home/bordereau-transfert', notif.bordereauId]);
      return;
    }
    void this.router.navigate(['/home/alertes-echeances']);
  }

  viewAgentBordereaux(): void {
    this.notificationsActive = false;
    void this.router.navigate(['/home/bordereau-transfert']);
  }

  clearAgentNotification(event: Event, notif: HomeNotification): void {
    event.stopPropagation();
    const id = notif.agentNotificationId;
    if (id == null) {
      return;
    }
    this.agentNotifications.dismissOne(id, () => this.syncAgentNotifications());
  }

  clearAllAgentNotifications(event: Event): void {
    event.stopPropagation();
    this.agentNotifications.dismissAll(() => this.syncAgentNotifications());
  }

  formatNotifDate(iso: string | undefined): string {
    if (!iso) {
      return '';
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
      return '';
    }
    return d.toLocaleString(this.lang.localeId(), {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  private syncAgentNotifications(): void {
    const items = this.agentNotifications
      .data()
      .items.map(
        (n): HomeNotification => ({
          id: `agent-${n.id}`,
          agentNotificationId: n.id,
          bordereauId: n.bordereauId,
          numeroBordereau: n.numeroBordereau,
          createdAt: n.createdAt,
          title: this.i18n.t('home.notifSlipValidated'),
          message:
            n.typeCode === 'BORDEREAU_VALIDE'
              ? this.i18n.t('home.notifSlipValidatedMessage', { numero: n.numeroBordereau ?? '' })
              : n.message,
          icon: 'pi pi-check-circle',
          type: 'success',
        }),
      );
    this.notifications = items;
  }

  private syncNotificationsFromCounts(): void {
    const c = this.alertesCount.unread();
    const items: HomeNotification[] = [];
    if (c.bordereauxEnAttente > 0) {
      items.push({
        id: 'bordereaux-en-attente',
        title: this.i18n.t('home.notifPendingAdminTitle'),
        message:
          c.bordereauxEnAttente === 1
            ? this.i18n.t('home.notifPendingAdminMessageOne')
            : this.i18n.t('home.notifPendingAdminMessageMany', { count: c.bordereauxEnAttente }),
        icon: 'pi pi-clock',
        type: 'warning',
      });
    }
    if (c.bordereauxValidationAgents > 0) {
      items.push({
        id: 'bordereaux-validation',
        title: this.i18n.t('home.notifValidationTitle'),
        message:
          c.bordereauxValidationAgents === 1
            ? this.i18n.t('home.notifValidationMessageOne')
            : this.i18n.t('home.notifValidationMessageMany', { count: c.bordereauxValidationAgents }),
        icon: 'pi pi-clipboard',
        type: 'warning',
      });
    }
    if (c.boitesSemiActif > 0) {
      items.push({
        id: 'semi-actif',
        title: this.i18n.t('home.notifSemiActifTitle'),
        message:
          c.boitesSemiActif === 1
            ? this.i18n.t('home.notifSemiActifMessageOne')
            : this.i18n.t('home.notifSemiActifMessageMany', { count: c.boitesSemiActif }),
        icon: 'pi pi-box',
        type: 'warning',
      });
    }
    if (c.boitesEcheance > 0) {
      items.push({
        id: 'echeance',
        title: this.i18n.t('home.notifEcheanceTitle'),
        message:
          c.boitesEcheance === 1
            ? this.i18n.t('home.notifEcheanceMessageOne')
            : this.i18n.t('home.notifEcheanceMessageMany', { count: c.boitesEcheance }),
        icon: 'pi pi-calendar',
        type: 'error',
      });
    }
    if (c.lignesPleines > 0) {
      items.push({
        id: 'lignes',
        title: this.i18n.t('home.notifLignesTitle'),
        message:
          c.lignesPleines === 1
            ? this.i18n.t('alertes.badgeLigneOne', { count: c.lignesPleines })
            : this.i18n.t('alertes.badgeLigneMany', { count: c.lignesPleines }),
        icon: 'pi pi-chart-bar',
        type: 'info',
      });
    }
    this.notifications = items;
  }

  toggleProfileMenu(event: Event): void {
    event.stopPropagation();
    this.profileActive = !this.profileActive;
    this.notificationsActive = false;
  }

  viewProfile(): void {
    this.profileActive = false;
    void this.router.navigate(['/home/profile']);
  }

  logout(): void {
    this.profileActive = false;
    this.agentNotifications.reset();
    this.auth.logout().subscribe({
      complete: () => void this.router.navigate(['/login']),
      error: () => void this.router.navigate(['/login']),
    });
  }

  handleImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.style.display = 'none';
    this.useProfileImage = false;
  }
}

