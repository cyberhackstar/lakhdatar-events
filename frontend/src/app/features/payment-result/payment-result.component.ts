import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { VerifyResponse } from '../../core/api/api.models';
import { BookingStoreService } from '../../shared/booking-store.service';

@Component({
  selector: 'lk-payment-result',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="success-page">
      <div class="success-glow"></div>
      <main *ngIf="result as r; else missing" class="success-card" [class.pending]="isPending(r)">
        <ng-container *ngIf="isConfirmed(r); else recoveryState">
          <div class="check">✓</div>
          <div class="eyebrow">Payment confirmed</div>
          <h1>Your evening is booked.</h1>
          <p>Order <strong>{{ r.orderNumber }}</strong> is confirmed. Your digital QR ticket{{ r.tickets.length > 1 ? 's' : '' }} {{ r.tickets.length > 1 ? 'are' : 'is' }} ready.</p>

          <div class="order-chip"><span>ORDER</span><strong>{{ r.orderNumber }}</strong><button type="button" (click)="copyOrder()">{{ copied ? 'Copied' : 'Copy' }}</button></div>

          <div class="ticket-list">
            <a class="ticket-row" *ngFor="let t of r.tickets" [routerLink]="['/ticket',t.ticketId]" [fragment]="'access=' + t.accessToken">
              <span><small>Ticket</small><strong>{{ t.ticketNumber }}</strong></span><span>View ticket&nbsp; →</span>
            </a>
          </div>
          <div class="actions" *ngIf="r.tickets.length">
            <a class="primary" [routerLink]="['/ticket',r.tickets[0].ticketId]" [fragment]="'access=' + r.tickets[0].accessToken">Open my ticket</a>
            <a routerLink="/recover">Recovery options</a>
          </div>
        </ng-container>
        <ng-template #recoveryState>
          <div class="pending-icon">!</div><div class="eyebrow">Payment reconciliation</div>
          <h1>Your payment needs a moment.</h1>
          <p>Order <strong>{{ r.orderNumber }}</strong> was received, but ticket issuance is waiting for the server-side payment state to settle. <strong>Do not make another payment.</strong></p>
          <div class="notice">You can use the order number and checkout email on the recovery page. The system will keep reconciling the payment safely.</div>
          <div class="actions"><a class="primary" routerLink="/recover">Check order status</a><a routerLink="/">Back to event</a></div>
        </ng-template>
      </main>
      <ng-template #missing>
        <main class="success-card missing"><div class="eyebrow">Payment / ticket recovery</div><h1>Check your order.</h1><p>Return here after a payment interruption or use your order number and checkout email to recover your ticket.</p><a class="primary" routerLink="/recover">Recover my ticket</a></main>
      </ng-template>
    </div>
  `,
  styles: [`
    .success-page{min-height:100vh;background:#08070b;color:#fff;display:grid;place-items:center;padding:24px;position:relative;overflow:hidden}.success-glow{position:absolute;width:460px;height:460px;border-radius:50%;background:rgba(83,161,109,.13);filter:blur(100px)}.success-card{position:relative;z-index:1;width:min(660px,100%);padding:46px;border:1px solid rgba(255,255,255,.09);border-radius:30px;background:rgba(18,15,23,.94);box-shadow:0 40px 120px rgba(0,0,0,.5);backdrop-filter:blur(18px)}.success-card.pending{border-color:rgba(211,164,73,.25)}.success-card.missing{max-width:560px}.check,.pending-icon{width:62px;height:62px;border-radius:50%;display:grid;place-items:center;margin-bottom:32px;font-size:30px}.check{background:#214e32;color:#a8e0b9}.pending-icon{background:#4a381c;color:#e7c26c}.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8d8491;font-size:10px;font-weight:800}.success-card h1{font-size:clamp(44px,7vw,66px);letter-spacing:-.06em;line-height:.92;margin:14px 0}.success-card p{color:#9b939f;line-height:1.6;font-size:14px}.order-chip{display:flex;align-items:center;gap:12px;margin:24px 0 6px;padding:12px 13px;border-radius:13px;background:rgba(255,255,255,.045);border:1px solid rgba(255,255,255,.07)}.order-chip span{font-size:8px;letter-spacing:.12em;color:#716a77}.order-chip strong{font-size:13px;flex:1}.order-chip button{border:1px solid rgba(255,255,255,.1);background:transparent;color:#c6bdc9;border-radius:999px;padding:7px 10px;font-size:9px;cursor:pointer}.ticket-list{margin:22px 0 28px;border-top:1px solid rgba(255,255,255,.08)}.ticket-row{display:flex;justify-content:space-between;align-items:center;padding:17px 0;color:#fff;text-decoration:none;border-bottom:1px solid rgba(255,255,255,.08);font-size:11px}.ticket-row small{display:block;color:#766f7c;font-size:9px;text-transform:uppercase;letter-spacing:.1em;margin-bottom:3px}.ticket-row strong{font-size:14px}.ticket-row>span:last-child{color:#b4a6bb}.actions{display:flex;gap:18px;align-items:center}.actions a:last-child{color:#8a828d;text-decoration:none;font-size:12px}.primary{display:inline-block;background:#fff;color:#161117;padding:14px 20px;border-radius:999px;text-decoration:none;font-weight:800;font-size:12px}.notice{padding:14px 16px;margin:24px 0;border:1px solid rgba(211,164,73,.18);background:rgba(211,164,73,.07);border-radius:14px;color:#c8b17c;font-size:11px;line-height:1.55}@media(max-width:620px){.success-card{padding:30px}.actions{flex-direction:column;align-items:stretch}.actions .primary{text-align:center}.order-chip{gap:8px}}
  `]
})
export class PaymentResultComponent implements OnInit {
  private readonly router = inject(Router);
  private readonly booking = inject(BookingStoreService);
  result?: VerifyResponse;
  copied = false;

  ngOnInit(): void {
    this.result = this.booking.getPaymentResult() || (history.state?.result as VerifyResponse | undefined);
    if (!this.result) {
      this.router.navigateByUrl('/recover', { replaceUrl: true });
      return;
    }
    this.booking.clear();
  }

  isConfirmed(result: VerifyResponse): boolean { return result.status === 'CONFIRMED' || result.status === 'COMPLETED'; }
  isPending(result: VerifyResponse): boolean { return !this.isConfirmed(result); }

  async copyOrder(): Promise<void> {
    if (!this.result) return;
    try { await navigator.clipboard.writeText(this.result.orderNumber); this.copied = true; setTimeout(() => this.copied = false, 1500); } catch { /* clipboard permission is optional */ }
  }
}
