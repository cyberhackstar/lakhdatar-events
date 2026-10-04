import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { FinanceOverview, FinanceRefund, FinanceLedgerRow, CursorPage } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';

@Component({
  selector: 'lk-finance', standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, FormsModule],
  template: `
    <div class="page-head"><div><div class="eyebrow">Finance & reconciliation</div><h1 class="title">Financial control</h1><p class="sub">Gross sales, refunds, unsettled payments and recovery health for the organizations you are authorized to view.</p></div><button class="a-btn sm" type="button" (click)="reload()" [disabled]="busy">{{busy?'Refreshing…':'Refresh'}}</button></div>
    <div class="alert" *ngIf="error" role="alert">{{error}}</div>
    <div class="metric-grid" *ngIf="overview() as o">
      <div class="metric"><small>Gross captured</small><strong>₹{{o.grossCapturedMinor/100 | number:'1.0-0'}}</strong></div>
      <div class="metric"><small>Refunded</small><strong>₹{{o.refundedMinor/100 | number:'1.0-0'}}</strong></div>
      <div class="metric"><small>Net collected</small><strong>₹{{o.netMinor/100 | number:'1.0-0'}}</strong></div>
      <div class="metric"><small>Pending refunds</small><strong>{{o.pendingRefundCount}}</strong></div>
      <div class="metric"><small>Recovery pending</small><strong>{{o.recoveryPendingCount}}</strong></div>
      <div class="metric"><small>Failed mail jobs</small><strong>{{o.failedMailCount}}</strong></div>
    </div>
    <section class="card" *ngIf="overview() as o">
      <div class="section-head"><div><h2>Operational health</h2><p>These counters identify financial work that may need attention.</p></div><span class="health" [class.warn]="o.pendingPaymentCount>0 || o.recoveryPendingCount>0 || o.pendingRefundCount>0 || o.failedMailCount>0">{{o.pendingPaymentCount>0 || o.recoveryPendingCount>0 || o.pendingRefundCount>0 || o.failedMailCount>0 ? 'Attention required' : 'Healthy'}}</span></div>
      <div class="health-grid"><div><small>Pending payments</small><b>{{o.pendingPaymentCount}}</b></div><div><small>Stale payments &gt; 2m</small><b>{{o.stalePaymentCount}}</b></div><div><small>Oldest pending</small><b>{{o.oldestPendingPaymentAt ? (o.oldestPendingPaymentAt|date:'d MMM yyyy, h:mm a') : '—'}}</b></div><div><small>Active reservations</small><b>{{o.heldReservationCount}}</b></div><div><small>Expired reservations awaiting sweep</small><b>{{o.expiredReservationCount}}</b></div><div><small>Webhook backlog / stuck</small><b>{{o.webhookBacklogCount}} / {{o.webhookStuckCount}}</b></div></div>
    </section>
    <section class="card">
      <div class="section-head"><div><h2>Refunds</h2><p>Searchable, paginated refund operations with server-side organization scope.</p></div></div>
      <div class="toolbar"><input [(ngModel)]="query" (keyup.enter)="applyFilters()" placeholder="Search order, customer, refund id or event"/><select [(ngModel)]="status" (change)="applyFilters()"><option value="">All statuses</option><option value="REQUESTED">Requested</option><option value="PROCESSING">Processing</option><option value="COMPLETED">Completed</option><option value="FAILED">Failed</option></select><button class="a-btn sm" type="button" (click)="applyFilters()">Search</button></div>
      <div class="table-wrap"><table><thead><tr><th>Refund</th><th>Order</th><th>Customer</th><th>Event</th><th>Amount</th><th>Status</th><th>Provider</th><th>Created</th></tr></thead><tbody><tr *ngFor="let r of refunds()?.items"><td><strong>{{r.refundId}}</strong><small>{{r.paymentId}}</small></td><td>{{r.orderNumber}}</td><td>{{r.customerName || 'Guest'}}</td><td>{{r.eventName}}</td><td>₹{{(-r.amountMinor)/100 | number:'1.0-0'}}</td><td>{{r.status}}</td><td>{{r.providerStatus || '—'}}</td><td>{{r.createdAt|date:'d MMM yyyy, h:mm a'}}</td></tr><tr *ngIf="!(refunds()?.items?.length)"><td colspan="8" class="empty">No refunds match this search.</td></tr></tbody></table></div>
      <div class="pager" *ngIf="refunds()"><button class="a-btn sm" [disabled]="!refundHistory.length" (click)="previousRefunds()">← Previous</button><span>{{refunds()!.items.length}} refunds loaded</span><button class="a-btn sm" [disabled]="!refunds()!.hasNext" (click)="nextRefunds()">Next →</button></div>
    </section>
    <section class="card">
      <div class="section-head"><div><h2>Immutable ledger</h2><p>Append-only sale and refund entries used for financial reconciliation and audit.</p></div></div>
      <div class="table-wrap"><table><thead><tr><th>Timestamp</th><th>Type</th><th>Order</th><th>Event</th><th>Organizer</th><th>Amount</th><th>Reference</th></tr></thead><tbody><tr *ngFor="let r of ledger()?.items"><td>{{r.createdAt|date:'d MMM yyyy, h:mm a'}}</td><td>{{r.entryType}}</td><td>{{r.orderNumber || '—'}}</td><td>{{r.eventName || '—'}}</td><td>{{r.organizerName || '—'}}</td><td [class.negative]="r.amountMinor<0">{{r.amountMinor<0?'−':'+'}}₹{{(r.amountMinor<0?-r.amountMinor:r.amountMinor)/100 | number:'1.0-0'}}</td><td><small>{{r.entryId}}</small></td></tr><tr *ngIf="!(ledger()?.items?.length)"><td colspan="7" class="empty">No ledger entries found.</td></tr></tbody></table></div>
      <div class="pager" *ngIf="ledger()"><button class="a-btn sm" [disabled]="!ledgerHistory.length" (click)="previousLedger()">← Previous</button><span>{{ledger()!.items.length}} ledger entries loaded</span><button class="a-btn sm" [disabled]="!ledger()!.hasNext" (click)="nextLedger()">Next →</button></div>
    </section>
  `,
  styles: [ADMIN_UI_STYLES, `.metric-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px;margin:0 0 16px}.metric{padding:17px;border:1px solid var(--line);border-radius:14px;background:#fff}.metric small,.health-grid small{display:block;text-transform:uppercase;letter-spacing:.1em;color:#8b828d;font-size:9px}.metric strong{display:block;margin-top:8px;font-size:20px;color:var(--ink)}.section-head{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:14px}.section-head h2{margin:0;font-size:18px}.section-head p{margin:5px 0 0;color:#817884;font-size:11px}.health{border:1px solid #cfe2d1;background:#eef8ef;color:#45734d;border-radius:999px;padding:7px 10px;font-size:9px;font-weight:900;text-transform:uppercase;letter-spacing:.08em}.health.warn{border-color:#ead9b4;background:#fff7e8;color:#8b6a2f}.health-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.health-grid>div{padding:15px;border:1px solid var(--line);border-radius:12px;background:#faf8f5}.health-grid b{display:block;margin-top:7px;font-size:14px;color:var(--ink)}.alert{margin:0 0 14px;padding:12px 14px;border-radius:12px;background:#fff0f0;border:1px solid #f1d1d1;color:#8c4141;font-size:12px}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:18px}.toolbar input,.toolbar select{min-height:42px;border:1px solid #d9d2c8;border-radius:10px;padding:8px 12px;background:#fff;color:var(--ink);font:inherit}.toolbar input{flex:1 1 300px}.table-wrap{overflow:auto;border:1px solid var(--line);border-radius:14px}table{width:100%;border-collapse:collapse;min-width:1000px}th,td{text-align:left;padding:13px 12px;border-bottom:1px solid #eee8e0;font-size:12px;white-space:nowrap}th{font-size:10px;text-transform:uppercase;letter-spacing:.1em;color:#827987;background:#faf8f5}td small{display:block;color:#8a8190;font-size:9px;margin-top:3px}.negative{color:#a63b3b}.pager{display:flex;justify-content:space-between;align-items:center;gap:12px;padding-top:16px;color:#7a717d;font-size:12px}@media(max-width:1050px){.metric-grid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:700px){.metric-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.health-grid{grid-template-columns:1fr}.pager{flex-direction:column;align-items:stretch}.pager span{text-align:center}}`]
})
export class FinanceComponent implements OnInit {
  private readonly api=inject(ApiService);
  overview=signal<FinanceOverview|undefined>(undefined);
  refunds=signal<CursorPage<FinanceRefund>|undefined>(undefined);
  ledger=signal<CursorPage<FinanceLedgerRow>|undefined>(undefined);
  private refundCursor: string | undefined; private ledgerCursor: string | undefined;
  private refundHistory: (string | undefined)[] = []; private ledgerHistory: (string | undefined)[] = [];
  error=''; busy=false; query=''; status='';
  ngOnInit(){this.reload();}
  reload(){this.busy=true;this.error='';this.refundCursor=undefined;this.ledgerCursor=undefined;this.refundHistory=[];this.ledgerHistory=[];this.api.financeOverview().subscribe({next:r=>{this.overview.set(r);this.busy=false;},error:e=>{this.busy=false;this.error=e?.error?.message||'Finance overview could not be loaded.';}});this.loadRefunds(0);this.loadLedger(0);}
  loadRefunds(_page:number=0){this.refundCursor=undefined;this.refundHistory=[];this.fetchRefunds();}
  applyFilters(){this.loadRefunds(0);this.loadLedger(0);}
  private fetchRefunds(){this.api.financeRefundsCursor({q:this.query.trim()||undefined,status:this.status||undefined,cursor:this.refundCursor,size:50}).subscribe({next:r=>this.refunds.set(r),error:e=>this.error=e?.error?.message||'Refunds could not be loaded.'});}
  nextRefunds(){const c=this.refunds()?.nextCursor;if(!c)return;this.refundHistory.push(this.refundCursor);this.refundCursor=c;this.fetchRefunds();}
  previousRefunds(){if(!this.refundHistory.length)return;this.refundCursor=this.refundHistory.pop();this.fetchRefunds();}
  loadLedger(_page:number=0){this.ledgerCursor=undefined;this.ledgerHistory=[];this.fetchLedger();}
  private fetchLedger(){this.api.financeLedgerCursor({q:this.query.trim()||undefined,cursor:this.ledgerCursor,size:50}).subscribe({next:r=>this.ledger.set(r),error:e=>this.error=e?.error?.message||'Financial ledger could not be loaded.'});}
  nextLedger(){const c=this.ledger()?.nextCursor;if(!c)return;this.ledgerHistory.push(this.ledgerCursor);this.ledgerCursor=c;this.fetchLedger();}
  previousLedger(){if(!this.ledgerHistory.length)return;this.ledgerCursor=this.ledgerHistory.pop();this.fetchLedger();}
}
