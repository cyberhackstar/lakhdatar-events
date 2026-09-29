import { ApplicationConfig, mergeApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { API_BASE_URL } from './core/api/api.tokens';

// Server-side rendering talks to the backend over the private Docker network, never through the public URL.
const internalApi = (typeof process !== 'undefined' && process.env['SSR_API_BASE_URL']) || 'http://backend:8080/api/v1';

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    { provide: API_BASE_URL, useValue: internalApi }
  ]
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
