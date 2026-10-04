import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { AdminIssuedTicket, AdminOrder, CursorPage } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';

@Component({
  selector: 'lk-event-operations', standalone: true, imports: [CommonModule, DatePipe, DecimalPipe, RouterLink, FormsModule],
  template: `
    <a class="back" routerLink="/admin/events">← All events</a>
    <div class="page-head"><div><div class="eyebrow">Event operations</div><h1 class="title">{{ eventName || 'Event' }}</h1><p class="sub">Operational control for issued tickets, attendees, orders and the event report.</p></div></div>
    <div class="alert" *ngIf="error" role="alert">{{ error }} <button type="button" class="link-btn" (click)="reloadCurrent()">Retry</button></div><div class="summary-grid" *ngIf="summary"><div><small>Tickets sold</small><strong>{{summary.ticketsSold}}</strong></div><div><small>Checked in</small><strong>{{summary.ticketsCheckedIn}}</strong></div><div><small>Orders</small><strong>{{summary.orderCount}}</strong></div><div><small>Revenue</small><strong>₹{{summary.revenueMinor / 100 | number:'1.0-0'}}</strong></div></div><div class="tabs"><button [class.on]="tab() === 'tickets'" (click)="selectTab('tickets')">Issued tickets / attendees <i>{{ summary?.ticketsSold || 0 }}</i></button><button [class.on]="tab() === 'orders'" (click)="selectTab('orders')">Orders <i>{{ summary?.orderCount || 0 }}</i></button><button class="a-btn sm" type="button" (click)="downloadCsv()" [disabled]="csvBusy">{{ csvBusy ? 'Exporting…' : 'Export CSV' }}</button></div>
    <section class="card" *ngIf="tab() === 'tickets'">
      <div class="toolbar"><input [(ngModel)]="ticketQuery" (keyup.enter)="loadTickets(0)" placeholder="Search ticket, attendee, email or order"/><select [(ngModel)]="ticketStatus" (change)="loadTickets(0)"><option value="">All statuses</option><option value="ISSUED">Issued</option><option value="CHECKED_IN">Checked in</option><option value="CANCELLED">Cancelled</option><option value="REFUNDED">Refunded</option></select><select [(ngModel)]="ticketSource" (change)="loadTickets(0)"><option value="">All sources</option><option value="ONLINE_PAYMENT">Online</option><option value="COMPLIMENTARY_MANAGER">Complimentary</option></select><button class="a-btn sm" (click)="loadTickets(0)">Search</button></div>
      <div class="table-wrap"><table><thead><tr><th>Ticket</th><th>Attendee</th><th>Type</th><th>Order</th><th>Amount</th><th>Status</th><th>Check-in</th><th>Issued</th></tr></thead><tbody><tr *ngFor="let t of tickets()?.items"><td><strong>{{t.ticketNumber}}</strong><small>{{t.source === 'COMPLIMENTARY_MANAGER' ? 'Complimentary' : 'Online'}}</small></td><td><strong>{{t.attendeeName || 'Guest'}}</strong><small>{{t.email || ''}}</small></td><td>{{t.ticketType}}</td><td>{{t.orderNumber}}</td><td>₹{{t.amountMinorUnits / 100 | number:'1.0-0'}}</td><td><span class="status" [class.published]="t.status === 'ISSUED'" [class.cancelled]="t.status === 'CANCELLED' || t.status === 'REFUNDED'">{{t.status}}</span></td><td>{{t.checkedInAt ? (t.checkedInAt | date:'d MMM, h:mm a') : 'Not checked'}}</td><td>{{t.issuedAt | date:'d MMM, h:mm a'}}</td></tr><tr *ngIf="!(tickets()?.items?.length)"><td colspan="8" class="empty">No issued tickets match this search.</td></tr></tbody></table></div>
      <div class="pager" *ngIf="tickets()"><button class="a-btn sm" [disabled]="!ticketHistory.length" (click)="previousTickets()">← Previous</button><span>Page {{ticketPage()+1}} · {{tickets()!.items.length}} tickets on this page{{tickets()!.hasNext ? ' · More available' : ' · End of results'}}</span><button class="a-btn sm" [disabled]="!tickets()!.hasNext" (click)="nextTickets()">Next →</button></div>
    </section>
    <section class="card" *ngIf="tab() === 'orders'">
      <div class="toolbar"><input [(ngModel)]="orderQuery" (keyup.enter)="loadOrders(0)" placeholder="Search order, customer or email"/><select [(ngModel)]="orderStatus" (change)="loadOrders(0)"><option value="">All order statuses</option><option value="CONFIRMED">Confirmed</option><option value="AWAITING_PAYMENT">Awaiting payment</option><option value="CANCELLED">Cancelled</option><option value="EXPIRED">Expired</option></select><button class="a-btn sm" (click)="loadOrders(0)">Search</button></div>
      <div class="table-wrap"><table><thead><tr><th>Order</th><th>Customer</th><th>Tickets</th><th>Total</th><th>Payment</th><th>Status</th><th>Created</th></tr></thead><tbody><tr *ngFor="let o of orders()?.items"><td><strong>{{o.orderNumber}}</strong></td><td><strong>{{o.customerName}}</strong><small>{{o.customerEmail}}</small></td><td>{{o.ticketCount}}</td><td>₹{{o.totalMinorUnits / 100 | number:'1.0-0'}}</td><td>{{o.paymentStatus || '—'}}</td><td><span class="status" [class.published]="o.status === 'CONFIRMED'">{{o.status}}</span></td><td>{{o.createdAt | date:'d MMM yyyy, h:mm a'}}</td></tr><tr *ngIf="!(orders()?.items?.length)"><td colspan="7" class="empty">No orders match this search.</td></tr></tbody></table></div>
      <div class="pager" *ngIf="orders()"><button class="a-btn sm" [disabled]="!orderHistory.length" (click)="previousOrders()">← Previous</button><span>Page {{orderPage()+1}} · {{orders()!.items.length}} orders on this page{{orders()!.hasNext ? ' · More available' : ' · End of results'}}</span><button class="a-btn sm" [disabled]="!orders()!.hasNext" (click)="nextOrders()">Next →</button></div>
    </section>
  `,
  styles: [ADMIN_UI_STYLES, `.summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:0 0 16px}.summary-grid>div{padding:16px;border:1px solid var(--line);border-radius:14px;background:#fff}.summary-grid small{display:block;text-transform:uppercase;letter-spacing:.1em;color:#8b828d;font-size:9px}.summary-grid strong{display:block;margin-top:6px;font-size:22px;color:var(--ink)}.alert{margin:0 0 14px;padding:12px 14px;border-radius:12px;background:#fff0f0;border:1px solid #f1d1d1;color:#8c4141;font-size:12px}.link-btn{border:0;background:transparent;color:inherit;text-decoration:underline;font-weight:800;cursor:pointer}.back{display:inline-block;color:#746d77;text-decoration:none;font-size:12px;margin-bottom:26px}.tabs{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin-bottom:16px;border-bottom:1px solid var(--line);padding-bottom:12px}.tabs button{border:0;background:#f1ede7;color:#5e5561;border-radius:10px;padding:10px 14px;font:inherit;font-size:13px;font-weight:800;cursor:pointer}.tabs button.on{background:#17121a;color:#fff}.tabs button i{font-style:normal;opacity:.65;margin-left:5px}.tabs .a-btn{margin-left:auto}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:18px}.toolbar input{flex:1 1 280px}.toolbar input,.toolbar select{min-height:42px;border:1px solid #d9d2c8;border-radius:10px;padding:8px 12px;background:#fff;color:var(--ink);font:inherit}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}table{width:100%;border-collapse:collapse;min-width:860px}th,td{text-align:left;padding:13px 12px;border-bottom:1px solid #eee8e0;font-size:12px;white-space:nowrap}th{font-size:10px;text-transform:uppercase;letter-spacing:.1em;color:#827987;background:#faf8f5}td small{display:block;color:#8a8190;font-size:10px;margin-top:3px;white-space:normal}.status.cancelled{background:#fde8e8;color:#8c2f2f}.pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding-top:16px;color:#7a717d;font-size:12px}@media(max-width:900px){.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.tabs .a-btn{margin-left:0}.pager{flex-direction:column;align-items:stretch}.pager span{text-align:center}}`]
})
export class EventOperationsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  readonly eventId = this.route.snapshot.paramMap.get('eventId') || '';
  eventName = '';
  summary?: { ticketsSold: number; ticketsCheckedIn: number; revenueMinor: number; orderCount: number };
  error = '';
  csvBusy = false;
  readonly tab = signal<'tickets'|'orders'>('tickets');
  readonly tickets = signal<CursorPage<AdminIssuedTicket> | undefined>(undefined);
  readonly orders = signal<CursorPage<AdminOrder> | undefined>(undefined);
  ticketQuery=''; ticketStatus=''; ticketSource=''; orderQuery=''; orderStatus='';
  private ticketCursor: string | undefined; private orderCursor: string | undefined;
  private ticketHistory: (string | undefined)[] = []; private orderHistory: (string | undefined)[] = [];
  readonly ticketPage = signal(0); readonly orderPage = signal(0);
  ngOnInit(): void {
    if (!this.eventId) { this.error = 'Event was not specified.'; return; }
    this.api.eventOperationsSummary(this.eventId).subscribe({ next: e => { this.eventName = e.eventName; this.summary = { ticketsSold: e.ticketsSold, ticketsCheckedIn: e.ticketsCheckedIn, revenueMinor: e.revenueMinor, orderCount: e.orderCount }; }, error: e => { this.error = e?.error?.message || 'Event operations summary could not be loaded.'; } });
    this.loadTickets(0);
  }
  selectTab(next: 'tickets'|'orders'): void { this.tab.set(next); if (next === 'orders' && !this.orders()) this.loadOrders(0); }
  reloadCurrent(): void { this.tab() === 'tickets' ? this.loadTickets(this.ticketPage()) : this.loadOrders(this.orderPage()); }
  loadTickets(page: number): void {
    this.error='';
    if (page === 0) { this.ticketCursor = undefined; this.ticketHistory=[]; this.ticketPage.set(0); }
    else this.ticketPage.set(page);
    this.fetchTickets();
  }
  private fetchTickets(): void {
    this.api.issuedTicketsCursor(this.eventId,{q:this.ticketQuery.trim(),status:this.ticketStatus,source:this.ticketSource,cursor:this.ticketCursor,size:50}).subscribe({
      next:r=>{ this.tickets.set(r); },
      error:e=>{ this.error=e?.error?.message || 'Issued tickets could not be loaded.'; }
    });
  }
  nextTickets(): void { const c=this.tickets()?.nextCursor; if(!c)return; this.ticketHistory.push(this.ticketCursor); this.ticketCursor=c; this.ticketPage.update(p=>p+1); this.fetchTickets(); }
  previousTickets(): void { if(!this.ticketHistory.length)return; this.ticketCursor=this.ticketHistory.pop(); this.ticketPage.update(p=>Math.max(0,p-1)); this.fetchTickets(); }
  loadOrders(page: number): void {
    this.error='';
    if (page === 0) { this.orderCursor = undefined; this.orderHistory=[]; this.orderPage.set(0); }
    else this.orderPage.set(page);
    this.fetchOrders();
  }
  private fetchOrders(): void {
    this.api.eventOrdersCursor(this.eventId,{q:this.orderQuery.trim(),status:this.orderStatus,cursor:this.orderCursor,size:50}).subscribe({
      next:r=>{ this.orders.set(r); },
      error:e=>{ this.error=e?.error?.message || 'Orders could not be loaded.'; }
    });
  }
  nextOrders(): void { const c=this.orders()?.nextCursor; if(!c)return; this.orderHistory.push(this.orderCursor); this.orderCursor=c; this.orderPage.update(p=>p+1); this.fetchOrders(); }
  previousOrders(): void { if(!this.orderHistory.length)return; this.orderCursor=this.orderHistory.pop(); this.orderPage.update(p=>Math.max(0,p-1)); this.fetchOrders(); }
  downloadCsv(): void {
    if (!this.eventId || this.csvBusy) return;
    // Use a normal same-origin browser download instead of an XHR blob. This avoids
    // Chrome/HTTP3 QUIC failures observed on streamed CSV responses while preserving
    // the existing httpOnly authentication cookie.
    this.csvBusy = true; this.error = '';
    const a = document.createElement('a');
    a.href = `/api/v1/admin/events/${encodeURIComponent(this.eventId)}/attendees.csv`;
    a.download = `${this.eventId}-attendees.csv`;
    a.rel = 'noopener';
    a.style.display = 'none';
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => { this.csvBusy = false; }, 1200);
  }
}
