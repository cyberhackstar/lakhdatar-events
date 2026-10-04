import { InjectionToken } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Base URL of the backend API. In the browser this is the same-origin relative path (proxied by the edge).
 * During server-side rendering it is overridden with the internal backend address (see app.config.server.ts).
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => {
    // Production browser traffic must stay same-origin so Cloudflare/Nginx can proxy /api to the backend.
    // SSR overrides this token with the private Docker URL in app.config.server.ts.
    if (typeof window !== 'undefined' && environment.production) return '/api/v1';
    return environment.apiBaseUrl;
  }
});
