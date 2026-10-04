import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { Dashboard } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';

type EventRow = Dashboard['events'][number];

@Component({
  selector: 'lk-events-list',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe],
  template: `
    <div class="page-head">
      <div>
        <div class="eyebrow">Event portfolio</div>
        <h1 class="title">Events</h1>
        <p class="sub">Every event you can manage. Open one to edit details, tickets and branding, publish it, or export attendees.</p>
      </div>
      <div class="actions">
        <button type="button" class="a-btn" (click)="store.load(true); resetAndLoad()" [disabled]="store.loading()">↻ Refresh</button>
        @if (store.canAdministerEvents) { <a class="a-btn primary" routerLink="/admin/events/new">＋ Create event</a> }
      </div>
    </div>

    @if (message()) { <div class="alert success" role="status">{{ message() }}</div> }
    @if (actionError() || store.error()) {
      <div class="alert error" role="alert">{{ actionError() || store.error() }} @if (store.error()) { <button type="button" class="a-btn sm" (click)="store.load(true)">Retry</button> }</div>
    }

    <div class="toolbar">
      <input class="search" type="search" placeholder="Search events…" aria-label="Search events" [value]="query()" (input)="query.set($any($event.target).value)" (keyup.enter)="resetAndLoad()" autocomplete="off" />
      <div class="filters" role="group" aria-label="Filter by status">
        @for (f of filters; track f.key) {
          <button type="button" [class.on]="status() === f.key" (click)="status.set(f.key); resetAndLoad()">{{ f.label }}</button>
        }
      </div>
    </div>

    @if (store.loading() && !data()) {
      <div class="card"><div class="skeleton-line"></div><div class="skeleton-line"></div><div class="skeleton-line"></div></div>
    } @else {
      <section class="card table">
        <div class="tr head"><span>Event</span><span>Status</span><span>Sold</span><span>Checked in</span><span>Revenue</span><span>Actions</span></div>
        @for (e of filtered(); track e.id) {
          <div class="tr">
            <span class="name">
              @if (store.canAdministerEvents) { <a [routerLink]="['/admin/events', e.id]"><strong>{{ e.name }}</strong></a> } @else { <strong>{{ e.name }}</strong> }
              <small>{{ e.startsAt | date:'d MMM yyyy · h:mm a' }} · /{{ e.slug }}</small>
            </span>
            <span><b class="status" [class.published]="e.status==='PUBLISHED'" [class.draft]="e.status==='DRAFT'" [class.cancelled]="e.status==='CANCELLED'">{{ e.status }}</b></span>
            <span class="num" data-label="Sold">{{ e.ticketsSold }}</span>
            <span class="num" data-label="Checked in">{{ e.ticketsCheckedIn }}</span>
            <span class="num" data-label="Revenue">₹{{ e.revenueMinor / 100 | number:'1.0-0' }}</span>
            <span class="acts">
              <a class="a-btn sm" [routerLink]="['/admin/events', e.id, 'operations']">Operations</a>
              @if (store.canAdministerEvents) { <a class="a-btn sm" [routerLink]="['/admin/events', e.id]">Edit</a> }
              <a class="a-btn sm" [routerLink]="['/events', e.slug]" target="_blank" rel="noopener">Public page ↗</a>
              <button type="button" class="a-btn sm" (click)="downloadCsv(e)" [disabled]="csvId() === e.id">{{ csvId() === e.id ? 'Exporting…' : 'CSV' }}</button>
              @if (store.canAdministerEvents && (e.status === 'DRAFT' || e.status === 'UNPUBLISHED')) {
                <button type="button" class="a-btn sm primary" (click)="publish(e)" [disabled]="publishingId() === e.id || !canPublish(e)" [title]="publishHint(e)">{{ publishingId() === e.id ? 'Publishing…' : 'Publish' }}</button>
              }
            </span>
          </div>
        } @empty {
          <div class="empty">
            @if (!(data()?.items?.length)) { No events match your search or filter. @if (store.canAdministerEvents) { <br><a class="a-btn primary" style="margin-top:14px" routerLink="/admin/events/new">Create your first event</a> } }
            
          </div>
        }
      </section>
      @if (data()) { <div class="cursor-pager"><button type="button" class="a-btn sm" (click)="previous()" [disabled]="!history.length">← Previous</button><span>Showing {{data()!.items.length}} of {{data()!.total}} events</span><button type="button" class="a-btn sm" (click)="next()" [disabled]="!data()!.hasNext">Next →</button></div> }
    }
  `,
  styles: [ADMIN_UI_STYLES, `
    .actions{display:flex;gap:10px;flex-wrap:wrap}.cursor-pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:16px 0;color:#7a717d;font-size:12px}
    .toolbar{display:flex;gap:12px;flex-wrap:wrap;align-items:center;margin-bottom:14px}
    .search{flex:1 1 260px;min-height:44px;border:1px solid #d9d2c8;border-radius:12px;padding:0 14px;background:#fff;color:var(--ink);font:inherit;font-size:16px;color-scheme:light}
    .search:focus{outline:0;border-color:#9e8150;box-shadow:0 0 0 3px rgba(158,129,80,.14)}
    .filters{display:flex;gap:6px;flex-wrap:wrap}
    .filters button{min-height:40px;padding:0 14px;border-radius:999px;border:1px solid #d8d0c7;background:#fff;color:#4a414d;font-size:13px;font-weight:600;cursor:pointer;display:inline-flex;gap:8px;align-items:center}
    .filters button.on{background:#17121a;border-color:#17121a;color:#fff}
    .filters b{font-size:11px;opacity:.7}
    .table{padding:0;overflow:hidden}
    .tr{display:grid;grid-template-columns:minmax(220px,2.4fr) 110px repeat(3,minmax(70px,.7fr)) minmax(240px,1.8fr);gap:14px;align-items:center;padding:16px 22px;border-top:1px solid #eee9e3}
    .tr.head{border-top:0;color:#9a919d;text-transform:uppercase;letter-spacing:.1em;font-size:11px;font-weight:800;background:var(--soft)}
    .name strong{font-size:15px;color:var(--ink)}.name a{text-decoration:none}.name a:hover strong{text-decoration:underline}
    .name small{display:block;color:#9a919d;font-size:12px;margin-top:3px;overflow-wrap:anywhere}
    .num{font-weight:700;font-size:15px}
    .acts{display:flex;gap:6px;flex-wrap:wrap}
    @media(max-width:980px){
      .tr.head{display:none}
      .tr{grid-template-columns:repeat(3,minmax(0,1fr));gap:10px 12px;padding:16px}
      .tr .name{grid-column:1/3}.tr>span:nth-child(2){grid-column:3;justify-self:end}
      .num{display:flex;flex-direction:column;gap:2px}
      .num::before{content:attr(data-label);font-size:11px;font-weight:500;color:#9a919d}
      .tr .acts{grid-column:1/-1}
      .acts .a-btn{flex:1 1 auto}
    }
  `]
})
export class EventsListComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly api = inject(ApiService);

  readonly query = signal('');
  readonly status = signal('ALL');
  readonly message = signal('');
  readonly actionError = signal('');
  readonly publishingId = signal('');
  readonly csvId = signal('');

  readonly filters = [
    { key: 'ALL', label: 'All' }, { key: 'DRAFT', label: 'Draft' }, { key: 'PUBLISHED', label: 'Published' }, { key: 'CLOSED', label: 'Completed / archived' }
  ];

  readonly data = signal<import('../../core/api/api.models').AdminEventCursorPage | undefined>(undefined);
  private cursor: string | undefined;
  private history: (string | undefined)[] = [];
  private readonly events = computed(() => this.data()?.items ?? []);
  private matches(e: EventRow, key: string): boolean {
    if (key === 'ALL') return true;
    if (key === 'CLOSED') return ['COMPLETED', 'ARCHIVED', 'CANCELLED'].includes(e.status);
    return e.status === key;
  }
  count(key: string): number { return this.events().filter(e => this.matches(e, key)).length; }

  readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    const key = this.status();
    return this.events().filter(e => this.matches(e, key) && (!q || e.name.toLowerCase().includes(q) || e.slug.toLowerCase().includes(q)));
  });

  ngOnInit(): void { this.store.load(true); this.resetAndLoad(); }

  resetAndLoad(): void { this.cursor = undefined; this.history = []; this.loadCursor(); }
  loadCursor(): void {
    this.actionError.set('');
    this.api.adminEventsCursor({q: this.query().trim() || undefined, status: this.status() === 'ALL' ? undefined : this.status(), cursor: this.cursor, size: 50}).subscribe({
      next: r => this.data.set(r),
      error: err => this.actionError.set(err?.error?.message || 'Events could not be loaded.')
    });
  }
  next(): void { const next = this.data()?.nextCursor; if (!next) return; this.history.push(this.cursor); this.cursor = next; this.loadCursor(); }
  previous(): void { if (!this.history.length) return; this.cursor = this.history.pop(); this.loadCursor(); }

  canPublish(e: EventRow): boolean {
    return (e.status === 'DRAFT' || e.status === 'UNPUBLISHED') && new Date(e.startsAt).getTime() > Date.now();
  }

  publishHint(e: EventRow): string {
    if (!['DRAFT','UNPUBLISHED'].includes(e.status)) return 'Only draft or unpublished events can be published.';
    if (!e.startsAt || new Date(e.startsAt).getTime() <= Date.now()) return 'An event in the past cannot be published.';
    return 'Publish this event';
  }

  publish(e: EventRow): void {
    this.publishingId.set(e.id); this.actionError.set(''); this.message.set('');
    this.api.publishEvent(e.id).subscribe({
      next: () => { this.publishingId.set(''); this.message.set(`“${e.name}” is now published.`); this.store.load(true); this.resetAndLoad(); },
      error: err => { this.publishingId.set(''); this.actionError.set(err?.error?.message || 'Could not publish this event. Make sure it has at least one ticket type.'); }
    });
  }

  downloadCsv(e: EventRow): void {
    if (this.csvId()) return;
    this.csvId.set(e.id); this.actionError.set('');
    // Browser-native same-origin download. Authentication remains on the httpOnly cookie
    // and the request is no longer routed through Angular's XHR/blob pipeline.
    const a = document.createElement('a');
    a.href = `/api/v1/admin/events/${encodeURIComponent(e.id)}/attendees.csv`;
    a.download = `${e.slug}-attendees.csv`;
    a.rel = 'noopener'; a.style.display = 'none';
    document.body.appendChild(a); a.click(); a.remove();
    window.setTimeout(() => this.csvId.set(''), 1200);
  }
}
