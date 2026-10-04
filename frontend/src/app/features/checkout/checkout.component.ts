import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, EventView, TicketTypeView, VerifyResponse } from '../../core/api/api.models';
import { BookingStoreService } from '../../shared/booking-store.service';
import { environment } from '../../../environments/environment';

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => {
      open(): void;
      on(event: string, handler: (response: any) => void): void;
    };
    Cashfree?: (options: { mode: 'sandbox' | 'production' }) => {
      checkout(options: { paymentSessionId: string; redirectTarget: string }): Promise<any> | any;
    };
  }
}

@Component({
  selector: 'lk-checkout',
  standalone: true,
  imports: [CommonModule, DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <div class="checkout-page">
      <header class="checkout-header">
        <a routerLink="/" class="wordmark" aria-label="Neelastack Events home">NEELASTACK <span>EVENTS</span></a>
        <div class="secure"><span class="secure-dot"></span> SECURE CHECKOUT</div>
      </header>

      <main *ngIf="event as e; else loading" class="checkout-grid">
        <section class="checkout-main">
          <a class="back" [routerLink]="['/events',e.slug]">← Back to event</a>
          <div class="eyebrow">Your details</div>
          <h1>Reserve your evening.</h1>
          <p class="intro">One quick form. Secure payment. Your QR ticket becomes available as soon as payment is confirmed.</p>

          <div class="hold-banner" [class.expired]="reservationExpired" *ngIf="reservationExpiresAt">
            <span class="hold-dot"></span>
            <div>
              <strong>{{ reservationExpired ? 'Reservation window closed' : 'Tickets held for ' + remainingLabel }}</strong>
              <small>{{ reservationExpired ? 'Start checkout again to release a fresh inventory hold.' : 'Your place is temporarily reserved while you complete payment.' }}</small>
            </div>
          </div>

          <form [formGroup]="form" (ngSubmit)="beginPayment()" novalidate>
            <div class="field">
              <label for="customerName">Full name</label>
              <input id="customerName" formControlName="customerName" placeholder="Your full name" autocomplete="name" [class.invalid]="isInvalid('customerName')" />
              <small *ngIf="isInvalid('customerName')">Please enter at least 2 characters.</small>
            </div>
            <div class="field">
              <label for="customerEmail">Email address</label>
              <input id="customerEmail" formControlName="customerEmail" placeholder="you@example.com" type="email" autocomplete="email" [class.invalid]="isInvalid('customerEmail')" />
              <small *ngIf="isInvalid('customerEmail')">Enter a valid email address.</small>
            </div>
            <div class="field">
              <label for="customerPhone">Phone <span>optional</span></label>
              <input id="customerPhone" formControlName="customerPhone" placeholder="10-digit mobile number" inputmode="tel" autocomplete="tel" maxlength="15" />
            </div>
            <label class="consent">
              <input type="checkbox" formControlName="accepted" />
              <span>I agree to the event terms and understand tickets are issued only after successful payment verification.</span>
            </label>

            <div class="checkout-error" *ngIf="error" role="alert">{{ error }}<a *ngIf="recoveryOrderNumber" routerLink="/recover" [queryParams]="{order: recoveryOrderNumber}">Recover this order →</a></div>

            <button class="pay" type="submit" [disabled]="form.invalid || paymentState !== 'idle' || paymentModalOpen || reservationExpired">
              <span>{{ paymentButtonLabel }}</span>
              <span>→</span>
            </button>
            <div class="gateway-note">Payments are processed securely by {{ event?.paymentProvider === 'CASHFREE' ? 'Cashfree' : 'Razorpay' }}. Your card / UPI credentials are never handled by {{ event?.organizer?.name || "the organizer" }} or Neelastack.</div>
            <div class="partner-note">Powered by <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Neelastack</a></div>
          </form>
        </section>

        <aside class="summary">
          <div class="summary-top"><span>YOUR ORDER</span><span>{{ e.currency }}</span></div>
          <div class="summary-kicker">{{ e.name }}</div>
          <div class="summary-date">{{ e.startsAt | date:'EEE, d MMM yyyy' }} · {{ e.startsAt | date:'h:mm a' }}</div>
          <div class="summary-venue">{{ e.venueName || 'Venue' }}<br>{{ e.venueAddress || 'Jaipur, Rajasthan' }}</div>
          <div class="summary-rule"></div>
          <div class="line" *ngFor="let item of lineItems">
            <span>{{ item.type.name }} × {{ item.quantity }}</span>
            <strong>₹{{ item.total / 100 | number:'1.0-0' }}</strong>
          </div>
          <div class="summary-rule"></div>
          <div class="total"><span>Total</span><strong>₹{{ total / 100 | number:'1.0-0' }}</strong></div>
          <div class="mini-trust"><span>✓ Server-verified payment</span><span>✓ Individual QR ticket</span><span>✓ Recoverable order</span></div>
        </aside>
      </main>
    </div>
    <ng-template #loading><div class="loading"><div class="loader-ring"></div><span>Preparing secure checkout…</span></div></ng-template>
  `,
  styles: [`
    .checkout-page{min-height:100vh;background:#f4f0ea;color:#171219}.checkout-header{height:82px;background:#09080d;color:#fff;display:flex;justify-content:space-between;align-items:center;padding:0 clamp(18px,5vw,64px);position:sticky;top:0;z-index:5}.wordmark{color:#fff;text-decoration:none;font-size:14px;font-weight:900;letter-spacing:.12em}.wordmark span{color:#8e8491;font-weight:500}.secure{display:flex;align-items:center;gap:8px;font-size:9px;letter-spacing:.15em;color:#a39aa8}.secure-dot{width:7px;height:7px;border-radius:50%;background:#75b488;box-shadow:0 0 0 5px rgba(117,180,136,.1)}.checkout-grid{max-width:1160px;margin:0 auto;padding:62px 24px 90px;display:grid;grid-template-columns:minmax(0,1fr) 390px;gap:72px}.back{color:#77707a;text-decoration:none;font-size:12px}.eyebrow{text-transform:uppercase;letter-spacing:.16em;font-weight:800;font-size:10px;color:#9a7c39;margin-top:52px}.checkout-main h1{font-size:clamp(50px,6vw,72px);letter-spacing:-.06em;line-height:.94;margin:14px 0 14px}.intro{color:#78717b;max-width:650px;line-height:1.65;font-size:14px;margin:0 0 26px}.hold-banner{display:flex;gap:12px;align-items:center;padding:14px 16px;background:#fffaf1;border:1px solid #ead9b6;border-radius:15px;margin-bottom:24px;color:#6b5530}.hold-banner.expired{background:#fff2f2;border-color:#e6c6c6;color:#7e3d3d}.hold-dot{width:8px;height:8px;border-radius:50%;background:#c69c45;box-shadow:0 0 0 5px rgba(198,156,69,.12);flex:0 0 auto}.hold-banner.expired .hold-dot{background:#a45353;box-shadow:0 0 0 5px rgba(164,83,83,.1)}.hold-banner div{display:grid;gap:3px}.hold-banner strong{font-size:11px}.hold-banner small{font-size:10px;color:#8e7b5f}.hold-banner.expired small{color:#9c7777}.field{display:grid;gap:8px;margin-bottom:20px}.field label{font-size:10px;font-weight:800;text-transform:uppercase;letter-spacing:.1em}.field label span{color:#aaa2a8;font-weight:500;text-transform:none;letter-spacing:0}.field input{height:54px;border:1px solid #dcd4cb;background:#fff;border-radius:14px;padding:0 15px;font:inherit;outline:none;transition:border-color .15s,box-shadow .15s}.field input:focus{border-color:#9b8152;box-shadow:0 0 0 4px rgba(155,129,82,.1)}.field input.invalid{border-color:#c77d7d}.field small{color:#a44d4d;font-size:11px}.consent{display:flex;gap:10px;align-items:flex-start;color:#726b74;font-size:11px;line-height:1.55;margin:20px 0}.consent input{margin-top:3px;accent-color:#181219}.checkout-error{background:#fff0f0;border:1px solid #f0d2d2;color:#8d3e3e;padding:12px 13px;border-radius:12px;font-size:11px;margin-bottom:12px;line-height:1.5}.checkout-error a{display:block;margin-top:7px;color:#733a3a;font-weight:800;text-decoration:none}.pay{width:100%;height:56px;border:0;border-radius:15px;background:#17121a;color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 18px;font-size:13px;font-weight:800;cursor:pointer}.pay:disabled{opacity:.42;cursor:not-allowed}.pay>span:last-child{font-size:18px}.gateway-note{text-align:center;color:#9a929a;font-size:10px;line-height:1.5;margin-top:12px}.partner-note{text-align:center;color:#9a929a;font-size:9px;margin-top:16px}.partner-note a{color:#675d69;font-weight:800;text-decoration:none}.summary{position:sticky;top:112px;align-self:start;background:#141118;color:#fff;border-radius:28px;padding:28px;box-shadow:0 32px 95px rgba(31,22,31,.2)}.summary-top{display:flex;justify-content:space-between;color:#79707d;text-transform:uppercase;font-size:9px;letter-spacing:.14em}.summary-kicker{font-size:24px;font-weight:800;letter-spacing:-.035em;margin-top:20px}.summary-date{font-size:11px;color:#b8afb9;margin-top:8px}.summary-venue{font-size:11px;color:#807782;line-height:1.55;margin-top:22px}.summary-rule{height:1px;background:rgba(255,255,255,.08);margin:23px 0}.line,.total{display:flex;justify-content:space-between;gap:15px}.line{font-size:11px;color:#a69da9;margin:12px 0}.line strong{color:#fff}.total{font-size:20px;font-weight:800}.mini-trust{display:grid;gap:8px;margin-top:23px;color:#786f7e;font-size:10px}.loading{min-height:100vh;display:grid;place-items:center;align-content:center;gap:17px;background:#09080d;color:#918894;font-size:12px}.loader-ring{width:38px;height:38px;border:2px solid rgba(255,255,255,.1);border-top-color:#d4a747;border-radius:50%;animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
    @media(max-width:900px){.checkout-grid{grid-template-columns:1fr;gap:38px;padding-top:40px}.summary{position:static;order:-1}.eyebrow{margin-top:34px}}
    @media(max-width:620px){.checkout-header{height:70px;padding:0 15px}.wordmark{font-size:12px}.secure{font-size:8px}.secure-dot{width:6px;height:6px}.checkout-grid{padding:34px 16px 60px}.checkout-main h1{font-size:48px}.summary{border-radius:22px;padding:22px}.hold-banner{align-items:flex-start}}
  `]
})
export class CheckoutComponent implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly booking = inject(BookingStoreService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  event?: EventView;
  cart: Array<{ ticketTypeId: string; quantity: number }> = [];
  error = '';
  loadingPayment = false;
  paymentModalOpen = false;
  paymentState: 'idle' | 'creating' | 'opening' | 'verifying' = 'idle';
  reservationExpiresAt = '';
  remainingSeconds = 0;
  recoveryOrderNumber = '';
  private countdown?: ReturnType<typeof setInterval>;
  private readonly externalScriptPromises = new Map<string, Promise<void>>();

  readonly form = this.fb.nonNullable.group({
    customerName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    customerEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    customerPhone: ['', [Validators.maxLength(15)]],
    accepted: [false, Validators.requiredTrue]
  });

  ngOnInit(): void {
    this.event = this.booking.getEvent() ?? undefined;
    this.cart = this.booking.getCart();
    const slug = this.route.snapshot.queryParamMap.get('slug');
    if (!this.event && slug) {
      this.api.event(slug).subscribe({
        next: event => { this.event = event; this.booking.setEvent(event); this.validateCart(); },
        error: () => { this.error = 'We could not load this event. Please return to the event page and try again.'; }
      });
    } else {
      this.validateCart();
    }
  }

  ngOnDestroy(): void { this.stopCountdown(); }

  get lineItems(): Array<{ type: TicketTypeView; quantity: number; total: number }> {
    return this.cart.map(item => {
      const type = this.event?.ticketTypes.find(t => t.id === item.ticketTypeId);
      return type ? { type, quantity: item.quantity, total: type.priceMinorUnits * item.quantity } : null;
    }).filter((x): x is { type: TicketTypeView; quantity: number; total: number } => !!x);
  }

  get total(): number { return this.lineItems.reduce((sum, item) => sum + item.total, 0); }
  get reservationExpired(): boolean { return !!this.reservationExpiresAt && this.remainingSeconds <= 0; }
  get remainingLabel(): string {
    const mins = Math.floor(this.remainingSeconds / 60);
    const secs = this.remainingSeconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }

  isInvalid(control: 'customerName' | 'customerEmail'): boolean {
    const c = this.form.controls[control];
    return c.touched && c.invalid;
  }

  get paymentButtonLabel(): string {
    switch (this.paymentState) {
      case 'creating': return 'Preparing secure checkout…';
      case 'opening': return 'Opening secure checkout…';
      case 'verifying': return 'Verifying payment…';
      default: return this.paymentModalOpen ? 'Payment window open…' : this.reservationExpired ? 'Reservation expired' : 'Pay securely';
    }
  }

  beginPayment(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.event || !this.cart.length || this.reservationExpired || this.paymentState !== 'idle') return;
    const validationError = this.validateCart();
    if (validationError) { this.error = validationError; return; }

    this.error = '';
    this.recoveryOrderNumber = '';
    this.paymentModalOpen = false;
    this.paymentState = 'creating';
    this.loadingPayment = true;
    const fingerprint = this.checkoutFingerprint();
    const idempotencyKey = this.booking.getOrCreateCheckoutKey(fingerprint);
    const { customerName, customerEmail, customerPhone } = this.form.getRawValue();

    this.api.checkout({
      eventId: this.event.id,
      customerName: customerName.trim(),
      customerEmail: customerEmail.trim().toLowerCase(),
      customerPhone: customerPhone.trim() || undefined,
      idempotencyKey,
      items: this.cart
    }).subscribe({
      next: response => {
        this.recoveryOrderNumber = response.orderNumber;
        this.booking.setRecoveryHint(response.orderNumber);
        this.preparePayment(response);
      },
      error: e => {
        this.paymentModalOpen = false;
        this.loadingPayment = false;
        this.paymentState = 'idle';
        const code = e?.error?.code;
        if (code === 'RESERVATION_EXPIRED' || code === 'ORDER_CLOSED') this.booking.clearCheckoutSession();
        this.error = e?.error?.message || 'We could not start secure checkout. Please retry.';
      }
    });
  }

  private preparePayment(response: CheckoutResponse): void {
    this.reservationExpiresAt = response.reservationExpiresAt;
    this.startCountdown(response.reservationExpiresAt);
    this.loadingPayment = true;
    this.paymentState = 'opening';

    const open = (): void => {
      if (this.reservationExpired) {
        this.finishProviderFailure('Your reservation expired before payment could start. Please return to the event and choose tickets again.');
        return;
      }

      if (response.provider === 'CASHFREE') {
        this.openCashfreeCheckout(response);
        return;
      }

      if (!window.Razorpay || !response.providerOrderId || !response.providerPublicKey) {
        this.finishProviderFailure('Razorpay secure checkout could not load. Check your connection and retry.');
        return;
      }

      this.paymentModalOpen = true;
      this.loadingPayment = false;
      this.paymentState = 'idle';
      try {
        const razorpay = new window.Razorpay({
          key: response.providerPublicKey, amount: response.amountMinorUnits, currency: response.currency,
          name: this.event?.organizer?.name || this.event?.brand?.organizerName || 'Neelastack Events',
          description: this.event?.name || 'Event ticket', order_id: response.providerOrderId,
          handler: (result: any) => this.verifyPayment(result),
          modal: { ondismiss: () => { this.paymentModalOpen = false; this.paymentState = 'idle'; } },
          prefill: { name: this.form.controls.customerName.value.trim(), email: this.form.controls.customerEmail.value.trim(), contact: this.form.controls.customerPhone.value.trim() },
          theme: { color: '#17121a' }
        });
        razorpay.open();
      } catch {
        this.finishProviderFailure('Secure payment checkout could not open. Please retry.');
      }
    };

    if (response.provider === 'CASHFREE') {
      void this.loadExternalScript('cashfree-checkout-js', environment.cashfreeCheckoutUrl, () => !!window.Cashfree).then(open).catch(() => {
        this.finishProviderFailure('Cashfree secure checkout could not load. Check your connection and retry.');
      });
      return;
    }

    if (window.Razorpay) { open(); return; }
    void this.loadExternalScript('razorpay-checkout-js', environment.razorpayCheckoutUrl, () => !!window.Razorpay).then(open).catch(() => {
      this.finishProviderFailure('Secure payment checkout could not load. Check your connection and retry.');
    });
  }

  private openCashfreeCheckout(response: CheckoutResponse): void {
    const cashfreeFactory = window.Cashfree;
    if (!cashfreeFactory || !response.providerSessionId || !response.providerOrderId) {
      this.finishProviderFailure('Cashfree secure checkout could not load. Check your connection and retry.');
      return;
    }

    try {
      const cashfree = cashfreeFactory({ mode: environment.production ? 'production' : 'sandbox' });
      // Use Cashfree's supported hosted redirect for all browsers. This avoids embedding
      // Cashfree inside a third-party iframe whose own CSP can reject api.cashfree.com,
      // and it also removes the mobile blur/overlay while navigation starts.
      this.paymentModalOpen = false;
      this.loadingPayment = false;
      this.paymentState = 'idle';

      const checkoutResult = cashfree.checkout({
        paymentSessionId: response.providerSessionId,
        redirectTarget: '_self'
      });

      Promise.resolve(checkoutResult).then((result: any) => {
        if (result?.paymentDetails) {
          this.paymentModalOpen = false;
          this.verifyCashfreePayment(response.providerOrderId);
          return;
        }
        if (result?.error) {
          this.paymentModalOpen = false;
          this.finishProviderFailure(result.error.message || 'Cashfree could not complete checkout. You can safely retry or recover this order.');
          return;
        }
        if (result?.redirect) {
          this.paymentModalOpen = false;
          this.finishProviderFailure('Cashfree redirected the payment flow. Do not start another payment; recover this order to check its server-side status.');
          return;
        }
        this.paymentModalOpen = false;
        this.finishProviderFailure('Cashfree checkout was closed before payment was completed. You can safely retry or recover this order.');
      }).catch(() => {
        this.paymentModalOpen = false;
        this.finishProviderFailure('Secure payment checkout could not stay open. Do not make another payment yet; recover this order if needed.');
      });
    } catch {
      this.finishProviderFailure('Secure payment checkout could not open. Please retry or recover this order.');
    }
  }

  private verifyCashfreePayment(providerOrderId: string): void {
    this.loadingPayment = true;
    this.paymentModalOpen = false;
    this.paymentState = 'verifying';
    this.api.verifyPayment({ providerOrderId }).subscribe({
      next: result => this.finishVerifiedPayment(result),
      error: e => {
        this.loadingPayment = false;
        this.paymentState = 'idle';
        this.error = e?.error?.message || 'Payment is awaiting server verification. Do not make another payment; use the recovery link to check the order.';
      }
    });
  }

  private verifyPayment(response: any): void {
    if (!response?.razorpay_order_id || !response?.razorpay_payment_id || !response?.razorpay_signature) {
      this.paymentModalOpen = false;
      this.loadingPayment = false;
      this.paymentState = 'idle';
      this.error='Payment response was incomplete. Do not make another payment yet. Recover the existing order if needed.';
      return;
    }
    this.loadingPayment = true;
    this.paymentModalOpen = false;
    this.paymentState = 'verifying';
    this.api.verifyPayment({ providerOrderId: response.razorpay_order_id, providerPaymentId: response.razorpay_payment_id, providerSignature: response.razorpay_signature }).subscribe({
      next: result => this.finishVerifiedPayment(result),
      error: e => {
        this.loadingPayment = false;
        this.paymentState = 'idle';
        this.error=e?.error?.message||'Payment is awaiting server verification. Do not make another payment.';
      }
    });
  }

  private finishVerifiedPayment(result: VerifyResponse): void {
    this.booking.setPaymentResult(result);
    this.booking.clearCheckoutSession();
    this.booking.clear();
    this.stopCountdown();
    this.loadingPayment = false;
    this.paymentState = 'idle';
    this.router.navigateByUrl('/payment/success',{replaceUrl:true});
  }

  private finishProviderFailure(message: string): void {
    this.paymentModalOpen = false;
    this.loadingPayment = false;
    this.paymentState = 'idle';
    this.error = message;
  }

  private loadExternalScript(id: string, src: string, isReady: () => boolean, timeoutMs = 15000): Promise<void> {
    if (isReady()) return Promise.resolve();
    const pending = this.externalScriptPromises.get(id);
    if (pending) return pending;

    const promise = new Promise<void>((resolve, reject) => {
      let settled = false;
      const script = document.getElementById(id) as HTMLScriptElement | null ?? document.createElement('script');
      const created = !script.parentElement;
      let pollTimer: ReturnType<typeof setInterval> | undefined;
      let timeoutTimer: ReturnType<typeof setTimeout> | undefined;

      const cleanup = () => {
        script.removeEventListener('load', onLoad);
        script.removeEventListener('error', onError);
        if (pollTimer) clearInterval(pollTimer);
        if (timeoutTimer) clearTimeout(timeoutTimer);
      };
      const succeed = () => {
        if (settled) return;
        if (!isReady()) return;
        settled = true;
        cleanup();
        resolve();
      };
      const fail = () => {
        if (settled) return;
        settled = true;
        cleanup();
        if (script.id === id && script.parentElement) script.remove();
        reject(new Error(`Payment SDK timed out or failed to load: ${id}`));
      };
      const onLoad = () => succeed();
      const onError = () => fail();

      if (!script.src) {
        script.id = id;
        script.src = src;
        script.async = true;
      }
      script.addEventListener('load', onLoad, { once: true });
      script.addEventListener('error', onError, { once: true });
      if (created) document.body.appendChild(script);

      // Existing script tags may have fired load before this checkout component mounted.
      // Polling the provider global makes the loader deterministic instead of waiting forever.
      pollTimer = setInterval(() => { if (isReady()) succeed(); }, 100);
      timeoutTimer = setTimeout(fail, timeoutMs);
      succeed();
    });

    this.externalScriptPromises.set(id, promise);
    return promise.finally(() => this.externalScriptPromises.delete(id));
  }

  private validateCart(): string | null {
    if (!this.event || !this.cart.length) {
      this.error = 'Your cart is empty. Please choose tickets again.';
      return this.error;
    }
    const now = Date.now();
    for (const item of this.cart) {
      const type = this.event.ticketTypes.find(t => t.id === item.ticketTypeId);
      if (!type) return 'One of your selected ticket types is no longer available. Please return to the event page.';
      const quantity = item.quantity;
      if (quantity < type.minPerOrder || quantity > type.maxPerOrder || quantity > type.availableQuantity) {
        return `${type.name} is no longer available in the selected quantity. Please review your ticket selection.`;
      }
      if (type.status !== 'ACTIVE') return `${type.name} is currently unavailable.`;
      if (type.saleStartsAt && new Date(type.saleStartsAt).getTime() > now) return `${type.name} is not on sale yet.`;
      if (type.saleEndsAt && new Date(type.saleEndsAt).getTime() <= now) return `${type.name} sales have closed.`;
    }
    this.booking.setCart(this.cart);
    return null;
  }

  private checkoutFingerprint(): string {
    const email = this.form.controls.customerEmail.value.trim().toLowerCase();
    const phone = this.form.controls.customerPhone.value.trim();
    const cart = [...this.cart].sort((a, b) => a.ticketTypeId.localeCompare(b.ticketTypeId)).map(x => `${x.ticketTypeId}:${x.quantity}`).join('|');
    return `${this.event?.id || ''}|${cart}|${email}|${phone}`;
  }

  private startCountdown(expiresAt: string): void {
    this.stopCountdown();
    const tick = () => {
      this.remainingSeconds = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
      if (this.remainingSeconds <= 0) this.stopCountdown();
    };
    tick();
    this.countdown = setInterval(tick, 1000);
  }

  private stopCountdown(): void {
    if (this.countdown) clearInterval(this.countdown);
    this.countdown = undefined;
  }
}
