import { CommonModule, DatePipe, DecimalPipe, isPlatformBrowser } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api/api.service';
import { OperationsDashboard } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';

@Component({
  selector: 'lk-operations-center',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe],
  template: `
    <div class="hero">
      <div>
        <div class="eyebrow">Neelastack Events · SRE</div>
        <h1 class="title">Platform Operations Center</h1>
        <p class="sub">Read-only production command view for availability, payment integrity, event activity and recovery queues.</p>
      </div>
      <div class="hero-actions">
        <span class="live"><i [class.warn]="refreshing()"></i>{{ refreshing() ? 'Refreshing' : 'Live snapshot' }}</span>
        <button class="a-btn sm" (click)="reload()" [disabled]="loading()">{{ loading() ? 'Loading…' : 'Refresh now' }}</button>
      </div>
    </div>

    @if (error()) { <div class="alert" role="alert"><strong>Monitoring unavailable.</strong> {{ error() }}</div> }

    @if (data(); as d) {
      <section class="statusbar">
        <div class="status-main" [class.degraded]="overallState(d) !== 'HEALTHY'">
          <span class="status-dot"></span>
          <div><small>Platform state</small><strong>{{ overallState(d) }}</strong></div>
        </div>
        <div><small>Release</small><strong>v{{ d.health.version }}</strong></div>
        <div><small>Worker tier</small><strong>{{ d.health.workerEnabled ? 'ENABLED' : 'API-ONLY' }}</strong></div>
        <div><small>Snapshot</small><strong>{{ d.generatedAt | date:'HH:mm:ss' }}</strong></div>
      </section>

      <section class="kpis">
        <article class="kpi primary"><span>Gross captured · 24h</span><strong>₹{{ d.kpis.grossCaptured24hMinor / 100 | number:'1.0-0' }}</strong><small>Captured / completed payments</small></article>
        <article class="kpi"><span>Orders · 24h</span><strong>{{ d.kpis.orders24h | number }}</strong><small>{{ d.kpis.successfulPayments24h | number }} successful payments</small></article>
        <article class="kpi"><span>Tickets issued · 24h</span><strong>{{ d.kpis.ticketsIssued24h | number }}</strong><small>{{ d.kpis.checkIns24h | number }} accepted check-ins</small></article>
        <article class="kpi"><span>Active events</span><strong>{{ d.kpis.activeEvents | number }}</strong><small>{{ d.kpis.upcomingEvents | number }} upcoming</small></article>
      </section>

      <div class="columns">
        <section class="panel dependencies">
          <div class="panel-head"><div><div class="eyebrow">Dependencies</div><h2>Core services</h2></div><span class="muted">Fail-closed health</span></div>
          <div class="dependency-grid">
            <div class="dependency"><span class="dot" [class.up]="d.health.database.status==='UP'"></span><div><b>PostgreSQL</b><small>{{ d.health.database.status }}</small></div><strong>{{ d.health.database.latencyMs }}ms</strong></div>
            <div class="dependency"><span class="dot" [class.up]="d.health.redis.status==='UP'"></span><div><b>Redis</b><small>{{ d.health.redis.status }}</small></div><strong>{{ d.health.redis.latencyMs }}ms</strong></div>
            <div class="dependency"><span class="dot up"></span><div><b>Application</b><small>API responding</small></div><strong>v{{ d.health.version }}</strong></div>
            <div class="dependency"><span class="dot" [class.up]="d.health.workerEnabled"></span><div><b>Worker tier</b><small>{{ d.health.workerEnabled ? 'ENABLED' : 'API-ONLY' }}</small></div><strong>{{ d.health.workerEnabled ? 'READY' : 'ISOLATED' }}</strong></div>
          </div>
        </section>

        <section class="panel integrity">
          <div class="panel-head"><div><div class="eyebrow">Financial integrity</div><h2>Payment signals</h2></div><span class="muted">24 hour window</span></div>
          <div class="signal-list">
            <div><span>Successful payments</span><b>{{ d.kpis.successfulPayments24h | number }}</b></div>
            <div><span>Failed / cancelled</span><b [class.bad]="d.kpis.failedPayments24h > 0">{{ d.kpis.failedPayments24h | number }}</b></div>
            <div><span>Pending payments</span><b [class.bad]="d.health.queues.pendingPayments > 0">{{ d.health.queues.pendingPayments | number }}</b></div>
            <div><span>Stale payments &gt;2m</span><b [class.bad]="d.health.queues.stalePayments > 0">{{ d.health.queues.stalePayments | number }}</b></div>
            <div><span>Provider recovery</span><b [class.bad]="d.health.queues.providerOrderRecoveryPending > 0">{{ d.health.queues.providerOrderRecoveryPending | number }}</b></div>
            <div><span>Refunds pending</span><b [class.bad]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds | number }}</b></div>
          </div>
        </section>
      </div>

      <section class="panel queue-panel">
        <div class="panel-head"><div><div class="eyebrow">Recovery &amp; event-day operations</div><h2>Queue posture</h2></div><span class="muted">Zero is healthy for recovery queues</span></div>
        <div class="queue-grid">
          <div class="queue"><span>Webhook backlog</span><strong [class.bad]="d.health.queues.webhookBacklog > 0">{{ d.health.queues.webhookBacklog }}</strong><small>older than 2 minutes</small></div>
          <div class="queue"><span>Webhook stuck</span><strong [class.bad]="d.health.queues.webhookStuck > 0">{{ d.health.queues.webhookStuck }}</strong><small>processing &gt;5 minutes</small></div>
          <div class="queue"><span>Refund queue</span><strong [class.bad]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds }}</strong><small>requested / processing</small></div>
          <div class="queue"><span>Reservation cleanup</span><strong [class.warn]="d.health.queues.expiredReservations > 0">{{ d.health.queues.expiredReservations }}</strong><small>expired holds</small></div>
          <div class="queue"><span>Mail pending</span><strong [class.warn]="d.health.queues.mailPending > 0">{{ d.health.queues.mailPending }}</strong><small>pending / processing</small></div>
          <div class="queue"><span>Mail failed</span><strong [class.bad]="d.health.queues.mailFailed > 0">{{ d.health.queues.mailFailed }}</strong><small>requires recovery</small></div>
        </div>
      </section>

      <section class="panel event-strip">
        <div><div class="eyebrow">Event-day pulse</div><h2>Live operating context</h2></div>
        <div class="pulse"><span class="pulse-value">{{ d.kpis.activeEvents | number }}</span><span>active events</span></div>
        <div class="pulse"><span class="pulse-value">{{ d.health.queues.heldReservations | number }}</span><span>held reservations</span></div>
        <div class="pulse"><span class="pulse-value">{{ d.kpis.checkIns24h | number }}</span><span>accepted check-ins · 24h</span></div>
        <div class="pulse"><span class="pulse-value">{{ d.kpis.refunds24hMinor / 100 | number:'1.0-0' }}</span><span>refunds · ₹ · 24h</span></div>
      </section>

      <footer class="ops-footer">
        <span>Last generated {{ d.generatedAt | date:'d MMM yyyy, HH:mm:ss' }}</span>
        <span>Auto-refresh every 15 seconds</span>
        <span>Deep infrastructure telemetry remains private in Grafana / Prometheus / Loki / Tempo.</span>
      </footer>
    } @else if (loading()) {
      <div class="panel loading-panel"><div class="loader"></div><strong>Loading secure operations snapshot…</strong><span>Only aggregate operator metrics are requested.</span></div>
    }
  `,
  styles: [ADMIN_UI_STYLES, `
    :host{display:block}.hero{display:flex;justify-content:space-between;gap:24px;align-items:flex-end;margin-bottom:18px}.hero-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.live{display:inline-flex;align-items:center;gap:7px;font-size:11px;color:#7b7380}.live i{width:7px;height:7px;border-radius:50%;background:#52a879;box-shadow:0 0 0 4px rgba(82,168,121,.12)}.live i.warn{background:#d7a349}.statusbar{display:grid;grid-template-columns:minmax(240px,2fr) repeat(3,1fr);gap:1px;background:#ded8d0;border:1px solid #ded8d0;border-radius:16px;overflow:hidden;margin-bottom:12px}.statusbar>div{background:#fff;padding:15px 18px}.statusbar small,.kpi span,.queue span{display:block;color:#8b828d;font-size:9px;text-transform:uppercase;letter-spacing:.1em}.statusbar strong{display:block;margin-top:5px;font-size:13px}.status-main{display:flex;align-items:center;gap:12px}.status-main strong{font-size:16px;color:#39734d}.status-main.degraded strong{color:#9a4e36}.status-dot{width:10px;height:10px;border-radius:50%;background:#52a879;box-shadow:0 0 0 5px rgba(82,168,121,.12)}.degraded .status-dot{background:#d7a349;box-shadow:0 0 0 5px rgba(215,163,73,.12)}.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:12px}.kpi{background:#fff;border:1px solid var(--line);border-radius:15px;padding:18px}.kpi.primary{background:#151019;color:#fff;border-color:#151019}.kpi span{color:#8b828d}.kpi.primary span{color:#aaa1ae}.kpi strong{display:block;font-size:28px;letter-spacing:-.04em;margin:12px 0 5px}.kpi small{color:#9a919d;font-size:10px}.kpi.primary small{color:#aaa1ae}.columns{display:grid;grid-template-columns:1.1fr .9fr;gap:12px;margin-bottom:12px}.panel{background:#fff;border:1px solid var(--line);border-radius:15px;padding:20px}.panel-head{display:flex;justify-content:space-between;align-items:flex-end;gap:12px;margin-bottom:16px}.panel h2{font-size:20px;margin:5px 0 0}.muted{font-size:10px;color:#9a919d}.dependency-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.dependency{display:grid;grid-template-columns:10px minmax(0,1fr) auto;gap:10px;align-items:center;border:1px solid #eee9e3;border-radius:12px;padding:13px}.dependency .dot{width:8px;height:8px;border-radius:50%;background:#d96b6b}.dependency .dot.up{background:#52a879}.dependency b,.dependency small{display:block}.dependency b{font-size:12px}.dependency small{font-size:9px;color:#8b828d;margin-top:2px}.dependency>strong{font-size:10px;color:#6f6573}.signal-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.signal-list>div{display:flex;justify-content:space-between;gap:10px;padding:12px 13px;background:#faf8f5;border-radius:11px}.signal-list span{font-size:10px;color:#766e79}.signal-list b{font-size:13px}.bad{color:#a13f3f!important}.warn{color:#a36b22!important}.queue-panel{margin-bottom:12px}.queue-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:8px}.queue{padding:14px;background:#faf8f5;border:1px solid #eee9e3;border-radius:12px}.queue strong{display:block;font-size:22px;margin:8px 0 2px}.queue small{display:block;color:#8b828d;font-size:9px}.event-strip{display:grid;grid-template-columns:2fr repeat(4,1fr);gap:12px;align-items:center}.event-strip h2{margin-top:5px}.pulse{border-left:1px solid #eee9e3;padding-left:14px}.pulse-value{display:block;font-size:22px;font-weight:750}.pulse span:last-child{display:block;font-size:9px;color:#8b828d;margin-top:3px}.ops-footer{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;color:#918894;font-size:9px;padding:14px 2px}.alert{margin-bottom:12px;padding:12px 14px;background:#fff0f0;border:1px solid #efd0d0;border-radius:12px;color:#8c4141;font-size:12px}.loading-panel{min-height:240px;display:grid;place-content:center;text-align:center;gap:8px;color:#6f6573}.loader{width:24px;height:24px;border:3px solid #e8e2da;border-top-color:#b58637;border-radius:50%;margin:0 auto;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:1050px){.kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.columns{grid-template-columns:1fr}.queue-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.event-strip{grid-template-columns:repeat(2,minmax(0,1fr))}.event-strip>div:first-child{grid-column:1/-1}}
    @media(max-width:680px){.hero{align-items:flex-start;flex-direction:column}.statusbar{grid-template-columns:repeat(2,minmax(0,1fr))}.status-main{grid-column:1/-1}.kpis{grid-template-columns:1fr}.dependency-grid,.signal-list,.queue-grid{grid-template-columns:1fr}.event-strip{grid-template-columns:1fr}.pulse{border-left:0;border-top:1px solid #eee9e3;padding:12px 0 0}.ops-footer{display:grid}}
  `]
})
export class OperationsCenterComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly platformId = inject(PLATFORM_ID);
  readonly data = signal<OperationsDashboard | undefined>(undefined);
  readonly loading = signal(false);
  readonly refreshing = signal(false);
  readonly error = signal('');
  private timer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.reload();
    if (isPlatformBrowser(this.platformId)) this.timer = setInterval(() => this.refreshSilently(), 15000);
  }

  ngOnDestroy(): void { if (this.timer) clearInterval(this.timer); }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.operationsDashboard().subscribe({
      next: value => { this.data.set(value); this.loading.set(false); },
      error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'The secure operations snapshot could not be loaded.'); }
    });
  }

  private refreshSilently(): void {
    if (this.loading() || this.refreshing()) return;
    this.refreshing.set(true);
    this.api.operationsDashboard().subscribe({
      next: value => { this.data.set(value); this.refreshing.set(false); },
      error: () => this.refreshing.set(false)
    });
  }

  overallState(d: OperationsDashboard): 'HEALTHY' | 'DEGRADED' {
    return d.health.database.status === 'UP' && d.health.redis.status === 'UP' &&
      d.health.queues.stalePayments === 0 && d.health.queues.webhookStuck === 0 &&
      d.health.queues.pendingRefunds === 0 ? 'HEALTHY' : 'DEGRADED';
  }
}
