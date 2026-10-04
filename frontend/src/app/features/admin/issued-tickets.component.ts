import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { CursorPage, AdminScopedTicket } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';

@Component({
  selector: 'lk-issued-tickets', standalone: true, imports: [CommonModule, DatePipe, DecimalPipe, FormsModule],
  template: `
    <div class="page-head"><div><div class="eyebrow">Admissions operations</div><h1 class="title">Issued tickets</h1><p class="sub">All issued admission credentials in the events you are authorized to operate.</p><span class="scope-pill">All events</span></div></div>
    <div class="alert error" *ngIf="error" role="alert">{{error}} <button type="button" class="retry" (click)="resetAndLoad()">Retry</button></div>
    <section class="card">
      <div class="toolbar">
        <input [(ngModel)]="query" (keyup.enter)="resetAndLoad()" placeholder="Search ticket, attendee, email, order or event" aria-label="Search tickets"/>
        <select [(ngModel)]="status" (change)="resetAndLoad()" aria-label="Status"><option value="">All statuses</option><option value="ISSUED">Issued</option><option value="CHECKED_IN">Checked in</option><option value="CANCELLED">Cancelled</option><option value="REFUNDED">Refunded</option></select>
        <select [(ngModel)]="source" (change)="resetAndLoad()" aria-label="Source"><option value="">All sources</option><option value="ONLINE_PAYMENT">Online</option><option value="COMPLIMENTARY_MANAGER">Complimentary</option></select>
        <button class="a-btn sm" type="button" (click)="resetAndLoad()">Search</button>
      </div>
      <div class="table-wrap"><table><thead><tr><th>Ticket</th><th>Event</th><th>Attendee</th><th>Type</th><th>Order</th><th>Amount</th><th>Status</th><th>Check-in</th><th>Issued</th></tr></thead><tbody>
        <tr *ngFor="let t of data()?.items"><td><strong>{{t.ticketNumber}}</strong><small>{{t.source === 'COMPLIMENTARY_MANAGER' ? 'Complimentary' : 'Online'}}</small></td><td><strong>{{t.eventName}}</strong><small>/{{t.eventSlug}}</small></td><td><strong>{{t.attendeeName || 'Guest'}}</strong><small>{{t.email || ''}}</small></td><td>{{t.ticketType}}</td><td>{{t.orderNumber}}</td><td>₹{{t.amountMinorUnits/100 | number:'1.0-0'}}</td><td><span class="status" [class.published]="t.status==='ISSUED'" [class.cancelled]="t.status==='CANCELLED' || t.status==='REFUNDED'">{{t.status}}</span></td><td>{{t.checkedInAt ? (t.checkedInAt|date:'d MMM, h:mm a') : 'Not checked'}}</td><td>{{t.issuedAt|date:'d MMM, h:mm a'}}</td></tr>
        <tr *ngIf="!(data()?.items?.length)"><td colspan="9" class="empty">No issued tickets match this search.</td></tr>
      </tbody></table></div>
      <div class="pager" *ngIf="data()"><button class="a-btn sm" [disabled]="page()===0 || loading" (click)="previous()">← Previous</button><span>Page {{page()+1}} · {{data()!.items.length}} tickets on this page{{data()!.hasNext ? ' · More available' : ' · End of results'}}</span><button class="a-btn sm" [disabled]="!data()!.hasNext || loading" (click)="next()">Next →</button></div>
    </section>
  `,
  styles: [ADMIN_UI_STYLES, `.scope-pill{display:inline-flex;margin-top:8px;padding:5px 9px;border-radius:999px;background:#f5efe7;border:1px solid #e6ddd2;color:#6e5f4a;font-size:10px;font-weight:800;letter-spacing:.06em;text-transform:uppercase}.alert{margin:0 0 14px;padding:12px 14px;border-radius:12px}.alert.error{background:#fff0f0;border:1px solid #f1d1d1;color:#8c4141}.retry{border:0;background:transparent;color:inherit;text-decoration:underline;font-weight:800;cursor:pointer}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:18px}.toolbar input{flex:1 1 280px}.toolbar input,.toolbar select{min-height:42px;border:1px solid #d9d2c8;border-radius:10px;padding:8px 12px;background:#fff;color:var(--ink);font:inherit}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}table{width:100%;border-collapse:collapse;min-width:1100px}th,td{text-align:left;padding:13px 12px;border-bottom:1px solid #eee8e0;font-size:12px;white-space:nowrap}th{font-size:10px;text-transform:uppercase;letter-spacing:.1em;color:#827987;background:#faf8f5}td small{display:block;color:#8a8190;font-size:10px;margin-top:3px;white-space:normal}.status.cancelled{background:#fde8e8;color:#8c2f2f}.pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding-top:16px;color:#7a717d;font-size:12px}@media(max-width:760px){.pager{flex-direction:column;align-items:stretch}.pager span{text-align:center}}`]
})
export class IssuedTicketsComponent implements OnInit {
  private readonly api=inject(ApiService);
  readonly data=signal<CursorPage<AdminScopedTicket>|undefined>(undefined);
  error=''; query=''; status=''; source=''; loading=false;
  readonly page=signal(0);
  private cursor:string|undefined;
  private readonly history:(string|undefined)[]=[];
  ngOnInit():void { this.load(0); }
  resetAndLoad():void { this.cursor=undefined; this.history.length=0; this.load(0); }
  load(page:number):void { this.error=''; this.page.set(Math.max(0,page)); this.loading=true; const cursor = page===0 ? undefined : this.cursor; this.api.allIssuedTicketsCursor({q:this.query.trim()||undefined,status:this.status||undefined,source:this.source||undefined,cursor,size:50}).subscribe({next:r=>{this.data.set(r);this.cursor=cursor;this.loading=false;},error:e=>{this.data.set(undefined);this.loading=false;this.error=e?.error?.message||'Issued tickets could not be loaded.';}}); }
  next():void { const nextCursor=this.data()?.nextCursor; if(!nextCursor || this.loading) return; this.history.push(this.cursor); this.cursor=nextCursor; this.page.update(v=>v+1); this.loading=true; this.api.allIssuedTicketsCursor({q:this.query.trim()||undefined,status:this.status||undefined,source:this.source||undefined,cursor:nextCursor,size:50}).subscribe({next:r=>{this.data.set(r);this.cursor=nextCursor;this.loading=false;},error:e=>{this.history.pop();this.cursor=this.history.length ? this.history[this.history.length-1] : undefined;this.page.update(v=>Math.max(0,v-1));this.loading=false;this.error=e?.error?.message||'Issued tickets could not be loaded.';}}); }
  previous():void { if(this.page()===0 || this.loading) return; const prior=this.history.pop(); this.cursor=prior; this.page.update(v=>Math.max(0,v-1)); this.loading=true; this.api.allIssuedTicketsCursor({q:this.query.trim()||undefined,status:this.status||undefined,source:this.source||undefined,cursor:prior,size:50}).subscribe({next:r=>{this.data.set(r);this.cursor=prior;this.loading=false;},error:e=>{this.history.push(prior);this.page.update(v=>v+1);this.loading=false;this.error=e?.error?.message||'Issued tickets could not be loaded.';}}); }
}
