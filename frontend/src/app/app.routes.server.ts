import { RenderMode, ServerRoute } from '@angular/ssr';

/**
 * Public, indexable pages are server-rendered for SEO and fast first paint.
 * Everything private (checkout, payment, tickets, admin, staff, auth) stays client-rendered so no
 * customer or operator data is ever produced on, or cached from, the server render path.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Server },
  { path: 'events', renderMode: RenderMode.Server },
  { path: 'events/:slug', renderMode: RenderMode.Server },
  { path: 'privacy', renderMode: RenderMode.Server },
  { path: 'terms', renderMode: RenderMode.Server },
  { path: 'refund-policy', renderMode: RenderMode.Server },
  { path: '**', renderMode: RenderMode.Client }
];
