import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';
import { dashboardGuard } from './core/auth/dashboard.guard';
import { guestGuard } from './core/auth/guest.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () => import('./components/login/login.component').then((m) => m.LoginComponent),
    canActivate: [guestGuard],
  },
  {
    path: 'home',
    loadComponent: () => import('./components/home/home.component').then((m) => m.HomeComponent),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/home/home-dashboard.component').then((m) => m.HomeDashboardComponent),
        canActivate: [dashboardGuard],
        data: { titleKey: 'routes.dashboard' },
      },
      {
        path: 'agent-dashboard',
        loadComponent: () =>
          import('./components/home/agent-dashboard.component').then((m) => m.AgentDashboardComponent),
        data: { titleKey: 'routes.agentDashboard' },
      },
      {
        path: 'agent/:id',
        loadComponent: () =>
          import('./components/gestion-agents/agent-view.component').then((m) => m.AgentViewComponent),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.agentView' },
      },
      {
        path: 'gestion-agent',
        loadComponent: () =>
          import('./components/gestion-agents/gestion-agent.component').then((m) => m.GestionAgentComponent),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.agents' },
      },
      {
        path: 'gestion-agents',
        redirectTo: 'gestion-agent',
        pathMatch: 'full',
      },
      {
        path: 'validation-bordereaux/reprendre/:id',
        redirectTo: 'alertes-echeances/reprendre/:id',
        pathMatch: 'full',
      },
      {
        path: 'validation-bordereaux/consulter/:id',
        redirectTo: 'alertes-echeances/consulter/:id',
        pathMatch: 'full',
      },
      {
        path: 'validation-bordereaux',
        redirectTo: 'alertes-echeances',
        pathMatch: 'prefix',
      },
      {
        path: 'bordereau-transfert',
        loadComponent: () =>
          import('./components/bordereau/bordereau-transfert-page.component').then(
            (m) => m.BordereauTransfertPageComponent,
          ),
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./components/bordereau/bordereau-list.component').then((m) => m.BordereauListComponent),
            data: { titleKey: 'routes.transferSlip' },
          },
          {
            path: 'nouveau',
            loadComponent: () =>
              import('./components/bordereau/bordereau-nouveau.component').then((m) => m.BordereauNouveauComponent),
            data: { titleKey: 'routes.newSlip', mettreEnAttente: false },
          },
          {
            path: 'reprendre/:id',
            loadComponent: () =>
              import('./components/bordereau/bordereau-nouveau.component').then((m) => m.BordereauNouveauComponent),
            data: { titleKey: 'routes.editSlip', mettreEnAttente: true, resume: true },
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./components/bordereau/bordereau-detail.component').then((m) => m.BordereauDetailComponent),
            data: { titleKey: 'routes.slipDetail', fromAlertes: false },
          },
        ],
      },
      {
        path: 'historique',
        loadComponent: () => import('./components/historique/historique.component').then((m) => m.HistoriqueComponent),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.history' },
      },
      {
        path: 'archives-intermediaires',
        loadComponent: () =>
          import('./components/archives-intermediaires/archives-intermediaires.component').then(
            (m) => m.ArchivesIntermediairesComponent,
          ),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.intermediateArchives' },
      },
      {
        path: 'alertes-echeances',
        loadComponent: () =>
          import('./components/alertes/alertes-transfert-page.component').then((m) => m.AlertesTransfertPageComponent),
        canActivate: [adminGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./components/alertes/alertes-bordereaux.component').then((m) => m.AlertesBordereauxComponent),
            data: { titleKey: 'routes.alertsDeadlines' },
          },
          {
            path: 'reprendre/:id',
            loadComponent: () =>
              import('./components/bordereau/bordereau-nouveau.component').then((m) => m.BordereauNouveauComponent),
            data: { titleKey: 'routes.resumeSlip', mettreEnAttente: false, resume: true, fromValidation: true },
          },
          {
            path: ':id',
            redirectTo: 'reprendre/:id',
            pathMatch: 'full',
          },
          {
            path: 'consulter/:id',
            loadComponent: () =>
              import('./components/bordereau/bordereau-detail.component').then((m) => m.BordereauDetailComponent),
            data: { titleKey: 'routes.slipDetail', fromAlertes: true },
          },
        ],
      },
      {
        path: 'profile',
        loadComponent: () => import('./components/profile/profile.component').then((m) => m.ProfileComponent),
        data: { titleKey: 'routes.profile' },
      },
      {
        path: 'emplacement',
        loadComponent: () =>
          import('./components/emplacement/emplacement.component').then((m) => m.EmplacementComponent),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.location' },
      },
      {
        path: 'types-documents',
        loadComponent: () =>
          import('./components/document-types/document-types.component').then((m) => m.DocumentTypesComponent),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.documentTypes' },
      },
      {
        path: 'regles',
        loadComponent: () =>
          import('./components/conservation-rules/conservation-rules.component').then(
            (m) => m.ConservationRulesComponent,
          ),
        canActivate: [adminGuard],
        data: { titleKey: 'routes.conservationRules' },
      },
      {
        path: 'recherche',
        loadComponent: () => import('./components/recherche/recherche.component').then((m) => m.RechercheComponent),
        data: { titleKey: 'routes.boxSearch' },
      },
    ],
  },
  { path: 'recherche', redirectTo: 'home/recherche', pathMatch: 'full' },
  { path: '**', redirectTo: 'login' },
];
