import { CommonModule, DatePipe, DecimalPipe, isPlatformBrowser } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../core/api/api.service';
import { OperationsDashboard, OperationsLogEntry } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { BrandMarkComponent } from '../shared/brand-mark.component';

interface Incident {
  severity: 'CRITICAL' | 'WARNING';
  title: string;
  detail: string;
  count?: number;
}

/** Dedicated SRE console served only from monitor.neelastack.com by the production edge. */
@Component({
  selector: 'lk-monitor-console',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, RouterLink, BrandMarkComponent],
  template: `
    <div class="monitor-shell">
      <aside class="rail">
        <a routerLink="/monitor" class="brand"><lk-brand-mark label="Monitor" [height]="34" /></a>
        <div class="environment"><span class="env-dot"></span> PRODUCTION · SRE</div>
        <nav aria-label="Monitor navigation">
          <a href="#overview" class="active">Overview</a>
          <a href="#incidents">Incidents</a>
          <a href="#logs">Live logs</a>
          <a href="#recovery">Recovery</a>
          <a href="#runbooks">Runbooks</a>
        </nav>
        <div class="rail-note">
          <span>Business workspace</span>
          <a href="https://events.neelastack.com/admin/operations" rel="noopener noreferrer">Open Events Ops ↗</a>
          <a href="https://events.neelastack.com/" rel="noopener noreferrer">Public site ↗</a>
        </div>
        <button class="signout" type="button" (click)="logout()">Sign out</button>
      </aside>

      <main class="content">
        <header class="topbar">
          <div>
            <div class="eyebrow">Neelastack Platform · SRE Control Plane</div>
            <h1>Production Monitor</h1>
            <p>Infrastructure diagnosis, recovery queues and searchable operational logs. Business workflows remain on Events.</p>
          </div>
          <div class="top-actions">
            <span class="operator">{{ auth.fullName() }}</span>
            <span class="release">v{{ data()?.health?.version || '—' }}</span>
            <span class="live"><i [class.warn]="refreshing()"></i>{{ refreshing() ? 'Refreshing' : 'Live' }}</span>
            <button class="action" type="button" (click)="reload()" [disabled]="loading()">{{ loading() ? 'Loading…' : 'Refresh' }}</button>
          </div>
        </header>

        @if (error()) { <div class="critical-banner"><strong>Monitor data unavailable.</strong><span>{{ error() }}</span><button (click)="reload()">Retry</button></div> }

        @if (data(); as d) {
          <section id="overview" class="hero-grid">
            <article class="hero-card state" [class.degraded]="overallState(d) !== 'HEALTHY'">
              <div class="card-kicker">Platform state</div>
              <div class="state-row"><span class="state-dot"></span><strong>{{ overallState(d) }}</strong></div>
              <span class="state-copy">Based on dependency health and active recovery exceptions.</span>
            </article>
            <article class="hero-card"><div class="card-kicker">Application</div><strong>RUNNING</strong><span>Release v{{ d.health.version }} · {{ d.health.application }}</span></article>
            <article class="hero-card"><div class="card-kicker">PostgreSQL</div><strong [class.down]="d.health.database.status !== 'UP'">{{ d.health.database.status }}</strong><span>{{ d.health.database.latencyMs }} ms · {{ d.health.database.detail }}</span></article>
            <article class="hero-card"><div class="card-kicker">Redis</div><strong [class.down]="d.health.redis.status !== 'UP'">{{ d.health.redis.status }}</strong><span>{{ d.health.redis.latencyMs }} ms · {{ d.health.redis.detail }}</span></article>
          </section>

          <section class="metrics-grid">
            <article><span>Open incidents</span><strong [class.bad-number]="incidents(d).length > 0">{{ incidents(d).length }}</strong><small>derived from fail-closed health + recovery signals</small></article>
            <article><span>Stale payments</span><strong [class.bad-number]="d.health.queues.stalePayments > 0">{{ d.health.queues.stalePayments }}</strong><small>older than 2 minutes</small></article>
            <article><span>Webhook pressure</span><strong [class.bad-number]="d.health.queues.webhookBacklog + d.health.queues.webhookStuck > 0">{{ d.health.queues.webhookBacklog + d.health.queues.webhookStuck }}</strong><small>backlog + stuck</small></article>
            <article><span>Refund queue</span><strong [class.bad-number]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds }}</strong><small>requested / processing</small></article>
            <article><span>Mail failures</span><strong [class.bad-number]="d.health.queues.mailFailed > 0">{{ d.health.queues.mailFailed }}</strong><small>delivery failures</small></article>
            <article><span>Worker tier</span><strong>{{ d.health.workerEnabled ? 'ON' : 'ISOLATED' }}</strong><small>background processing posture</small></article>
          </section>

          <section id="incidents" class="panel">
            <div class="panel-head"><div><div class="eyebrow">SRE queue</div><h2>Incidents &amp; warnings</h2><span class="muted">No destructive action is exposed here. Investigate, correlate and reconcile.</span></div><span class="timestamp">{{ d.generatedAt | date:'HH:mm:ss' }}</span></div>
            @if (!incidents(d).length) {
              <div class="all-clear"><span>✓</span><div><strong>No active operational incidents.</strong><small>Dependency checks and recovery queues are currently within the configured posture.</small></div></div>
            } @else {
              <div class="incident-grid">
                @for (incident of incidents(d); track incident.title) {
                  <article class="incident" [class.critical]="incident.severity === 'CRITICAL'">
                    <div class="incident-head"><span class="severity">{{ incident.severity }}</span>@if (incident.count !== undefined) {<strong>{{ incident.count }}</strong>}</div>
                    <h3>{{ incident.title }}</h3><p>{{ incident.detail }}</p>
                    <button type="button" (click)="focusLogs(incident.title)">Find related logs</button>
                  </article>
                }
              </div>
            }
          </section>

          <section id="recovery" class="two-col">
            <article class="panel">
              <div class="panel-head"><div><div class="eyebrow">Financial integrity</div><h2>Payment recovery</h2></div><span class="muted">authoritative aggregate</span></div>
              <div class="table-list">
                <div><span>Gross captured · 24h</span><strong>₹{{ d.kpis.grossCaptured24hMinor / 100 | number:'1.0-0' }}</strong></div>
                <div><span>Successful payments · 24h</span><strong>{{ d.kpis.successfulPayments24h | number }}</strong></div>
                <div><span>Pending payments</span><strong [class.bad-number]="d.health.queues.pendingPayments > 0">{{ d.health.queues.pendingPayments }}</strong></div>
                <div><span>Provider order recovery</span><strong [class.bad-number]="d.health.queues.providerOrderRecoveryPending > 0">{{ d.health.queues.providerOrderRecoveryPending }}</strong></div>
                <div><span>Pending refunds</span><strong [class.bad-number]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds }}</strong></div>
                <div><span>Refunds completed · 24h</span><strong>₹{{ d.kpis.refunds24hMinor / 100 | number:'1.0-0' }}</strong></div>
              </div>
            </article>
            <article class="panel">
              <div class="panel-head"><div><div class="eyebrow">Background processing</div><h2>Recovery queues</h2></div><span class="muted">zero is healthy</span></div>
              <div class="queue-grid">
                <div><span>Webhook backlog</span><strong [class.bad-number]="d.health.queues.webhookBacklog > 0">{{ d.health.queues.webhookBacklog }}</strong></div>
                <div><span>Webhook stuck</span><strong [class.bad-number]="d.health.queues.webhookStuck > 0">{{ d.health.queues.webhookStuck }}</strong></div>
                <div><span>Reservations expired</span><strong [class.warn-number]="d.health.queues.expiredReservations > 0">{{ d.health.queues.expiredReservations }}</strong></div>
                <div><span>Mail pending</span><strong [class.warn-number]="d.health.queues.mailPending > 0">{{ d.health.queues.mailPending }}</strong></div>
                <div><span>Mail failed</span><strong [class.bad-number]="d.health.queues.mailFailed > 0">{{ d.health.queues.mailFailed }}</strong></div>
                <div><span>Held reservations</span><strong>{{ d.health.queues.heldReservations }}</strong></div>
              </div>
            </article>
          </section>

          <section id="logs" class="panel logs-panel">
            <div class="panel-head logs-head">
              <div><div class="eyebrow">Observability</div><h2>Live logs</h2><span class="muted">Loki-backed, bounded and redacted. Use correlation IDs to join edge → API → business event → recovery activity.</span></div>
              <div class="log-head-actions"><span class="muted">{{ logPaused() ? 'Paused' : 'Polling every 5 seconds' }}</span><button class="action compact" type="button" (click)="toggleLogPause()">{{ logPaused() ? 'Resume' : 'Pause' }}</button><button class="action compact" type="button" (click)="loadLogs()" [disabled]="logLoading()">{{ logLoading() ? 'Loading…' : 'Refresh' }}</button></div>
            </div>
            <div class="log-filters">
              <label>Service<select [value]="logService()" (change)="setLogService($event)"><option value="ALL">All services</option><option value="lakhdatar-backend">Backend</option><option value="lakhdatar-edge">Edge</option><option value="lakhdatar-web">Web / SSR</option><option value="lakhdatar-postgres">PostgreSQL</option><option value="lakhdatar-redis">Redis</option><option value="lakhdatar-rabbitmq">RabbitMQ</option></select></label>
              <label>Level<select [value]="logLevel()" (change)="setLogLevel($event)"><option>ALL</option><option>ERROR</option><option>WARN</option><option>INFO</option><option>DEBUG</option></select></label>
              <label>Window<select [value]="logSince()" (change)="setLogSince($event)"><option value="15">15 min</option><option value="30">30 min</option><option value="60">1 hour</option><option value="360">6 hours</option><option value="1440">24 hours</option></select></label>
              <label class="log-search">Search<input [value]="logSearch()" maxlength="160" placeholder="order number, error code, correlation id…" (input)="setLogSearch($event)" (keyup.enter)="loadLogs()" /></label>
            </div>
            @if (logError()) { <div class="log-alert">{{ logError() }}</div> }
            @if (!logLoading() && !logs().length && !logError()) { <div class="logs-empty">No matching logs were returned for this window.</div> }
            <div class="logs-table-wrap" aria-live="polite">
              <table class="logs-table">
                <thead><tr><th>Time</th><th>Level</th><th>Service</th><th>Event</th><th>Details</th><th>Correlation</th></tr></thead>
                <tbody>
                  @for (entry of logs(); track entry.timestamp + entry.container + entry.message) {
                    <tr [class.log-error]="entry.level === 'ERROR'" [class.log-warn]="entry.level === 'WARN'">
                      <td class="mono">{{ entry.timestamp | date:'dd MMM HH:mm:ss' }}</td>
                      <td><span class="level" [class.error]="entry.level === 'ERROR'" [class.warning]="entry.level === 'WARN'">{{ entry.level }}</span></td>
                      <td><strong>{{ entry.container }}</strong></td>
                      <td class="action-cell"><strong>{{ entry.action }}</strong></td>
                      <td class="message">{{ entry.message }}</td>
                      <td class="mono correlation">{{ entry.correlationId || '—' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <div class="logs-footer"><span>{{ logs().length }} entries · {{ logSince() }} minute window</span><span>Live store: private Loki via authenticated backend BFF</span></div>
          </section>

          <section id="runbooks" class="panel runbooks">
            <div class="panel-head"><div><div class="eyebrow">Operator guidance</div><h2>Safe diagnosis path</h2></div><span class="muted">production-safe workflow</span></div>
            <div class="runbook-grid">
              <article><span class="step">01</span><strong>Confirm the signal</strong><p>Check dependency health and the queue counts above. Do not infer payment loss from a browser result alone.</p></article>
              <article><span class="step">02</span><strong>Correlate the event</strong><p>Search Live logs by correlation ID, order number or provider recovery event to reconstruct the request path.</p></article>
              <article><span class="step">03</span><strong>Reconcile server-side</strong><p>Use the existing payment/recovery workflows. Never rewrite payment status or refund rows directly in PostgreSQL.</p></article>
              <article><span class="step">04</span><strong>Escalate infrastructure</strong><p>Use private Grafana / Prometheus / Loki / Tempo for deep telemetry. Keep those systems off the public internet.</p></article>
            </div>
          </section>

          <footer class="footer"><span>Generated {{ d.generatedAt | date:'d MMM yyyy, HH:mm:ss' }}</span><span>Auto-refresh · 15 seconds</span><span>Monitor is the SRE surface; Events is the business surface.</span></footer>
        } @else if (loading()) {
          <div class="loading"><div class="spinner"></div><strong>Loading secure production telemetry…</strong><span>Only aggregate health data and bounded logs are requested.</span></div>
        }
      </main>
    </div>
  `,
  styles: [`
    :host{display:block;color:#e8ecf1;background:#070a0f;min-height:100vh}.monitor-shell{min-height:100vh;background:radial-gradient(circle at 80% -10%,rgba(61,79,112,.16),transparent 32%),#070a0f;display:grid;grid-template-columns:250px minmax(0,1fr)}.rail{position:sticky;top:0;height:100vh;overflow:auto;padding:22px 16px;display:flex;flex-direction:column;border-right:1px solid rgba(255,255,255,.08);background:rgba(8,11,17,.9);backdrop-filter:blur(16px)}.brand{display:inline-flex;color:#fff;text-decoration:none;padding:8px 6px}.environment{margin:30px 8px 12px;color:#7e8a9a;font-size:9px;font-weight:800;letter-spacing:.14em}.env-dot{display:inline-block;width:6px;height:6px;border-radius:50%;background:#56bf82;margin-right:6px;box-shadow:0 0 0 4px rgba(86,191,130,.1)}.rail nav{display:grid;gap:4px}.rail nav a{padding:11px 12px;border-radius:9px;color:#8994a5;text-decoration:none;font-size:12px}.rail nav a:hover,.rail nav a.active{background:rgba(255,255,255,.06);color:#fff}.rail-note{margin-top:auto;padding:14px 10px;border-top:1px solid rgba(255,255,255,.07);display:grid;gap:9px}.rail-note span{color:#5f6a79;font-size:8px;text-transform:uppercase;letter-spacing:.12em}.rail-note a{color:#919cac;text-decoration:none;font-size:10px}.rail-note a:hover{color:#fff}.signout{margin:13px 10px 0;border:0;background:transparent;color:#6f7a89;text-align:left;padding:0;font:inherit;font-size:10px;cursor:pointer}.signout:hover{color:#fff}.content{padding:34px clamp(16px,4vw,52px) 60px;min-width:0}.topbar{display:flex;justify-content:space-between;gap:30px;align-items:flex-end;margin-bottom:18px}.eyebrow,.card-kicker{font-size:8px;text-transform:uppercase;letter-spacing:.16em;color:#6f7b8c;font-weight:800}.topbar h1{margin:9px 0 7px;font-size:40px;letter-spacing:-.045em}.topbar p{margin:0;color:#7f8a99;font-size:12px;max-width:760px;line-height:1.55}.top-actions{display:flex;align-items:center;gap:9px;flex-wrap:wrap;justify-content:flex-end}.operator{color:#a8b0bd;font-size:10px}.release{border:1px solid rgba(255,255,255,.09);background:rgba(255,255,255,.03);padding:8px 10px;border-radius:999px;font-size:9px}.live{display:inline-flex;align-items:center;gap:6px;color:#91a09e;font-size:9px}.live i{width:7px;height:7px;border-radius:50%;background:#53ba7e;box-shadow:0 0 0 4px rgba(83,186,126,.1)}.live i.warn{background:#d1a245}.action{border:1px solid rgba(255,255,255,.12);background:#fff;color:#0a0d12;border-radius:9px;padding:9px 12px;font:inherit;font-size:10px;font-weight:800;cursor:pointer}.action:disabled{opacity:.5;cursor:not-allowed}.action.compact{padding:8px 10px}.critical-banner{margin-bottom:12px;display:flex;align-items:center;gap:10px;padding:12px 14px;border:1px solid rgba(209,105,105,.3);background:rgba(86,25,25,.2);border-radius:11px;color:#e4b7b7;font-size:10px}.critical-banner span{color:#b18f8f}.critical-banner button{margin-left:auto;border:0;background:transparent;color:#fff;cursor:pointer;font:inherit;font-size:9px}.hero-grid{display:grid;grid-template-columns:1.3fr 1fr 1fr 1fr;gap:9px;margin-bottom:9px}.hero-card,.metrics-grid article,.panel{border:1px solid rgba(255,255,255,.08);background:rgba(15,19,27,.78);border-radius:14px}.hero-card{padding:16px;min-height:118px;display:flex;flex-direction:column;justify-content:space-between}.hero-card>strong{font-size:24px;letter-spacing:-.04em}.hero-card>span:last-child{color:#737f8e;font-size:9px;line-height:1.5}.hero-card .down{color:#df8686}.state-row{display:flex;align-items:center;gap:9px;margin:8px 0}.state-row strong{font-size:23px;color:#66ce90}.state.degraded .state-row strong{color:#e0aa4f}.state-dot{width:9px;height:9px;border-radius:50%;background:#57bd82;box-shadow:0 0 0 5px rgba(87,189,130,.1)}.state.degraded .state-dot{background:#d6a149;box-shadow:0 0 0 5px rgba(214,161,73,.1)}.state-copy{color:#6f7b8b;font-size:9px;line-height:1.5}.metrics-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:9px;margin-bottom:9px}.metrics-grid article{padding:13px}.metrics-grid span{display:block;color:#758192;font-size:8px;text-transform:uppercase;letter-spacing:.12em}.metrics-grid strong{display:block;font-size:22px;margin:8px 0 3px}.metrics-grid small{display:block;color:#606b7b;font-size:8px;line-height:1.4}.bad-number{color:#e08b8b!important}.warn-number{color:#d9b15e!important}.panel{padding:17px;margin-bottom:9px}.panel-head{display:flex;justify-content:space-between;align-items:flex-end;gap:14px;margin-bottom:14px}.panel-head h2{margin:6px 0 3px;font-size:18px;letter-spacing:-.02em}.muted{color:#647080;font-size:9px;line-height:1.5}.timestamp{color:#7b8796;font-size:9px;font-variant-numeric:tabular-nums}.incident-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px}.incident{border:1px solid rgba(209,162,75,.18);background:rgba(70,55,25,.14);border-radius:11px;padding:13px}.incident.critical{border-color:rgba(213,95,95,.28);background:rgba(72,23,23,.14)}.incident-head{display:flex;justify-content:space-between;align-items:center}.severity{font-size:7px;font-weight:900;letter-spacing:.12em;color:#dcb75e}.incident.critical .severity{color:#dc8d8d}.incident-head strong{font-size:17px}.incident h3{margin:10px 0 5px;font-size:12px}.incident p{margin:0;min-height:47px;color:#7e8997;font-size:9px;line-height:1.5}.incident button{margin-top:11px;border:0;background:transparent;color:#a9b4c2;padding:0;font:inherit;font-size:8px;cursor:pointer;text-decoration:underline}.all-clear{border:1px solid rgba(83,186,126,.16);background:rgba(38,79,57,.11);border-radius:11px;padding:16px;display:flex;align-items:center;gap:12px}.all-clear>span{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:rgba(83,186,126,.12);color:#66ce90}.all-clear strong,.all-clear small{display:block}.all-clear strong{font-size:11px}.all-clear small{margin-top:3px;color:#758192;font-size:9px}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:9px}.table-list{display:grid}.table-list>div{display:flex;justify-content:space-between;gap:14px;padding:10px 0;border-bottom:1px solid rgba(255,255,255,.06);font-size:10px}.table-list>div:last-child{border-bottom:0}.table-list span{color:#7c8795}.table-list strong{font-size:11px}.queue-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}.queue-grid>div{padding:12px;border:1px solid rgba(255,255,255,.06);border-radius:9px;background:rgba(255,255,255,.018)}.queue-grid span{display:block;color:#738091;font-size:8px}.queue-grid strong{display:block;font-size:21px;margin-top:7px}.logs-panel{scroll-margin-top:20px}.logs-head{align-items:flex-start}.log-head-actions{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.log-filters{display:grid;grid-template-columns:150px 130px 130px minmax(200px,1fr);gap:7px;margin-bottom:9px}.log-filters label{display:grid;gap:5px;color:#788494;font-size:7px;font-weight:800;text-transform:uppercase;letter-spacing:.1em}.log-filters select,.log-filters input{height:38px;border:1px solid rgba(255,255,255,.1);border-radius:8px;background:#0c1118;color:#dbe1e9;padding:0 9px;outline:none;font:inherit}.log-filters select:focus,.log-filters input:focus{border-color:#8c9ab0;box-shadow:0 0 0 3px rgba(140,154,176,.08)}.logs-table-wrap{overflow:auto;max-height:540px;border:1px solid rgba(255,255,255,.07);border-radius:10px}.logs-table{width:100%;min-width:1040px;border-collapse:collapse;font-size:9px}.logs-table th{position:sticky;top:0;background:#0d121a;color:#738091;text-align:left;text-transform:uppercase;letter-spacing:.1em;font-size:7px;padding:9px;border-bottom:1px solid rgba(255,255,255,.08)}.logs-table td{padding:9px;border-bottom:1px solid rgba(255,255,255,.05);vertical-align:top}.logs-table tr:last-child td{border-bottom:0}.logs-table .action-cell{min-width:170px;max-width:260px}.logs-table .message{max-width:520px;white-space:pre-wrap;word-break:break-word;color:#aab4c1}.logs-table .mono{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;color:#768291}.logs-table .correlation{max-width:145px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.log-error td{background:rgba(90,35,35,.1)}.log-warn td{background:rgba(88,69,25,.08)}.level{display:inline-flex;padding:4px 7px;border-radius:99px;background:rgba(255,255,255,.06);font-size:7px;font-weight:800}.level.warning{background:rgba(196,150,61,.12);color:#d7b260}.level.error{background:rgba(205,89,89,.13);color:#df9292}.log-alert{margin:8px 0;padding:9px;border:1px solid rgba(209,105,105,.22);background:rgba(86,25,25,.13);border-radius:8px;color:#d9a4a4;font-size:9px}.logs-empty{padding:34px;text-align:center;color:#647080;font-size:9px}.logs-footer,.footer{display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;color:#5d6876;font-size:8px;padding-top:9px}.runbook-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.runbook-grid article{border:1px solid rgba(255,255,255,.06);border-radius:10px;padding:13px}.step{display:inline-flex;width:22px;height:22px;align-items:center;justify-content:center;border-radius:6px;background:rgba(255,255,255,.05);color:#aeb8c5;font-size:7px;font-weight:800}.runbook-grid strong{display:block;margin:11px 0 5px;font-size:11px}.runbook-grid p{margin:0;color:#74808e;font-size:9px;line-height:1.55}.loading{min-height:300px;display:grid;place-content:center;text-align:center;gap:8px;color:#758091}.spinner{width:25px;height:25px;border:3px solid rgba(255,255,255,.08);border-top-color:#d2b05d;border-radius:50%;margin:0 auto;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:1150px){.hero-grid{grid-template-columns:1fr 1fr}.metrics-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.incident-grid{grid-template-columns:1fr 1fr}.runbook-grid{grid-template-columns:1fr 1fr}.topbar{align-items:flex-start;flex-direction:column}.top-actions{justify-content:flex-start}}
    @media(max-width:820px){.monitor-shell{display:block}.rail{position:static;height:auto;display:block;padding:14px}.rail nav{display:flex;overflow:auto;margin-top:8px}.rail nav a{flex:0 0 auto}.rail-note{display:none}.signout{margin:9px 0 0}.content{padding:22px 14px 42px}.hero-grid,.two-col{grid-template-columns:1fr}.metrics-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.log-filters{grid-template-columns:1fr 1fr}.incident-grid,.runbook-grid{grid-template-columns:1fr}}
    @media(max-width:560px){.topbar h1{font-size:32px}.metrics-grid,.log-filters{grid-template-columns:1fr}.hero-grid{grid-template-columns:1fr}.panel{padding:14px}.panel-head{align-items:flex-start;flex-direction:column}.log-head-actions{justify-content:flex-start}}
  `]
})
export class MonitorConsoleComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly platformId = inject(PLATFORM_ID);
  readonly data = signal<OperationsDashboard | undefined>(undefined);
  readonly loading = signal(false);
  readonly refreshing = signal(false);
  readonly error = signal('');
  readonly logs = signal<OperationsLogEntry[]>([]);
  readonly logLoading = signal(false);
  readonly logError = signal('');
  readonly logsOpen = signal(true);
  readonly logService = signal('ALL');
  readonly logLevel = signal('ALL');
  readonly logSearch = signal('');
  readonly logSince = signal(30);
  readonly logPaused = signal(false);
  private timer?: ReturnType<typeof setInterval>;
  private logTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.reload();
    this.loadLogs();
    if (isPlatformBrowser(this.platformId)) {
      this.timer = setInterval(() => this.refreshSilently(), 15000);
      this.logTimer = setInterval(() => { if (!this.logPaused()) this.loadLogs(); }, 5000);
    }
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    if (this.logTimer) clearInterval(this.logTimer);
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.operationsDashboard().subscribe({
      next: value => { this.data.set(value); this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'Production telemetry could not be loaded.'); }
    });
  }

  private refreshSilently(): void {
    if (this.loading() || this.refreshing()) return;
    this.refreshing.set(true);
    this.api.operationsDashboard().subscribe({ next: value => { this.data.set(value); this.refreshing.set(false); }, error: () => this.refreshing.set(false) });
  }

  overallState(d: OperationsDashboard): 'HEALTHY' | 'DEGRADED' {
    const q = d.health.queues;
    return d.health.database.status !== 'UP' || d.health.redis.status !== 'UP' || q.stalePayments > 0 || q.providerOrderRecoveryPending > 0 || q.pendingRefunds > 0 || q.webhookBacklog > 0 || q.webhookStuck > 0 || q.mailFailed > 0 ? 'DEGRADED' : 'HEALTHY';
  }

  incidents(d: OperationsDashboard): Incident[] {
    const q = d.health.queues;
    const out: Incident[] = [];
    if (d.health.database.status !== 'UP') out.push({ severity:'CRITICAL', title:'PostgreSQL unavailable', detail:'Primary database health check is not UP. Treat the application as impaired until dependency health recovers.' });
    if (d.health.redis.status !== 'UP') out.push({ severity:'CRITICAL', title:'Redis unavailable', detail:'Cache/rate-limit dependency health is not UP. Check the Redis container and network before taking business action.' });
    if (q.stalePayments > 0) out.push({ severity:'WARNING', title:'Stale payments need reconciliation', detail:'Payments remain in an in-flight state beyond the normal two-minute signal window. Correlate provider state and do not edit rows manually.', count:q.stalePayments });
    if (q.providerOrderRecoveryPending > 0) out.push({ severity:'WARNING', title:'Provider order recovery pending', detail:'External provider order discovery/recovery remains queued. Use the authoritative provider reconciliation path.', count:q.providerOrderRecoveryPending });
    if (q.pendingRefunds > 0) out.push({ severity:'WARNING', title:'Refund queue is non-zero', detail:'Refund requests remain in requested/processing states. Verify provider status and ledger consistency before escalation.', count:q.pendingRefunds });
    if (q.webhookStuck > 0) out.push({ severity:'WARNING', title:'Webhook workers appear stuck', detail:'Payment webhook work has exceeded the configured processing threshold and should be investigated with correlation logs.', count:q.webhookStuck });
    if (q.webhookBacklog > 0) out.push({ severity:'WARNING', title:'Webhook backlog detected', detail:'Unprocessed payment webhooks are waiting beyond the two-minute threshold.', count:q.webhookBacklog });
    if (q.mailFailed > 0) out.push({ severity:'WARNING', title:'Ticket mail failures', detail:'Ticket delivery jobs are in FAILED state. Investigate SMTP/provider logs without exposing attendee data.', count:q.mailFailed });
    return out;
  }

  focusLogs(title: string): void {
    const query = title.toLowerCase().includes('payment') ? 'payment' : title.toLowerCase().includes('refund') ? 'refund' : title.toLowerCase().includes('webhook') ? 'webhook' : title.toLowerCase().includes('mail') ? 'mail' : title.toLowerCase().includes('redis') ? 'redis' : title.toLowerCase().includes('postgres') ? 'database' : '';
    this.logSearch.set(query);
    this.logLevel.set(title.toLowerCase().includes('unavailable') ? 'ERROR' : 'WARN');
    document.getElementById('logs')?.scrollIntoView({ behavior:'smooth', block:'start' });
    this.loadLogs();
  }

  toggleLogPause(): void { this.logPaused.update(v => !v); if (!this.logPaused()) this.loadLogs(); }
  setLogService(e: Event): void { this.logService.set((e.target as HTMLSelectElement).value); this.loadLogs(); }
  setLogLevel(e: Event): void { this.logLevel.set((e.target as HTMLSelectElement).value); this.loadLogs(); }
  setLogSince(e: Event): void { this.logSince.set(Number((e.target as HTMLSelectElement).value)); this.loadLogs(); }
  setLogSearch(e: Event): void { this.logSearch.set((e.target as HTMLInputElement).value); }

  loadLogs(): void {
    this.logLoading.set(true);
    this.logError.set('');
    this.api.operationsLogs({
      service: this.logService() === 'ALL' ? undefined : this.logService(),
      level: this.logLevel() === 'ALL' ? undefined : this.logLevel(),
      q: this.logSearch().trim() || undefined,
      limit: 160,
      sinceMinutes: this.logSince()
    }).subscribe({
      next: page => { this.logs.set(page.items); this.logLoading.set(false); if (!page.available) this.logError.set('The private log store is currently unavailable; the primary monitor remains available.'); },
      error: err => { this.logLoading.set(false); this.logError.set(err?.error?.message || 'Live logs are temporarily unavailable.'); }
    });
  }

  logout(): void {
    const done = () => { location.href = '/login'; };
    this.auth.logout().subscribe({ complete: done, error: done });
  }
}
