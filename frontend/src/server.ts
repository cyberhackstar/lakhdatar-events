import { AngularNodeAppEngine, createNodeRequestHandler, isMainModule, writeResponseToNodeResponse } from '@angular/ssr/node';
import express from 'express';
import { join } from 'node:path';

const browserDistFolder = join(import.meta.dirname, '../browser');
const app = express();
const angularApp = new AngularNodeAppEngine();

app.disable('x-powered-by');
app.set('trust proxy', true);

// Liveness for container orchestration. Does not touch the backend on purpose.
app.get('/healthz', (_req, res) => { res.status(200).type('text/plain').send('ok'); });

// Hashed build assets are immutable.
app.use(express.static(browserDistFolder, { maxAge: '1y', immutable: true, index: false, redirect: false }));

const PRIVATE_PREFIXES = ['/checkout', '/payment', '/ticket', '/recover', '/login', '/admin', '/staff'];

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
  app.listen(port, '0.0.0.0', () => console.log(`SSR server listening on :${port}`));
}

export const reqHandler = createNodeRequestHandler(app);
