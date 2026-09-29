import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { TicketView } from '../../core/api/api.models';
import { CoBrandedHeaderComponent } from '../../core/branding/co-branded-header.component';

@Component({
  selector: 'lk-ticket',
  standalone: true,
  imports: [CommonModule, DatePipe, RouterLink, CoBrandedHeaderComponent],
  template: `
    <div class="ticket-page" *ngIf="ticket as t; else status">
      <lk-co-branded-header [brand]="t.brand"></lk-co-branded-header>
      <main>
        <div class="eyebrow">Digital admission pass</div>
        <div class="title-row"><div><h1>{{ t.eventName }}</h1><p>Keep this pass ready at the gate. The QR credential is unique to this ticket.</p></div><span class="valid-badge" [class.used]="t.status==='CHECKED_IN'" [class.invalid]="isInvalid(t.status)">{{statusLabel(t.status)}}</span></div>
        <div class="ticket-stage">
          <section class="ticket-main">
            <div class="ticket-event-line"><span>{{ t.startsAt | date:'d MMMM yyyy' }}</span><span>{{ t.startsAt | date:'h:mm a' }}</span><span>{{ t.venueName || 'Venue' }}</span></div>
            <div class="ticket-content">
              <div class="ticket-copy"><div class="ticket-type">{{ t.ticketType }}</div><span class="manager-badge" *ngIf="t.source === 'COMPLIMENTARY_MANAGER'">COMPLIMENTARY · MANAGER ISSUED<span *ngIf="t.issuedByName"> · {{t.issuedByName}}</span></span><div class="attendee">{{ t.attendeeName || 'Guest' }}</div><div class="ticket-no">Ticket {{ t.ticketNumber }}</div></div>
              <div class="qr-wrap"><img [src]="t.qrDataUri" alt="Secure entry QR code" draggable="false"/><small>Present this code at the gate</small></div>
            </div>
            <div class="cut-line"><span></span><span>{{ (t.brand.organizerName || 'EVENT TICKET') | uppercase }}</span><span></span></div>
            <div class="ticket-foot">
              <div><small>Venue</small><strong>{{ t.venueName || 'Venue' }}</strong><span>{{ t.venueAddress || '' }}</span></div>
              <div class="ticket-note"><small>Entry status</small><strong>{{statusLabel(t.status)}}</strong><span *ngIf="t.checkedInAt">Checked in {{ t.checkedInAt | date:'d MMM, h:mm a' }}</span><span *ngIf="!t.checkedInAt">Valid until event check-in closes.</span></div>
            </div>
            <div class="ticket-partner">Powered by <a [href]="t.brand.technologyPartnerUrl" target="_blank" rel="noopener noreferrer">{{ t.brand.technologyPartnerName }}</a></div>
          </section>
        </div>
        <div class="ticket-actions"><button type="button" (click)="print()">Print / Save PDF</button><a routerLink="/recover">Recovery options</a><a routerLink="/">Back to event</a></div>
      </main>
    </div>
    <ng-template #status><div class="ticket-status"><div class="status-mark">!</div><div class="eyebrow">Secure ticket access</div><h1>{{ error ? 'Ticket unavailable.' : 'Loading your ticket…' }}</h1><p *ngIf="error">This ticket link is invalid, expired, or no longer available. Retrieve it again from your order using the recovery page.</p><a *ngIf="error" routerLink="/recover" class="status-link">Recover my ticket →</a></div></ng-template>
  `,
  styles: [`
    .ticket-page{min-height:100vh;background:#efebe5;color:#151116}.ticket-page main{max-width:1040px;margin:0 auto;padding:60px 22px 80px}.eyebrow{text-transform:uppercase;letter-spacing:.16em;color:#9b7d3c;font-weight:800;font-size:10px}.title-row{display:flex;justify-content:space-between;align-items:end;gap:28px;margin:13px 0 34px}.ticket-page h1{font-size:clamp(48px,7vw,72px);letter-spacing:-.06em;line-height:.93;margin:0}.title-row p{color:#8a828a;font-size:12px;max-width:610px;line-height:1.6;margin:12px 0 0}.valid-badge{flex:0 0 auto;padding:10px 13px;border-radius:999px;background:#e8f4e9;color:#46724f;font-size:9px;font-weight:800;text-transform:uppercase;letter-spacing:.08em}.valid-badge.used{background:#e9e8e9;color:#6d6870}.valid-badge.invalid{background:#fae9e9;color:#9a3e43}.ticket-stage{background:#19141b;border-radius:32px;padding:18px;box-shadow:0 35px 100px rgba(24,17,27,.2)}.ticket-main{background:#fff;border-radius:22px;color:#171219;padding:30px;break-inside:avoid;page-break-inside:avoid}.ticket-event-line{display:flex;justify-content:space-between;gap:12px;font-size:10px;text-transform:uppercase;letter-spacing:.1em;color:#827883;padding-bottom:22px;border-bottom:1px solid #e8e1da}.ticket-content{display:flex;justify-content:space-between;align-items:center;gap:30px;padding:38px 12px}.ticket-copy{min-width:0}.ticket-type{display:inline-flex;padding:8px 10px;border-radius:999px;background:#efe7d7;color:#8d6f32;font-size:9px;text-transform:uppercase;letter-spacing:.14em;font-weight:800}.manager-badge{display:block;width:max-content;max-width:100%;margin-top:10px;padding:7px 10px;border-radius:999px;background:#2c2230;color:#bca8cf;font-size:8px;font-weight:900;letter-spacing:.08em;text-transform:uppercase;line-height:1.35}.attendee{font-size:clamp(34px,5vw,52px);letter-spacing:-.05em;margin-top:28px;word-break:break-word}.ticket-no{font-size:11px;color:#8a818b;margin-top:7px}.qr-wrap{display:grid;gap:8px;text-align:center}.qr-wrap img{width:min(255px,34vw);height:min(255px,34vw);min-width:190px;min-height:190px;image-rendering:auto}.qr-wrap small{font-size:9px;color:#8a818b}.cut-line{display:flex;align-items:center;gap:12px;color:#a0959f;font-size:8px;letter-spacing:.12em}.cut-line span:first-child,.cut-line span:last-child{height:1px;background:#e7dfd7;flex:1}.ticket-foot{margin-top:30px;padding-top:24px;border-top:1px solid #e8e1da;display:grid;grid-template-columns:1fr 1fr;gap:24px}.ticket-foot small{display:block;color:#998f97;font-size:9px;text-transform:uppercase;letter-spacing:.1em;margin-bottom:5px}.ticket-foot strong{display:block;font-size:12px}.ticket-foot span{display:block;color:#928995;font-size:10px;margin-top:5px}.ticket-note{text-align:right}.ticket-partner{text-align:right;margin-top:24px;padding-top:18px;border-top:1px dashed #ddd4cc;color:#948b94;font-size:9px}.ticket-partner a{color:#5e5561;text-decoration:none;font-weight:800}.ticket-actions{display:flex;gap:20px;align-items:center;margin-top:25px}.ticket-actions button{background:#171219;color:#fff;border:0;border-radius:999px;padding:14px 19px;font-weight:800;cursor:pointer}.ticket-actions a{color:#746d77;text-decoration:none;font-size:12px}.ticket-status{min-height:100vh;background:#09080c;color:#fff;display:grid;place-items:center;align-content:center;padding:24px;text-align:center}.ticket-status h1{font-size:50px;letter-spacing:-.05em;margin:14px 0}.ticket-status p{max-width:470px;color:#968d9a;font-size:13px;line-height:1.6}.status-mark{width:62px;height:62px;border-radius:50%;display:grid;place-items:center;background:#3c2f1b;color:#d9b766;font-size:28px;margin-bottom:22px}.status-link{margin-top:20px;background:#fff;color:#171219;padding:14px 20px;border-radius:999px;text-decoration:none;font-weight:800;font-size:12px}@media(max-width:760px){.ticket-page main{padding:45px 13px 65px}.title-row{align-items:flex-start;flex-direction:column}.ticket-page h1{font-size:48px}.ticket-content{flex-direction:column;align-items:flex-start}.qr-wrap{align-self:center}.qr-wrap img{width:220px;height:220px}.ticket-event-line{flex-direction:column}.ticket-foot{grid-template-columns:1fr}.ticket-note{text-align:left}.ticket-actions{flex-direction:column;align-items:stretch}.ticket-actions button{width:100%}.ticket-status h1{font-size:40px}}
    @media print{.ticket-page{background:#fff}.ticket-stage{box-shadow:none;padding:0}.ticket-page main{padding:0}.ticket-actions,.brand-header{display:none!important}.ticket-main{box-shadow:none!important}}
  `]
})
export class TicketComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  ticket?: TicketView;
  error = false;
  loading = true;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('ticketId') || '';
    const fragment = this.route.snapshot.fragment || '';
    let token = fragment.startsWith('access=') ? this.decodeFragmentToken(fragment.slice(7)) : '';
    if (!id || !token || token.length > 512) { this.loading = false; this.error = true; return; }
    // Remove bearer credentials from the visible URL/history after the first successful navigation.
    if (fragment.startsWith('access=')) {
      const clean = `${location.pathname}${location.search}`;
      history.replaceState(history.state, '', clean);
    }
    this.api.ticket(id, token).subscribe({
      next: t => { this.ticket = t; this.loading = false; this.error = false; document.title = `${t.ticketNumber} · ${t.eventName} · ${t.brand.organizerName || 'Neelastack Events'}`; },
      error: () => { this.loading = false; this.error = true; }
    });
  }

  private decodeFragmentToken(value: string): string {
    try { return decodeURIComponent(value).trim(); } catch { return ''; }
  }

  statusLabel(status: string): string {
    switch (status) {
      case 'CHECKED_IN': return 'Checked in';
      case 'CANCELLED': return 'Cancelled';
      case 'REFUNDED': return 'Refunded';
      case 'VOID': return 'Voided';
      default: return 'Valid for entry';
    }
  }

  isInvalid(status: string): boolean { return status === 'CANCELLED' || status === 'REFUNDED' || status === 'VOID'; }
  print(): void { window.print(); }
}
