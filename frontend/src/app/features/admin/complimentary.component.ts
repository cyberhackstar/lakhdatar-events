import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { ManagerTicketIssueResponse, ManagerTicketType } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';

/** Event managers issue free tickets that consume real inventory and are permanently attributed to the issuing manager. */
@Component({
  selector: 'lk-complimentary',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head">
      <div>
        <div class="eyebrow">Complimentary access</div>
        <h1 class="title">Issue manager tickets</h1>
        <p class="sub">Free tickets use real inventory and are permanently marked with the issuing manager. You can issue tickets only for events assigned to you.</p>
      </div>
    </div>

    @if (!events().length && !store.loading()) { <div class="alert info">No events are assigned to you yet. Ask an administrator to assign you to an event.</div> }

    <section class="card">
      <form [formGroup]="form" (ngSubmit)="issue()" novalidate>
        <div class="grid">
          <label class="field">Event
            <select formControlName="eventId" (change)="onEventChange($any($event.target).value)" [class.invalid]="bad('eventId')"><option value="">Choose your event</option>@for (e of events(); track e.id) { <option [value]="e.id">{{ e.name }}</option> }</select>
          </label>
          <label class="field">Ticket type
            <select formControlName="ticketTypeId" [class.invalid]="bad('ticketTypeId')"><option value="">Choose a ticket type</option>@for (t of types(); track t.id) { <option [value]="t.id">{{ t.name }} · {{ t.availableQuantity }} left</option> }</select>
          </label>
          <label class="field">Quantity<input formControlName="quantity" type="number" min="1" max="100" inputmode="numeric" [class.invalid]="bad('quantity')" /></label>
          <label class="field">Attendee name<input formControlName="attendeeName" placeholder="Guest full name" autocomplete="off" [class.invalid]="bad('attendeeName')" /></label>
          <label class="field">Attendee email<input formControlName="attendeeEmail" type="email" placeholder="guest@example.com" autocomplete="off" [class.invalid]="bad('attendeeEmail')" /></label>
          <label class="field"><span>Phone <small>(optional)</small></span><input formControlName="attendeePhone" type="tel" placeholder="+91 98765 43210" autocomplete="off" inputmode="tel" /></label>
        </div>
        <button class="a-btn primary" type="submit" [disabled]="busy()">{{ busy() ? 'Issuing…' : 'Issue free ticket' }} ↗</button>
      </form>

      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      @if (message()) { <div class="alert success" role="status">{{ message() }}</div> }
      @if (result(); as r) {
        <div class="result">
          <div>Issued {{ r.quantity }} × {{ r.ticketType }} · ₹0 · <strong>{{ r.issuedByName }}</strong></div>
          @for (t of r.tickets; track t.ticketId) {
            <div class="t"><b>{{ t.ticketNumber }}</b><a [href]="ticketUrl(t)" target="_blank" rel="noopener">Open QR ↗</a></div>
          }
        </div>
      }
    </section>
  `,
  styles: [ADMIN_UI_STYLES, `
    .grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}
    form .a-btn{margin-top:18px;min-width:240px;justify-content:space-between;min-height:48px}
    .result{margin-top:16px;padding:14px;border-radius:12px;background:#f2efe8;color:#5c4e30;font-size:14px;line-height:1.55;display:grid;gap:8px}
    .t{display:flex;justify-content:space-between;gap:12px;padding:10px 12px;background:#fff;border:1px solid #e6dfd3;border-radius:10px}.t a{color:#7a5a12;font-weight:800;text-decoration:none}
    @media(max-width:700px){.grid{grid-template-columns:1fr}form .a-btn{width:100%}}
  `]
})
export class ComplimentaryComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  events = () => this.store.dash()?.events ?? [];
  readonly types = signal<ManagerTicketType[]>([]);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly error = signal('');
  readonly result = signal<ManagerTicketIssueResponse | undefined>(undefined);

  readonly form = this.fb.nonNullable.group({
    eventId: ['', Validators.required], ticketTypeId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1), Validators.max(100)]],
    attendeeName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    attendeeEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    attendeePhone: ['', Validators.maxLength(40)],
    idempotencyKey: [this.newKey(), [Validators.required, Validators.minLength(16), Validators.maxLength(100)]]
  });

  ngOnInit(): void { this.store.load(); }

  private newKey(): string { return `MGR-${globalThis.crypto.randomUUID()}`; }
  bad(field: 'eventId' | 'ticketTypeId' | 'quantity' | 'attendeeName' | 'attendeeEmail'): boolean { const c = this.form.controls[field]; return c.invalid && c.touched; }
  ticketUrl(t: { ticketId: string; accessToken: string }): string { return `/ticket/${encodeURIComponent(t.ticketId)}#access=${encodeURIComponent(t.accessToken)}`; }

  onEventChange(eventId: string): void {
    this.types.set([]); this.form.controls.ticketTypeId.setValue('');
    if (!eventId) return;
    this.api.adminTicketTypes(eventId).subscribe({
      next: list => this.types.set(list.filter(t => t.status !== 'CLOSED' && t.availableQuantity > 0)),
      error: e => this.error.set(e?.error?.message || 'Could not load ticket types for this event.')
    });
  }

  issue(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error.set('Choose an event and ticket type, and enter the attendee name and email.'); return; }
    this.busy.set(true); this.message.set(''); this.error.set(''); this.result.set(undefined);
    const v = this.form.getRawValue();
    this.api.issueComplimentaryTicket(v).subscribe({
      next: r => {
        this.busy.set(false); this.result.set(r); this.message.set(`Complimentary ticket(s) issued for ${r.eventName}.`);
        this.form.patchValue({ attendeeName: '', attendeeEmail: '', attendeePhone: '', quantity: 1, idempotencyKey: this.newKey() });
        this.form.controls.attendeeName.markAsUntouched(); this.form.controls.attendeeEmail.markAsUntouched();
        this.onEventChange(v.eventId); this.form.controls.eventId.setValue(v.eventId);
        this.store.load(true);
      },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'Could not issue complimentary tickets.'); }
    });
  }
}
