import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { adminGuard, authGuard, roleGuard, staffGuard } from './core/auth/auth.guard';

// Public discovery pages are eagerly bundled with the shell for fast first paint; everything else is lazy-loaded.
export const routes: Routes = [
  { path: '', pathMatch: 'full', component: HomeComponent },
  { path: 'events', pathMatch: 'full', loadComponent: () => import('./features/events/events-browse.component').then(m => m.EventsBrowseComponent) },
  { path: 'events/:slug', loadComponent: () => import('./features/event-page/event-page.component').then(m => m.EventPageComponent) },
  { path: 'checkout', loadComponent: () => import('./features/checkout/checkout.component').then(m => m.CheckoutComponent) },
  { path: 'payment/success', loadComponent: () => import('./features/payment-result/payment-result.component').then(m => m.PaymentResultComponent) },
  { path: 'ticket/:ticketId', loadComponent: () => import('./features/ticket/ticket.component').then(m => m.TicketComponent) },
  { path: 'recover', loadComponent: () => import('./features/recover/recover.component').then(m => m.RecoverComponent) },
  { path: 'login', loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent) },
  { path: 'accept-invite', title: 'Accept invitation · Neelastack Events', loadComponent: () => import('./features/auth/accept-invite.component').then(m => m.AcceptInviteComponent) },
  { path: 'change-password', title: 'Change password · Neelastack Events', canActivate: [authGuard], loadComponent: () => import('./features/auth/change-password.component').then(m => m.ChangePasswordComponent) },
  { path: 'setup/initial-admin', loadComponent: () => import('./features/setup/initial-admin.component').then(m => m.InitialAdminComponent) },
  { path: 'not-found', loadComponent: () => import('./shared/not-found.component').then(m => m.NotFoundComponent) },
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/admin-shell.component').then(m => m.AdminShellComponent),
    canActivate: [adminGuard],
    children: [
      { path: '', pathMatch: 'full', title: 'Console · Neelastack Events', loadComponent: () => import('./features/admin/admin-overview.component').then(m => m.AdminOverviewComponent) },
      { path: 'events', pathMatch: 'full', title: 'Events · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER', 'EVENT_MANAGER')], loadComponent: () => import('./features/admin/events-list.component').then(m => m.EventsListComponent) },
      { path: 'tickets', title: 'Issued tickets · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER', 'EVENT_MANAGER')], loadComponent: () => import('./features/admin/issued-tickets.component').then(m => m.IssuedTicketsComponent) },
      { path: 'events/new', title: 'Create event · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER')], loadComponent: () => import('./features/admin/event-create.component').then(m => m.EventCreateComponent) },
      { path: 'events/:eventId/operations', title: 'Event operations · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER', 'EVENT_MANAGER')], loadComponent: () => import('./features/admin/event-operations.component').then(m => m.EventOperationsComponent) },
      { path: 'finance', title: 'Finance · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER', 'FINANCE')], loadComponent: () => import('./features/admin/finance.component').then(m => m.FinanceComponent) },
      { path: 'operations', title: 'Operations health · Console', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./features/admin/operations-health.component').then(m => m.OperationsHealthComponent) },
      { path: 'events/:eventId', title: 'Edit event · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER')], loadComponent: () => import('./features/admin/event-editor.component').then(m => m.EventEditorComponent) },
      { path: 'organizers', title: 'Organizers · Console', canActivate: [roleGuard('ADMIN')], loadComponent: () => import('./features/admin/organizers.component').then(m => m.OrganizersComponent) },
      { path: 'team', title: 'Team · Console', canActivate: [roleGuard('ADMIN', 'ORGANIZER')], loadComponent: () => import('./features/admin/team.component').then(m => m.TeamComponent) },
      { path: 'complimentary', title: 'Complimentary tickets · Console', canActivate: [roleGuard('EVENT_MANAGER')], loadComponent: () => import('./features/admin/complimentary.component').then(m => m.ComplimentaryComponent) },
      { path: '**', redirectTo: '' }
    ]
  },
  { path: 'staff', loadComponent: () => import('./features/staff/staff.component').then(m => m.StaffComponent), canActivate: [staffGuard] },
  { path: 'staff/events/:eventId/scanner', loadComponent: () => import('./features/scanner/scanner.component').then(m => m.ScannerComponent), canActivate: [staffGuard] },
  { path: '**', redirectTo: 'not-found' }
];
