import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api/api.service';
import { OperationsHealth } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';

@Component({
  selector: 'lk-operations-health', standalone: true, imports: [CommonModule, DatePipe],
  template: `
  <div class="page-head"><div><div class="eyebrow">Platform operations</div><h1 class="title">Operations health</h1><p class="sub">Live infrastructure, payment recovery, webhook, reservation and email backlogs.</p></div><button class="a-btn sm" (click)="reload()" [disabled]="loading()">{{loading()?'Refreshing…':'Refresh'}}</button></div>
  @if(error()){<div class="alert" role="alert">{{error()}}</div>}
  @if(data(); as d){
    <div class="grid"><div class="metric"><small>PostgreSQL</small><strong [class.bad]="d.database.status!=='UP'">{{d.database.status}}</strong><span>{{d.database.latencyMs}}ms</span></div><div class="metric"><small>Redis</small><strong [class.bad]="d.redis.status!=='UP'">{{d.redis.status}}</strong><span>{{d.redis.latencyMs}}ms</span></div><div class="metric"><small>Worker tier</small><strong>{{d.workerEnabled?'Enabled':'API-only'}}</strong><span>Dedicated workers supported</span></div><div class="metric"><small>Published events</small><strong>{{d.publishedEvents}}</strong><span>{{d.organizers}} organizers</span></div></div>
    <section class="card"><h2>Recovery queues</h2><div class="qgrid"><div><small>Pending payments</small><b>{{d.queues.pendingPayments}}</b></div><div><small>Stale payments &gt;2m</small><b>{{d.queues.stalePayments}}</b></div><div><small>Provider-order recovery</small><b>{{d.queues.providerOrderRecoveryPending}}</b></div><div><small>Pending refunds</small><b>{{d.queues.pendingRefunds}}</b></div><div><small>Webhook backlog</small><b>{{d.queues.webhookBacklog}}</b></div><div><small>Webhook stuck</small><b>{{d.queues.webhookStuck}}</b></div><div><small>Held reservations</small><b>{{d.queues.heldReservations}}</b></div><div><small>Expired reservations</small><b>{{d.queues.expiredReservations}}</b></div><div><small>Mail pending</small><b>{{d.queues.mailPending}}</b></div><div><small>Mail failed</small><b>{{d.queues.mailFailed}}</b></div><div><small>Event notices pending</small><b>{{d.queues.eventNotificationPending}}</b></div><div><small>Event notices failed</small><b>{{d.queues.eventNotificationFailed}}</b></div></div></section>
    <p class="stamp">Checked {{d.checkedAt|date:'d MMM yyyy, h:mm:ss a'}}</p>
  } @else if(loading()){<div class="card">Loading platform health…</div>}
  `,
  styles: [ADMIN_UI_STYLES, `.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:16px}.metric{background:#fff;border:1px solid var(--line);border-radius:14px;padding:18px}.metric small{display:block;text-transform:uppercase;letter-spacing:.1em;color:#8b828d;font-size:9px}.metric strong{display:block;margin-top:8px;font-size:20px;color:#3f7048}.metric strong.bad{color:#9b3434}.metric span{display:block;margin-top:5px;color:#8a8190;font-size:10px}.card h2{margin:0 0 14px;font-size:18px}.qgrid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}.qgrid>div{padding:14px;border:1px solid var(--line);border-radius:12px;background:#faf8f5}.qgrid small{display:block;color:#8b828d;font-size:9px;text-transform:uppercase;letter-spacing:.08em}.qgrid b{display:block;margin-top:7px;font-size:18px}.alert{margin-bottom:14px;padding:12px 14px;background:#fff0f0;border:1px solid #f1d1d1;border-radius:12px;color:#8c4141;font-size:12px}.stamp{color:#8a8190;font-size:10px}@media(max-width:1000px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}.qgrid{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:650px){.grid,.qgrid{grid-template-columns:1fr}.metric{padding:14px}}`]
})
export class OperationsHealthComponent implements OnInit {
  private readonly api=inject(ApiService); readonly data=signal<OperationsHealth|undefined>(undefined); readonly loading=signal(false); readonly error=signal('');
  ngOnInit(){this.reload();}
  reload(){this.loading.set(true);this.error.set('');this.api.operationsHealth().subscribe({next:r=>{this.data.set(r);this.loading.set(false);},error:e=>{this.loading.set(false);this.error.set(e?.error?.message||'Operations health could not be loaded.');}});}
}
