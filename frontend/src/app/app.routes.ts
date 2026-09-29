import { Routes } from '@angular/router';
import { HomeComponent } from './features/home/home.component';
import { adminGuard, staffGuard } from './core/auth/auth.guard';

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
  { path: 'not-found', loadComponent: () => import('./shared/not-found.component').then(m => m.NotFoundComponent) },
  { path: 'admin', loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent), canActivate: [adminGuard] },
  { path: 'staff', loadComponent: () => import('./features/staff/staff.component').then(m => m.StaffComponent), canActivate: [staffGuard] },
  { path: 'staff/events/:eventId/scanner', loadComponent: () => import('./features/scanner/scanner.component').then(m => m.ScannerComponent), canActivate: [staffGuard] },
  { path: '**', redirectTo: 'not-found' }
];
