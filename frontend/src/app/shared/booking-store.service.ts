import { Injectable } from '@angular/core';
import { EventView, VerifyResponse } from '../core/api/api.models';

interface CheckoutSession { fingerprint: string; key: string; createdAt: number; }
interface RecoveryHint { orderNumber: string; createdAt: number; }

@Injectable({ providedIn: 'root' })
export class BookingStoreService {
  private readonly eventKey = 'lk_booking_event';
  private readonly cartKey = 'lk_booking_cart';
  private readonly checkoutKey = 'lk_checkout_session';
  private readonly recoveryHintKey = 'lk_recovery_hint';
  private readonly recoveryHintTtlMs = 7 * 24 * 60 * 60 * 1000;
  private paymentResult?: VerifyResponse;
  private readonly checkoutTtlMs = 25 * 60 * 1000;

  setEvent(event: EventView): void { try { sessionStorage.setItem(this.eventKey, JSON.stringify(event)); } catch { /* session storage is optional */ } }

  getEvent(): EventView | null {
    try {
      const value = JSON.parse(sessionStorage.getItem(this.eventKey) || 'null');
      return value && typeof value.id === 'string' && typeof value.slug === 'string' ? value : null;
    } catch { return null; }
  }

  setCart(cart: Array<{ ticketTypeId: string; quantity: number }>): void {
    try { sessionStorage.setItem(this.cartKey, JSON.stringify(cart)); } catch { /* session storage is optional */ }
  }

  getCart(): Array<{ ticketTypeId: string; quantity: number }> {
    try {
      const value = JSON.parse(sessionStorage.getItem(this.cartKey) || '[]');
      if (!Array.isArray(value)) return [];
      return value.filter((x: any) => typeof x?.ticketTypeId === 'string' && Number.isInteger(x?.quantity) && x.quantity > 0);
    } catch { return []; }
  }

  getOrCreateCheckoutKey(fingerprint: string): string {
    const existing = this.getCheckoutKey(fingerprint);
    if (existing) return existing;
    const bytes = new Uint8Array(24);
    crypto.getRandomValues(bytes);
    const key = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    try { sessionStorage.setItem(this.checkoutKey, JSON.stringify({ fingerprint, key, createdAt: Date.now() } satisfies CheckoutSession)); } catch { /* caller can still complete a single checkout attempt */ }
    return key;
  }

  getCheckoutKey(fingerprint: string): string | null {
    try {
      const value = JSON.parse(sessionStorage.getItem(this.checkoutKey) || 'null') as CheckoutSession | null;
      if (!value || value.fingerprint !== fingerprint || typeof value.key !== 'string') return null;
      if (!Number.isFinite(value.createdAt) || Date.now() - value.createdAt > this.checkoutTtlMs) {
        this.clearCheckoutSession();
        return null;
      }
      return value.key;
    } catch { return null; }
  }

  setPaymentResult(result: VerifyResponse): void {
    this.paymentResult = result;
  }

  getPaymentResult(): VerifyResponse | null {
    const value = this.paymentResult;
    return value && typeof value.orderNumber === 'string' && Array.isArray(value.tickets) ? value : null;
  }

  clearCheckoutSession(): void { try { sessionStorage.removeItem(this.checkoutKey); } catch { /* session storage is optional */ } }

  setRecoveryHint(orderNumber: string): void {
    if (!/^LK-[A-Z0-9]{12}$/.test(orderNumber)) return;
    try { localStorage.setItem(this.recoveryHintKey, JSON.stringify({ orderNumber, createdAt: Date.now() } satisfies RecoveryHint)); } catch { /* optional persistence */ }
  }

  getRecoveryHint(): string | null {
    try {
      const value = JSON.parse(localStorage.getItem(this.recoveryHintKey) || 'null') as RecoveryHint | null;
      if (!value || !/^LK-[A-Z0-9]{12}$/.test(value.orderNumber) || !Number.isFinite(value.createdAt) || Date.now() - value.createdAt > this.recoveryHintTtlMs) {
        localStorage.removeItem(this.recoveryHintKey);
        return null;
      }
      return value.orderNumber;
    } catch { return null; }
  }

  clearRecoveryHint(): void { try { localStorage.removeItem(this.recoveryHintKey); } catch { /* optional persistence */ } }

  clear(): void {
    try { sessionStorage.removeItem(this.eventKey); } catch { /* optional */ }
    try { sessionStorage.removeItem(this.cartKey); } catch { /* optional */ }
    try { sessionStorage.removeItem(this.checkoutKey); } catch { /* optional */ }
  }

  clearPaymentResult(): void { this.paymentResult = undefined; }
}
