import { CommonModule, DatePipe, DecimalPipe, isPlatformBrowser } from '@angular/common';
import { Component, OnDestroy, OnInit, PLATFORM_ID, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { OperationsDashboard } from '../../core/api/api.models';

/** Business-facing operations view. Deep platform diagnostics live on monitor.neelastack.com. */
@Component({
  selector: 'lk-operations-center',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, RouterLink],
  template: `
    <div class="hero">
      <div>
        <div class="eyebrow">Neelastack Events · Business Ops</div>
        <h1 class="title">Business Operations</h1>
        <p class="sub">Event-day sales, ticket activity and financial recovery signals for organizers. Infrastructure diagnosis is intentionally separated into the dedicated Monitor console.</p>
      </div>
      <div class="hero-actions">
        <span class="live"><i [class.warn]="refreshing()"></i>{{ refreshing() ? 'Refreshing' : 'Live snapshot' }}</span>
        <button class="a-btn sm" (click)="reload()" [disabled]="loading()">{{ loading() ? 'Loading…' : 'Refresh now' }}</button>
        <a class="a-btn sm ghost" href="https://monitor.neelastack.com/" rel="noopener noreferrer">Open Monitor ↗</a>
      </div>
    </div>

    @if (error()) { <div class="alert" role="alert"><strong>Business metrics unavailable.</strong> {{ error() }}</div> }

    @if (data(); as d) {
      <section class="statusbar">
        <div class="status-main" [class.degraded]="businessState(d) !== 'HEALTHY'">
          <span class="status-dot"></span>
          <div><small>Business posture</small><strong>{{ businessState(d) }}</strong></div>
        </div>
        <div><small>Release</small><strong>v{{ d.health.version }}</strong></div>
        <div><small>Active events</small><strong>{{ d.kpis.activeEvents | number }}</strong></div>
        <div><small>Snapshot</small><strong>{{ d.generatedAt | date:'HH:mm:ss' }}</strong></div>
      </section>

      <section class="kpis">
        <article class="kpi primary"><span>Gross captured · 24h</span><strong>₹{{ d.kpis.grossCaptured24hMinor / 100 | number:'1.0-0' }}</strong><small>Authoritative captured/completed payments</small></article>
        <article class="kpi"><span>Orders · 24h</span><strong>{{ d.kpis.orders24h | number }}</strong><small>{{ d.kpis.successfulPayments24h | number }} successful payments</small></article>
        <article class="kpi"><span>Tickets issued · 24h</span><strong>{{ d.kpis.ticketsIssued24h | number }}</strong><small>{{ d.kpis.checkIns24h | number }} accepted check-ins</small></article>
        <article class="kpi"><span>Refunds · 24h</span><strong>₹{{ d.kpis.refunds24hMinor / 100 | number:'1.0-0' }}</strong><small>{{ d.health.queues.pendingRefunds | number }} currently pending</small></article>
      </section>

      <div class="columns">
        <section class="panel">
          <div class="panel-head"><div><div class="eyebrow">Event-day pulse</div><h2>Customer &amp; venue activity</h2></div><span class="muted">last 24 hours</span></div>
          <div class="pulse-grid">
            <div><span>Accepted check-ins</span><strong>{{ d.kpis.checkIns24h | number }}</strong><small>Gate entry confirmations</small></div>
            <div><span>Tickets issued</span><strong>{{ d.kpis.ticketsIssued24h | number }}</strong><small>Paid + complimentary issuance</small></div>
            <div><span>Upcoming events</span><strong>{{ d.kpis.upcomingEvents | number }}</strong><small>Published future events</small></div>
            <div><span>Active events</span><strong>{{ d.kpis.activeEvents | number }}</strong><small>Currently live</small></div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head"><div><div class="eyebrow">Financial recovery</div><h2>Payment posture</h2></div><span class="muted">investigate exceptions, don't mutate state manually</span></div>
          <div class="signal-list">
            <div><span>Successful payments</span><b>{{ d.kpis.successfulPayments24h | number }}</b></div>
            <div><span>Failed / cancelled</span><b [class.bad]="d.kpis.failedPayments24h > 0">{{ d.kpis.failedPayments24h | number }}</b></div>
            <div><span>Pending payments</span><b [class.bad]="d.health.queues.pendingPayments > 0">{{ d.health.queues.pendingPayments | number }}</b></div>
            <div><span>Stale &gt;2m</span><b [class.bad]="d.health.queues.stalePayments > 0">{{ d.health.queues.stalePayments | number }}</b></div>
            <div><span>Provider recovery</span><b [class.bad]="d.health.queues.providerOrderRecoveryPending > 0">{{ d.health.queues.providerOrderRecoveryPending | number }}</b></div>
            <div><span>Refunds pending</span><b [class.bad]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds | number }}</b></div>
          </div>
        </section>
      </div>

      <section class="panel recovery">
        <div class="panel-head"><div><div class="eyebrow">Exceptions</div><h2>Business recovery queues</h2></div><span class="muted">non-zero values need investigation</span></div>
        <div class="queue-grid">
          <div class="queue"><span>Webhook backlog</span><strong [class.bad]="d.health.queues.webhookBacklog > 0">{{ d.health.queues.webhookBacklog }}</strong><small>waiting longer than 2 minutes</small></div>
          <div class="queue"><span>Webhook stuck</span><strong [class.bad]="d.health.queues.webhookStuck > 0">{{ d.health.queues.webhookStuck }}</strong><small>processing longer than 5 minutes</small></div>
          <div class="queue"><span>Refund queue</span><strong [class.bad]="d.health.queues.pendingRefunds > 0">{{ d.health.queues.pendingRefunds }}</strong><small>requested / processing</small></div>
          <div class="queue"><span>Expired holds</span><strong [class.warn]="d.health.queues.expiredReservations > 0">{{ d.health.queues.expiredReservations }}</strong><small>reservation cleanup candidates</small></div>
          <div class="queue"><span>Mail pending</span><strong [class.warn]="d.health.queues.mailPending > 0">{{ d.health.queues.mailPending }}</strong><small>pending / processing</small></div>
          <div class="queue"><span>Mail failed</span><strong [class.bad]="d.health.queues.mailFailed > 0">{{ d.health.queues.mailFailed }}</strong><small>delivery failures</small></div>
          <div class="queue"><span>Event notices failed</span><strong [class.bad]="d.health.queues.eventNotificationFailed > 0">{{ d.health.queues.eventNotificationFailed }}</strong><small>cancellation/detail email failures</small></div>
        </div>
      </section>

      @if (businessState(d) !== 'HEALTHY') {
        <section class="next-step">
          <div><div class="eyebrow">Next step</div><strong>Use the dedicated Monitor console for infrastructure diagnosis.</strong><span>It includes searchable live logs, service health and recovery signals without mixing them into the organizer workflow.</span></div>
          <a class="a-btn" href="https://monitor.neelastack.com/" rel="noopener noreferrer">Open SRE Monitor ↗</a>
        </section>
      }

      <footer class="ops-footer">
        <span>Last generated {{ d.generatedAt | date:'d MMM yyyy, HH:mm:ss' }}</span>
        <span>Business snapshot auto-refreshes every 15 seconds</span>
        <span><a routerLink="/admin">Return to Console</a></span>
      </footer>
    } @else if (loading()) {
      <div class="panel loading-panel"><div class="loader"></div><strong>Loading business operations snapshot…</strong><span>Only aggregate operator metrics are requested.</span></div>
    }
  `,
  styles: [`
    :host{display:block}.hero{display:flex;justify-content:space-between;gap:24px;align-items:flex-end;margin-bottom:18px}.hero-actions{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.live{display:inline-flex;align-items:center;gap:7px;font-size:11px;color:#7b7380}.live i{width:7px;height:7px;border-radius:50%;background:#52a879;box-shadow:0 0 0 4px rgba(82,168,121,.12)}.live i.warn{background:#d7a349}.statusbar{display:grid;grid-template-columns:minmax(240px,2fr) repeat(3,1fr);gap:1px;background:#ded8d0;border:1px solid #ded8d0;border-radius:16px;overflow:hidden;margin-bottom:12px}.statusbar>div{background:#fff;padding:15px 18px}.statusbar small,.kpi span,.queue span{display:block;color:#8b828d;font-size:9px;text-transform:uppercase;letter-spacing:.1em}.statusbar strong{display:block;margin-top:5px;font-size:13px}.status-main{display:flex;align-items:center;gap:12px}.status-main strong{font-size:16px;color:#39734d}.status-main.degraded strong{color:#9a4e36}.status-dot{width:10px;height:10px;border-radius:50%;background:#52a879;box-shadow:0 0 0 5px rgba(82,168,121,.12)}.degraded .status-dot{background:#d7a349;box-shadow:0 0 0 5px rgba(215,163,73,.12)}.kpis{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:12px}.kpi{background:#fff;border:1px solid #e4ded7;border-radius:15px;padding:18px}.kpi.primary{background:#151019;color:#fff;border-color:#151019}.kpi span{color:#8b828d}.kpi.primary span{color:#aaa1ae}.kpi strong{display:block;font-size:28px;letter-spacing:-.04em;margin:12px 0 5px}.kpi small{color:#9a919d;font-size:10px}.kpi.primary small{color:#aaa1ae}.columns{display:grid;grid-template-columns:1.05fr .95fr;gap:12px;margin-bottom:12px}.panel{background:#fff;border:1px solid #e4ded7;border-radius:15px;padding:20px}.panel-head{display:flex;justify-content:space-between;align-items:flex-end;gap:12px;margin-bottom:16px}.panel h2{font-size:20px;margin:5px 0 0}.muted{font-size:10px;color:#9a919d}.pulse-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.pulse-grid>div{border:1px solid #eee9e3;border-radius:12px;padding:15px}.pulse-grid span{display:block;color:#766e79;font-size:10px}.pulse-grid strong{display:block;font-size:23px;margin:9px 0 2px}.pulse-grid small{color:#8b828d;font-size:9px}.signal-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.signal-list>div{display:flex;justify-content:space-between;gap:10px;padding:12px 13px;background:#faf8f5;border-radius:11px}.signal-list span{font-size:10px;color:#766e79}.signal-list b{font-size:13px}.bad{color:#a13f3f!important}.warn{color:#a36b22!important}.recovery{margin-bottom:12px}.queue-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:8px}.queue{padding:14px;background:#faf8f5;border:1px solid #eee9e3;border-radius:12px}.queue strong{display:block;font-size:22px;margin:8px 0 2px}.queue small{display:block;color:#8b828d;font-size:9px}.next-step{margin:12px 0;padding:16px 18px;border:1px solid #dbcdb9;background:#fffaf2;border-radius:15px;display:flex;justify-content:space-between;align-items:center;gap:16px}.next-step strong,.next-step span{display:block}.next-step strong{margin-top:4px;font-size:13px}.next-step span{margin-top:5px;color:#776f79;font-size:11px;line-height:1.5}.ops-footer{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;color:#918894;font-size:9px;padding:14px 2px}.ops-footer a{color:inherit;text-decoration:underline}.a-btn.ghost{background:#fff8ef;border-color:#e1d4c3}.alert{margin-bottom:12px;padding:12px 14px;background:#fff0f0;border:1px solid #efd0d0;border-radius:12px;color:#8c4141;font-size:12px}.loading-panel{min-height:240px;display:grid;place-content:center;text-align:center;gap:8px;color:#6f6573}.loader{width:24px;height:24px;border:3px solid #e8e2da;border-top-color:#b58637;border-radius:50%;margin:0 auto;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:1050px){.kpis{grid-template-columns:repeat(2,minmax(0,1fr))}.columns{grid-template-columns:1fr}.queue-grid{grid-template-columns:repeat(3,minmax(0,1fr))}.next-step{align-items:flex-start;flex-direction:column}}
    @media(max-width:680px){.hero{align-items:flex-start;flex-direction:column}.statusbar{grid-template-columns:repeat(2,minmax(0,1fr))}.status-main{grid-column:1/-1}.kpis,.pulse-grid,.signal-list,.queue-grid{grid-template-columns:1fr}.ops-footer{display:grid}}
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
      error: err => { this.loading.set(false); this.error.set(err?.error?.message || 'The business operations snapshot could not be loaded.'); }
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

  businessState(d: OperationsDashboard): 'HEALTHY' | 'DEGRADED' {
    const q = d.health.queues;
    return q.stalePayments > 0 || q.providerOrderRecoveryPending > 0 || q.pendingRefunds > 0 || q.webhookBacklog > 0 || q.webhookStuck > 0 || q.mailFailed > 0 || q.eventNotificationFailed > 0 ? 'DEGRADED' : 'HEALTHY';
  }
}
