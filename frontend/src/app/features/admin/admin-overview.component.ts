import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';

@Component({
  selector: 'lk-admin-overview',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="page-head">
      <div>
        <div class="eyebrow">Operations control</div>
        <h1 class="title">{{ greeting }}, {{ firstName }}.</h1>
        <p class="sub">{{ canAdminister ? 'One workspace for event inventory, revenue, staff access and event-day entry.' : 'Operate only the events explicitly assigned to your account.' }}</p>
      </div>
      <div class="actions">
        <button type="button" class="a-btn" (click)="store.load(true)" [disabled]="store.loading()">↻ Refresh</button>
        @if (canAdminister) { <a class="a-btn primary" routerLink="/admin/events/new">＋ Create event</a> }
      </div>
    </div>

    @if (store.error()) {
      <div class="alert error" role="alert">{{ store.error() }} <button type="button" class="a-btn sm" (click)="store.load(true)">Retry</button></div>
    }

    @if (store.loading() && !store.dash()) {
      <div class="metrics"><div class="card"><div class="skeleton-line"></div><div class="skeleton-line"></div></div><div class="card"><div class="skeleton-line"></div><div class="skeleton-line"></div></div><div class="card"><div class="skeleton-line"></div><div class="skeleton-line"></div></div><div class="card"><div class="skeleton-line"></div><div class="skeleton-line"></div></div></div>
    } @else if (store.dash(); as d) {
      <section class="metrics">
        <article class="card"><span>Gross revenue</span><strong>₹{{ d.totalRevenueMinor / 100 | number:'1.0-0' }}</strong><small>Successful payments</small></article>
        <article class="card"><span>Tickets sold</span><strong>{{ d.totalSold | number }}</strong><small>Across managed events</small></article>
        <article class="card"><span>Checked in</span><strong>{{ d.totalCheckedIn | number }}</strong><small>Verified entries</small></article>
        <article class="card"><span>Events</span><strong>{{ d.totalEvents | number }}</strong><small>{{ d.publishedEvents | number }} published · {{ d.draftEvents | number }} draft</small></article>
      </section>

      <section class="card list">
        <div class="list-head">
          <div><div class="eyebrow">Event portfolio</div><h2>Upcoming &amp; recent events</h2></div>
          <a class="a-btn sm" routerLink="/admin/events">View all events →</a>
        </div>
        @for (e of recent(); track e.id) {
          <a class="row" [routerLink]="canAdminister ? ['/admin/events', e.id] : null" [class.static]="!canAdminister">
            <span class="name"><strong>{{ e.name }}</strong><small>{{ e.startsAt | date:'d MMM yyyy · h:mm a' }}</small></span>
            <span><b class="status" [class.published]="e.status==='PUBLISHED'" [class.draft]="e.status==='DRAFT'" [class.cancelled]="e.status==='CANCELLED'">{{ e.status }}</b></span>
            <span class="num"><small>Sold</small>{{ e.ticketsSold }}</span>
            <span class="num"><small>Checked in</small>{{ e.ticketsCheckedIn }}</span>
            <span class="num"><small>Revenue</small>₹{{ e.revenueMinor / 100 | number:'1.0-0' }}</span>
          </a>
        } @empty {
          <div class="empty">
            No events yet.
            @if (canAdminister) { <br><a class="a-btn primary" style="margin-top:14px" routerLink="/admin/events/new">Create your first event</a> }
          </div>
        }
      </section>

      <section class="quick">
        @if (canAdminister) {
          <a class="card q" routerLink="/admin/team"><div class="eyebrow">Event-day access</div><h3>Provision staff &amp; managers</h3><p>Create gate staff, assign them to events and set up event managers.</p><span>Open team &amp; access →</span></a>
        }
        <a class="card q" routerLink="/staff"><div class="eyebrow">Scanner console</div><h3>Turn any phone into a gate scanner</h3><p>Staff sign in, choose their gate and scan QR tickets with the phone camera.</p><span>Open scanner console →</span></a>
        <a class="card q dark" href="https://neelastack.com" target="_blank" rel="noopener noreferrer"><div class="eyebrow">Technology partner</div><h3>Digital experiences by Neelastack</h3><p>Custom web applications, business portals, dashboards and automation.</p><span>Explore Neelastack ↗</span></a>
      </section>
    }
  `,
  styles: [ADMIN_UI_STYLES, `
    .actions{display:flex;gap:10px;flex-wrap:wrap}
    .metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-bottom:16px}
    .metrics .card{padding:20px;margin:0}
    .metrics span{display:block;color:var(--muted);font-size:13px}
    .metrics strong{display:block;font-size:32px;letter-spacing:-.04em;margin:14px 0 6px;font-weight:700}
    .metrics small{color:#9a919d;font-size:12px}
    .list{padding:0;overflow:hidden}.list-head{display:flex;justify-content:space-between;align-items:flex-end;gap:12px;padding:22px 24px;flex-wrap:wrap}
    .list-head h2{margin:6px 0 0}
    .row{display:grid;grid-template-columns:minmax(200px,2.4fr) 1fr repeat(3,minmax(70px,.8fr));gap:14px;align-items:center;padding:16px 24px;border-top:1px solid #eee9e3;color:inherit;text-decoration:none}
    .row:not(.static):hover{background:var(--soft)}
    .name strong{display:block;font-size:15px}.name small{display:block;color:#9a919d;font-size:12px;margin-top:3px}
    .num{font-size:15px;font-weight:700}.num small{display:none;color:#9a919d;font-weight:500;font-size:11px}
    .quick{display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:14px;margin-top:16px}
    .q{display:flex;flex-direction:column;gap:6px;text-decoration:none;color:inherit;background:#ece5db;border:0;margin:0!important}
    .q h3{font-family:var(--display);font-size:21px;margin:4px 0 0;letter-spacing:-.02em;font-weight:600}.q p{margin:0;color:#6f6573;font-size:13px;line-height:1.5}.q span{margin-top:auto;padding-top:14px;font-size:13px;font-weight:800}
    .q.dark{background:#151019;color:#fff}.q.dark p{color:#a59cab}.q.dark .eyebrow{color:#f0cf8c}
    @media(max-width:1000px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}}
    @media(max-width:700px){
      .row{grid-template-columns:repeat(3,minmax(0,1fr));padding:14px 16px;gap:10px 12px}
      .row .name{grid-column:1/3}.row>span:nth-child(2){grid-column:3;justify-self:end}
      .row .num small{display:block}
      .list-head{padding:18px 16px}
      .metrics strong{font-size:26px}
    }
  `]
})
export class AdminOverviewComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly recent = computed(() => (this.store.dash()?.events ?? []).slice(0, 6));

  get canAdminister(): boolean { return this.store.canAdministerEvents; }
  get firstName(): string { return this.auth.fullName().split(/\s+/)[0] || 'Operator'; }
  get greeting(): string {
    const hour = Number(new Intl.DateTimeFormat('en-GB', { hour: '2-digit', hourCycle: 'h23', timeZone: 'Asia/Kolkata' }).format(new Date()));
    return hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  }

  ngOnInit(): void { if (this.auth.role() === 'FINANCE') { this.router.navigateByUrl('/admin/finance', { replaceUrl: true }); return; } this.store.load(); }
}
