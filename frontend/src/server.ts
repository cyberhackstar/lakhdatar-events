import { AngularNodeAppEngine, createNodeRequestHandler, isMainModule, writeResponseToNodeResponse } from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');
const app = express();
const angularApp = new AngularNodeAppEngine();

app.disable('x-powered-by');
app.set('trust proxy', 1);

const allowedHosts = new Set((process.env['NG_ALLOWED_HOSTS'] || '')
  .split(',').map(v => v.trim().toLowerCase()).filter(Boolean));

app.use((req, res, next) => {
  if (req.path === '/healthz' || allowedHosts.size === 0) return next();
  const host = req.hostname.toLowerCase();
  if (allowedHosts.has(host)) return next();
  res.status(400).type('text/plain').send('Invalid host');
});

// Liveness for container orchestration. Does not touch the backend on purpose.
app.get('/healthz', (_req, res) => { res.status(200).type('text/plain').send('ok'); });

// Un-hashed brand assets (neelastack-logo.png, og-default.png) must be replaceable without waiting a year for caches to expire.
app.use('/assets', express.static(join(browserDistFolder, 'assets'), { maxAge: '10m', index: false, redirect: false }));
// A missing brand asset must be an honest 404. Without this it fell through to the Angular router, whose catch-all
// redirects to /not-found (HTTP 302 + an HTML page), so the logo rendered as a broken image and every page view paid for an extra SSR render.
app.use('/assets', (_req, res) => { res.status(404).type('text/plain').set('Cache-Control', 'no-store').send('Not found'); });

// Hashed build assets are immutable.
app.use(express.static(browserDistFolder, { maxAge: '1y', immutable: true, index: false, redirect: false }));

const PRIVATE_PREFIXES = ['/checkout', '/payment', '/ticket', '/recover', '/login', '/setup', '/admin', '/staff'];

app.use((req, res, next) => {
  const isPrivate = PRIVATE_PREFIXES.some(p => req.path === p || req.path.startsWith(p + '/'));
  if (isPrivate) {
    // Customer, payment and operator pages must never be cached or indexed by any intermediary.
    res.setHeader('Cache-Control', 'no-store');
    res.setHeader('X-Robots-Tag', 'noindex, nofollow');
  } else {
    res.setHeader('Cache-Control', 'public, max-age=0, s-maxage=30, stale-while-revalidate=60');
  }
  angularApp
    .handle(req)
    .then(response => (response ? writeResponseToNodeResponse(response, res) : next()))
    .catch(next);
});

// Never leak stack traces to customers.
app.use((err: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error('SSR error', err instanceof Error ? err.message : err);
  res.status(500).type('text/plain').send('Something went wrong. Please try again shortly.');
});

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  const port = Number(process.env['PORT'] || 3000);
  const server = app.listen(port, '0.0.0.0', () => console.log(`SSR server listening on :${port}`));
  let shuttingDown = false;
  const shutdown = (signal: string) => {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log(`SSR graceful shutdown requested by ${signal}`);
    const force = setTimeout(() => {
      console.error('SSR graceful shutdown timeout exceeded');
      process.exit(1);
    }, 10000);
    force.unref();
    server.close(() => {
      clearTimeout(force);
      process.exit(0);
    });
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}

export const reqHandler = createNodeRequestHandler(app);
