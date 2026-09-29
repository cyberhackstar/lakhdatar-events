import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { BookingStoreService } from '../../shared/booking-store.service';
import { RecoveryResponse } from '../../core/api/api.models';

@Component({
  selector: 'lk-recover',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <div class="recover-page">
      <div class="recover-orbit"></div>
      <main class="recover-card">
        <a routerLink="/" class="back">← All events</a>
        <div class="eyebrow">Ticket recovery</div>
        <h1>Find your ticket.</h1>
        <p>Use the order number from your payment receipt and the same email used at checkout.</p>
        <div class="privacy-note"><span>✓</span><p>Ticket access is revealed only after your order number and checkout email match our records.</p></div>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label for="orderNumber">Order number<input id="orderNumber" formControlName="orderNumber" placeholder="LK-XXXXXXXXXXXX" autocomplete="off" maxlength="15" /></label>
          <label for="email">Email<input id="email" formControlName="email" type="email" placeholder="you@example.com" autocomplete="email" maxlength="255" /></label>
          <div class="error" *ngIf="error" role="alert">{{error}}</div>
          <button [disabled]="form.invalid || loading" type="submit">{{loading?'Checking secure records…':'Recover tickets'}} <span>→</span></button>
        </form>
        <div class="found" *ngIf="result as r">
          <div class="found-head"><strong>{{r.tickets.length}} ticket{{r.tickets.length > 1 ? 's' : ''}} found</strong><span>{{r.eventName}}</span></div>
          <a *ngFor="let t of r.tickets" [routerLink]="['/ticket',t.ticketId]" [fragment]="'access=' + t.accessToken"><span>{{t.ticketNumber}}</span><span>Open →</span></a>
        </div>
        <div class="footer-note">Powered by <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Neelastack</a></div>
      </main>
    </div>
  `,
  styles: [`
    .recover-page{min-height:100vh;background:#08070b;color:#fff;display:grid;place-items:center;padding:22px;position:relative;overflow:hidden}.recover-orbit{position:absolute;width:560px;height:560px;border-radius:50%;border:1px solid rgba(255,255,255,.05);box-shadow:0 0 0 55px rgba(255,255,255,.014),0 0 0 110px rgba(255,255,255,.008)}.recover-card{position:relative;z-index:1;width:min(540px,100%);background:rgba(17,14,23,.95);border:1px solid rgba(255,255,255,.09);border-radius:28px;padding:40px;box-shadow:0 40px 120px rgba(0,0,0,.45);backdrop-filter:blur(18px)}.back{color:#89808d;text-decoration:none;font-size:11px}.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8e8493;font-size:10px;font-weight:800;margin-top:52px}.recover-card h1{font-size:56px;letter-spacing:-.06em;line-height:.94;margin:14px 0 10px}.recover-card>p{color:#8e8692;font-size:13px;line-height:1.65}.privacy-note{display:flex;gap:10px;align-items:flex-start;background:rgba(255,255,255,.035);border:1px solid rgba(255,255,255,.06);border-radius:14px;padding:12px 13px;margin-top:22px}.privacy-note span{color:#8ec39c;font-weight:900}.privacy-note p{margin:0;color:#8e8692;font-size:10px;line-height:1.5}.recover-card form{display:grid;gap:18px;margin-top:26px}.recover-card label{display:grid;gap:8px;font-size:10px;text-transform:uppercase;letter-spacing:.1em;font-weight:800}.recover-card input{height:54px;border:1px solid rgba(255,255,255,.12);background:#100d14;color:#fff;border-radius:13px;padding:0 14px;outline:none}.recover-card input:focus{border-color:#d0a24b;box-shadow:0 0 0 4px rgba(208,162,75,.08)}.recover-card button{height:54px;border:0;border-radius:14px;background:#fff;color:#18131a;font-weight:800;cursor:pointer}.recover-card button:disabled{opacity:.4}.recover-card button span{float:right;font-size:18px}.error{font-size:11px;color:#d98e8e}.found{display:grid;gap:8px;margin-top:26px;padding-top:22px;border-top:1px solid rgba(255,255,255,.08)}.found-head{display:flex;justify-content:space-between;gap:12px;align-items:end;margin-bottom:4px}.found-head strong{font-size:12px}.found-head span{font-size:9px;color:#756d79}.found a{display:flex;justify-content:space-between;gap:12px;padding:13px;border:1px solid rgba(255,255,255,.08);border-radius:12px;color:#fff;text-decoration:none;font-size:11px}.found a span:last-child{color:#a79caa}.footer-note{text-align:center;color:#5f5864;font-size:9px;margin-top:28px}.footer-note a{color:#8a7f90;text-decoration:none;font-weight:800}@media(max-width:560px){.recover-card{padding:30px}.recover-card h1{font-size:48px}.found-head{align-items:flex-start;flex-direction:column}}
  `]
})
export class RecoverComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly booking = inject(BookingStoreService);
  private readonly route = inject(ActivatedRoute);
  form = this.fb.nonNullable.group({
    orderNumber: ['', [Validators.required, Validators.pattern(/^LK-[A-Z0-9]{12}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]]
  });
  loading = false;
  error = '';
  result?: RecoveryResponse;

  ngOnInit(): void {
    const order = this.route.snapshot.queryParamMap.get('order') || this.booking.getRecoveryHint();
    if (order && /^LK-[A-Z0-9]{12}$/i.test(order)) this.form.controls.orderNumber.setValue(order.toUpperCase());
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.error = '';
    this.result = undefined;
    this.loading = true;
    const value = this.form.getRawValue();
    this.api.recover({ orderNumber: value.orderNumber.trim().toUpperCase(), email: value.email.trim().toLowerCase() }).subscribe({
      next: r => { this.result = r; this.loading = false; this.booking.clearRecoveryHint(); },
      error: e => { this.loading = false; this.error = e?.error?.message || 'Order not found or no recoverable tickets are available.'; }
    });
  }
}
