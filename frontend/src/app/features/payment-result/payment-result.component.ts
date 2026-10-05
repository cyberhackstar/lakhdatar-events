import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink, ActivatedRoute } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { VerifyResponse } from '../../core/api/api.models';
import { BookingStoreService } from '../../shared/booking-store.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';

@Component({
  selector: 'lk-payment-result',
  standalone: true,
  imports: [CommonModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="success-page">
      <div class="success-glow"></div>
      <main *ngIf="result as r; else missing" class="success-card" [class.pending]="isPending(r)">
        <a routerLink="/" class="result-brand" aria-label="Neelastack Events home"><lk-brand-mark label="Events" [height]="48" /></a>
        <ng-container *ngIf="isConfirmed(r); else recoveryState">
          <div class="check">✓</div>
          <div class="eyebrow">Payment confirmed</div>
          <h1>Your evening is booked.</h1>
          <p>Order <strong>{{ r.orderNumber }}</strong> is confirmed. Your digital QR ticket{{ r.tickets.length > 1 ? 's' : '' }} {{ r.tickets.length > 1 ? 'are' : 'is' }} ready.</p>

          <div class="order-chip"><span>ORDER</span><strong>{{ r.orderNumber }}</strong><button type="button" (click)="copyOrder()">{{ copied ? 'Copied' : 'Copy' }}</button></div>

          <div class="ticket-list">
            <div class="ticket-row" *ngFor="let t of r.tickets"><a [routerLink]="['/ticket',t.ticketId]" [fragment]="'access=' + t.accessToken"><span><small>Ticket</small><strong>{{ t.ticketNumber }}</strong></span></a><span class="row-actions"><button type="button" (click)="shareTicket(t.ticketId,t.ticketNumber,t.accessToken)">Share</button><button type="button" (click)="saveTicketPdf(t.ticketId,t.accessToken)">Save PDF</button></span></div>
          </div>
          <div class="actions" *ngIf="r.tickets.length">
            <a class="primary" [routerLink]="['/ticket',r.tickets[0].ticketId]" [fragment]="'access=' + r.tickets[0].accessToken">Open my ticket</a>
            <a routerLink="/recover">Recovery options</a>
          </div>
        </ng-container>
        <ng-template #recoveryState>
          <div class="pending-icon">!</div><div class="eyebrow">{{ isCancelled(r) ? 'Payment not completed' : 'Payment reconciliation' }}</div>
          <h1>{{ isCancelled(r) ? 'Your payment was not completed.' : 'Your payment needs a moment.' }}</h1>
          <p *ngIf="isCancelled(r)">Order <strong>{{ r.orderNumber }}</strong> was returned from the payment gateway without a confirmed payment. <strong>No ticket has been issued for this attempt.</strong></p>
          <p *ngIf="!isCancelled(r)">Order <strong>{{ r.orderNumber }}</strong> was received, but the server has not confirmed a successful payment yet. <strong>Do not make another payment.</strong></p>
          <div class="notice">Your Neelastack order number is <strong>{{ r.orderNumber }}</strong>. Cashfree may show a separate transaction ID. Use either reference together with the checkout email on the recovery page.</div>
          <div class="actions"><a class="primary" routerLink="/recover" [queryParams]="{order:r.orderNumber}">Recovery options</a><a routerLink="/">Back to event</a></div>
        </ng-template>
      </main>
      <ng-template #missing>
        <main class="success-card missing"><a routerLink="/" class="result-brand" aria-label="Neelastack Events home"><lk-brand-mark label="Events" [height]="48" /></a><div class="eyebrow">Payment / ticket recovery</div><h1>Check your order.</h1><p>Return here after a payment interruption or use your order number and checkout email to recover your ticket.</p><a class="primary" routerLink="/recover">Recover my ticket</a></main>
      </ng-template>
    </div>
  `,
  styles: [`
    .success-page{min-height:100vh;color-scheme:dark;background:#08070b;color:#fff;display:grid;place-items:center;padding:24px;position:relative;overflow:hidden}.success-glow{position:absolute;width:460px;height:460px;border-radius:50%;background:rgba(83,161,109,.13);filter:blur(100px)}.success-card{position:relative;z-index:1;width:min(660px,100%);padding:46px;border:1px solid rgba(255,255,255,.09);border-radius:30px;background:rgba(18,15,23,.94);box-shadow:0 40px 120px rgba(0,0,0,.5);backdrop-filter:blur(18px)}.success-card.pending{border-color:rgba(211,164,73,.25)}.success-card.missing{max-width:560px}.result-brand{display:inline-flex;margin-bottom:30px;text-decoration:none}.check,.pending-icon{width:62px;height:62px;border-radius:50%;display:grid;place-items:center;margin-bottom:32px;font-size:30px}.check{background:#214e32;color:#a8e0b9}.pending-icon{background:#4a381c;color:#e7c26c}.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8d8491;font-size:10px;font-weight:800}.success-card h1{font-size:clamp(44px,7vw,66px);letter-spacing:-.06em;line-height:.92;margin:14px 0}.success-card p{color:#9b939f;line-height:1.6;font-size:14px}.order-chip{display:flex;align-items:center;gap:12px;margin:24px 0 6px;padding:12px 13px;border-radius:13px;background:rgba(255,255,255,.045);border:1px solid rgba(255,255,255,.07)}.order-chip span{font-size:8px;letter-spacing:.12em;color:#716a77}.order-chip strong{font-size:13px;flex:1}.order-chip button{border:1px solid rgba(255,255,255,.1);background:transparent;color:#c6bdc9;border-radius:999px;padding:7px 10px;font-size:9px;cursor:pointer}.ticket-list{margin:22px 0 28px;border-top:1px solid rgba(255,255,255,.08)}.ticket-row{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:17px 0;color:#fff;text-decoration:none;border-bottom:1px solid rgba(255,255,255,.08);font-size:11px}.ticket-row small{display:block;color:#766f7c;font-size:9px;text-transform:uppercase;letter-spacing:.1em;margin-bottom:3px}.ticket-row strong{font-size:14px}.ticket-row a{color:#fff;text-decoration:none}.row-actions{display:flex;gap:6px}.row-actions button{border:1px solid rgba(255,255,255,.1);background:transparent;color:#b4a6bb;border-radius:999px;padding:7px 10px;font-size:9px;cursor:pointer}.ticket-row>span:last-child{color:#b4a6bb}.actions{display:flex;gap:18px;align-items:center}.actions a:last-child{color:#8a828d;text-decoration:none;font-size:12px}.primary{display:inline-block;background:#fff;color:#161117;padding:14px 20px;border-radius:999px;text-decoration:none;font-weight:800;font-size:12px}.notice{padding:14px 16px;margin:24px 0;border:1px solid rgba(211,164,73,.18);background:rgba(211,164,73,.07);border-radius:14px;color:#c8b17c;font-size:11px;line-height:1.55}@media(max-width:620px){.success-card{padding:30px}.actions{flex-direction:column;align-items:stretch}.actions .primary{text-align:center}.order-chip{gap:8px}}
  `]
})
export class PaymentResultComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly booking = inject(BookingStoreService);
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  result?: VerifyResponse;
  copied = false;
  private pendingRetryTimer?: ReturnType<typeof setTimeout>;
  private returnAttempt = 0;
  // The API permits five verification calls per order per minute. One initial call +
  // four spaced retries stays within that limit while still covering delayed webhooks.
  private readonly maxReturnAttempts = 4;

  ngOnInit(): void {
    const orderId = this.route.snapshot.queryParamMap.get('order_id');
    if (orderId) {
      this.verifyReturnedOrder(orderId);
      return;
    }
    this.result = this.booking.getPaymentResult() || (history.state?.result as VerifyResponse | undefined);
    if (!this.result) {
      this.router.navigateByUrl('/recover', { replaceUrl: true });
      return;
    }
    this.booking.clear();
  }

  ngOnDestroy(): void {
    if (this.pendingRetryTimer) clearTimeout(this.pendingRetryTimer);
  }

  isConfirmed(result: VerifyResponse): boolean { return result.status === 'CONFIRMED' || result.status === 'COMPLETED'; }
  isCancelled(result: VerifyResponse): boolean { return ['CANCELLED','FAILED','EXPIRED','REFUNDED'].includes(result.status.toUpperCase()); }
  isPending(result: VerifyResponse): boolean { return !this.isConfirmed(result) && !this.isCancelled(result); }

  private verifyReturnedOrder(providerOrderId: string): void {
    this.api.verifyPayment({ providerOrderId }).subscribe({
      next: r => {
        this.result = r;
        this.booking.setPaymentResult(r);
        if (this.isConfirmed(r)) {
          this.booking.clear();
          return;
        }
        if (this.isPending(r) && this.returnAttempt < this.maxReturnAttempts) {
          this.scheduleReturnVerification(providerOrderId);
        }
      },
      error: e => {
        // A provider redirect is not proof of success. Never turn a transient verification error into
        // a forced recovery redirect; keep the order reference visible and let the customer recover it safely.
        const recoveryOrder = this.booking.getRecoveryHint();
        this.result = { orderPublicId: '', orderNumber: recoveryOrder || providerOrderId, status: 'PENDING', tickets: [] };
        if (this.returnAttempt < this.maxReturnAttempts) this.scheduleReturnVerification(providerOrderId);
      }
    });
  }

  private scheduleReturnVerification(providerOrderId: string): void {
    this.returnAttempt += 1;
    if (this.pendingRetryTimer) clearTimeout(this.pendingRetryTimer);
    const delaysMs = [3000, 6000, 10000, 15000];
    const delay = delaysMs[Math.min(this.returnAttempt - 1, delaysMs.length - 1)];
    this.pendingRetryTimer = setTimeout(() => this.verifyReturnedOrder(providerOrderId), delay);
  }


  async shareTicket(ticketId: string, ticketNumber: string, accessToken: string): Promise<void> {
    const url = `${location.origin}/ticket/${encodeURIComponent(ticketId)}#access=${encodeURIComponent(accessToken)}`;
    try {
      if (navigator.share) await navigator.share({ title: `Ticket ${ticketNumber}`, text: `Your Neelastack Events ticket ${ticketNumber}`, url });
      else await navigator.clipboard.writeText(url);
    } catch { /* cancelled / unavailable */ }
  }

  saveTicketPdf(ticketId: string, accessToken: string): void {
    this.api.ticketPdf(ticketId, accessToken).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); const a = document.createElement('a');
        a.href=url; a.download=`${ticketId}.pdf`; a.style.display='none'; document.body.appendChild(a); a.click(); a.remove();
        setTimeout(()=>URL.revokeObjectURL(url),1000);
      },
      error: () => this.printTicketFallback(ticketId, accessToken)
    });
  }

  private printTicketFallback(ticketId: string, accessToken: string): void {
    const url = `${location.origin}/ticket/${encodeURIComponent(ticketId)}?print=1#access=${encodeURIComponent(accessToken)}`;
    window.open(url, '_blank', 'noopener');
  }

  async copyOrder(): Promise<void> {
    if (!this.result) return;
    try { await navigator.clipboard.writeText(this.result.orderNumber); this.copied = true; setTimeout(() => this.copied = false, 1500); } catch { /* clipboard permission is optional */ }
  }
}
