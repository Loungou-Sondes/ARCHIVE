import { Component, DestroyRef, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { AuthService } from '../../core/auth/auth.service';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgClass, NgFor, NgIf } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs/operators';
import { MenuStateService } from '../../services/menu-state.service';
import { AlertesCountService } from '../../services/alertes-count.service';
import { TranslocoPipe } from '@jsverse/transloco';

export interface MenuItem {
  labelKey: string;
  icon: string;
  badge?: string;
  expanded?: boolean;
  items?: { labelKey: string; icon: string; routerLink: string[]; badge?: string; adminOnly?: boolean }[];
  flyoutTop?: number;
}

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, NgClass, NgFor, NgIf, TranslocoPipe],
  templateUrl: './menu.component.html',
  styleUrl: './menu.component.scss',
})
export class MenuComponent implements OnInit {
  protected readonly menuState = inject(MenuStateService);
  protected readonly auth = inject(AuthService);
  protected readonly alertesCount = inject(AlertesCountService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly hostRef = inject(ElementRef<HTMLElement>);

  private static readonly ALERTES_PATH = '/home/alertes-echeances';
  private static readonly HISTORIQUE_PATH = '/home/historique';
  private static readonly ARCHIVES_ALERTES_KEY = 'menu.archivesAlerts';
  private static readonly CONSULTATION_KEY = 'menu.consultation';
  private static readonly ADMINISTRATION_KEY = 'menu.administrationGroup';

  menuItems: MenuItem[] = [
    {
      labelKey: 'menu.adminGroup',
      icon: 'fa-solid fa-building',
      expanded: false,
      items: [
        {
          labelKey: 'menu.agents',
          icon: 'fa-solid fa-user-tie',
          routerLink: ['/home', 'gestion-agent'],
        },
      ],
    },
    {
      labelKey: 'menu.settingsGroup',
      icon: 'fa-solid fa-folder-tree',
      expanded: false,
      items: [
        {
          labelKey: 'menu.documentTypes',
          icon: 'fa-solid fa-file-alt',
          routerLink: ['/home', 'types-documents'],
          adminOnly: true,
        },
        {
          labelKey: 'menu.conservationRules',
          icon: 'fa-solid fa-calendar-check',
          routerLink: ['/home', 'regles'],
          adminOnly: true,
        },
        {
          labelKey: 'menu.location',
          icon: 'fa-solid fa-location-dot',
          routerLink: ['/home', 'emplacement'],
          adminOnly: true,
        },
      ],
    },
    {
      labelKey: MenuComponent.ADMINISTRATION_KEY,
      icon: 'fa-solid fa-scale-balanced',
      expanded: false,
      items: [
        {
          labelKey: 'menu.transferSlip',
          icon: 'fa-solid fa-file-lines',
          routerLink: ['/home', 'bordereau-transfert'],
        },
      ],
    },
    {
      labelKey: MenuComponent.ARCHIVES_ALERTES_KEY,
      icon: 'fa-solid fa-box-archive',
      expanded: false,
      items: [
        {
          labelKey: 'menu.history',
          icon: 'fa-solid fa-clock-rotate-left',
          routerLink: ['/home', 'historique'],
        },
        {
          labelKey: 'menu.intermediateArchives',
          icon: 'fa-solid fa-sitemap',
          routerLink: ['/home', 'archives-intermediaires'],
        },
        {
          labelKey: 'menu.alertsDeadlines',
          icon: 'fa-solid fa-bell',
          routerLink: ['/home', 'alertes-echeances'],
        },
      ],
    },
    {
      labelKey: MenuComponent.CONSULTATION_KEY,
      icon: 'fa-solid fa-boxes-stacked',
      expanded: false,
      items: [
        {
          labelKey: 'menu.boxSearch',
          icon: 'fa-solid fa-magnifying-glass',
          routerLink: ['/home', 'recherche'],
        },
        {
          labelKey: 'menu.aiAssistant',
          icon: 'fa-solid fa-robot',
          routerLink: ['/home', 'assistant-ai'],
          adminOnly: true,
        },
      ],
    },
  ];

  ngOnInit(): void {
    this.expandCurrentRouteParent();
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((e) => {
        this.expandCurrentRouteParent();
      });
  }

  protected onSubmenuNavigate(_sub: { routerLink: string[] }): void {
    /* Le compteur reste tant que l'alerte n'est pas traitée (API). */
  }

  protected closeAllSubmenus(): void {
    this.menuItems.forEach((i) => (i.expanded = false));
  }

  protected isAlertesMenuItem(sub: { routerLink: string[] }): boolean {
    return this.submenuPath(sub) === MenuComponent.ALERTES_PATH;
  }

  protected isHistoriqueMenuItem(sub: { routerLink: string[] }): boolean {
    return this.submenuPath(sub) === MenuComponent.HISTORIQUE_PATH;
  }

  protected submenuExactActive(sub: { routerLink: string[] }): boolean {
    return !this.isAlertesMenuItem(sub);
  }

  protected alertesGroupBadge(item: MenuItem): string {
    if (item.labelKey !== MenuComponent.ARCHIVES_ALERTES_KEY) {
      return '';
    }
    return this.alertesCount.badgeLabel() ?? '';
  }

  protected visibleMenuItems(): MenuItem[] {
    if (this.auth.isAgent()) {
      return this.menuItems.filter(
        (g) =>
          g.labelKey === MenuComponent.ADMINISTRATION_KEY ||
          g.labelKey === MenuComponent.CONSULTATION_KEY,
      );
    }
    return this.menuItems;
  }

  protected visibleSubItems(item: MenuItem): NonNullable<MenuItem['items']> {
    const list = item.items ?? [];
    if (this.auth.isAdmin()) {
      return list;
    }
    return list.filter((s) => !s.adminOnly);
  }

  protected toggleSidebar(): void {
    this.menuState.toggle();
    if (this.menuState.collapsed()) {
      this.menuItems.forEach((i) => (i.expanded = false));
    }
  }

  protected toggleSubmenu(item: MenuItem, event?: MouseEvent): void {
    if (this.isCompactSidebar() && event) {
      const target = event.currentTarget as HTMLElement;
      item.flyoutTop = target.getBoundingClientRect().top;
    }
    this.menuItems.forEach((i) => {
      if (i !== item) i.expanded = false;
    });
    item.expanded = !item.expanded;
  }

  protected isCompactSidebar(): boolean {
    return this.menuState.collapsed() || window.innerWidth <= 992;
  }

  protected submenuPath(sub: { routerLink: string[] }): string {
    return (
      '/' +
      sub.routerLink
        .map((s) => s.replace(/^\/+|\/+$/g, ''))
        .filter(Boolean)
        .join('/')
    );
  }

  protected navigateSubmenu(
    sub: { routerLink: string[] },
    event: MouseEvent,
  ): void {
    event.stopPropagation();
    this.onSubmenuNavigate(sub);
    if (this.isCompactSidebar()) {
      this.closeAllSubmenus();
    }
  }

  private expandCurrentRouteParent(): void {
    const url = this.router.url.split('?')[0];
    this.visibleMenuItems().forEach((parent) => {
      if (!parent.items) return;
      const visible = this.visibleSubItems(parent);
      const hit = visible.some((child) => child.routerLink?.length && this.urlMatchesChild(url, child));
      if (hit) parent.expanded = true;
    });
  }

  private urlMatchesChild(url: string, child: { routerLink: string[] }): boolean {
    if (!child.routerLink?.length) return false;
    const path =
      '/' +
      child.routerLink.map((s) => s.replace(/^\/+|\/+$/g, '')).filter(Boolean).join('/');
    return url === path || url.startsWith(path + '/');
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const el = event.target as Node | null;
    if (this.isCompactSidebar() && el && !this.hostRef.nativeElement.contains(el)) {
      this.menuItems.forEach((i) => (i.expanded = false));
    }
  }

  @HostListener('window:scroll')
  onScroll(): void {
    if (this.isCompactSidebar()) {
      this.menuItems.forEach((i) => (i.expanded = false));
    }
  }
}
