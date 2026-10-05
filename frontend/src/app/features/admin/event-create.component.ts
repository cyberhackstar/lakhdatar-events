import { DecimalPipe } from '@angular/common';
import { Component, OnInit, effect, inject } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { DEFAULT_TZ, slugify, toIsoInZone } from '../../core/datetime';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';

@Component({
  selector: 'lk-event-create',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe],
  template: `
    <a class="back" routerLink="/admin/events">← All events</a>
    <div class="page-head">
      <div>
        <div class="eyebrow">New event</div>
        <h1 class="title">Create an event</h1>
        <p class="sub">Build the event shell first. It is saved as a draft: nothing is public until you publish it from the event editor.</p>
      </div>
    </div>

    <form class="card" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <h2>Basics</h2>
      <div class="grid">
        @if (store.organizers().length) {
          <label class="field wide"><span>Organizer <span class="req">*</span></span>
            <select formControlName="organizerSlug" [class.invalid]="organizerMissing()">
              @if (store.organizers().length > 1) { <option value="">Choose the organizer…</option> }
              @for (o of store.organizers(); track o.slug) { <option [value]="o.slug">{{ o.name }}</option> }
            </select>
            @if (organizerMissing()) { <span class="err">Choose which organizer this event belongs to.</span> }
          </label>
        }
        <label class="field"><span>Event name <span class="req">*</span></span>
          <input formControlName="name" placeholder="Dandiya Night 2026" autocomplete="off" [class.invalid]="bad('name')" (input)="onNameInput()" />
          @if (bad('name')) { <span class="err">Enter a name of 3–180 characters.</span> }
        </label>
        <label class="field"><span>URL slug <span class="req">*</span></span>
          <input formControlName="slug" placeholder="dandiya-night-2026" autocomplete="off" autocapitalize="none" spellcheck="false" [class.invalid]="bad('slug')" (input)="slugTouched = true" />
          @if (bad('slug')) { <span class="err">Lowercase letters, numbers and single hyphens only.</span> }
          @else { <small>Public link: /events/{{ form.controls.slug.value || 'your-event' }}</small> }
        </label>
        <label class="field"><span>Starts <span class="req">*</span></span>
          <input type="datetime-local" formControlName="startsAt" [class.invalid]="bad('startsAt')" />
          @if (bad('startsAt')) { <span class="err">Pick both a date and a time.</span> } @else { <small>India Standard Time (Asia/Kolkata)</small> }
        </label>
        <label class="field"><span>Ends <small>(optional)</small></span>
          <input type="datetime-local" formControlName="endsAt" />
          <small>For multi-day events, booking can remain open through this end time.</small>
        </label>
        <label class="field"><span>Booking starts <small>(optional)</small></span>
          <input type="datetime-local" formControlName="bookingStartsAt" />
          <small>Leave blank to allow sales immediately after publication.</small>
        </label>
        <label class="field"><span>Booking ends <small>(optional)</small></span>
          <input type="datetime-local" formControlName="bookingEndsAt" />
          <small>Leave blank to close automatically at the event end.</small>
        </label>
        <label class="field">Category
          <input formControlName="category" list="categories" placeholder="Concert, Festival, Wedding…" autocomplete="off" />
          <datalist id="categories"><option value="Concert"></option><option value="Festival"></option><option value="Garba / Dandiya"></option><option value="Wedding"></option><option value="Conference"></option><option value="Workshop"></option><option value="Comedy"></option></datalist>
        </label>
        <label class="field">Payment provider
          <select formControlName="paymentProvider"><option value="RAZORPAY">Razorpay</option><option value="CASHFREE">Cashfree</option></select>
          <small>Must be configured on the server. Locked once payments start.</small>
        </label>
        <label class="field wide">Description
          <textarea formControlName="description" rows="4" placeholder="What guests should know about the event"></textarea>
        </label>
      </div>

      <h2 style="margin-top:28px">Venue</h2>
      <div class="grid">
        <label class="field"><span>Venue name <span class="req">*</span></span>
          <input formControlName="venueName" placeholder="Venue name" autocomplete="off" [class.invalid]="bad('venueName')" />
          @if (bad('venueName')) { <span class="err">Enter the venue name.</span> }
        </label>
        <label class="field">City
          <input formControlName="city" placeholder="Jaipur" autocomplete="off" />
        </label>
        <label class="field wide">Venue address
          <input formControlName="venueAddress" placeholder="Full venue address" autocomplete="off" />
        </label>
      </div>

      <div class="tickets-head">
        <h2>Ticket inventory</h2>
        <span class="muted">Prices in ₹ INR · allocated <b [class.over]="over()">{{ allocated() | number }}</b> of
          <input class="cap" type="number" min="1" formControlName="capacity" aria-label="Event capacity" /> seats</span>
      </div>
      <p class="hint">Capacity is the overall limit for the event and is fixed after creation, so leave headroom if you plan to add ticket types later.</p>

      <div formArrayName="ticketTypes" class="ticket-list">
        @for (g of tickets.controls; track g; let i = $index) {
          <div class="ticket" [formGroupName]="i">
            <label class="cell name">Ticket name
              <input class="cell-input" formControlName="name" placeholder="General Entry" autocomplete="off" [class.invalid]="badCell(g, 'name')" />
            </label>
            <label class="cell">Price (₹)
              <input class="cell-input" formControlName="priceRupees" type="number" min="1" step="1" inputmode="numeric" placeholder="599" [class.invalid]="badCell(g, 'priceRupees')" />
            </label>
            <label class="cell">Quantity
              <input class="cell-input" formControlName="totalQuantity" type="number" min="1" inputmode="numeric" placeholder="1000" [class.invalid]="badCell(g, 'totalQuantity')" />
            </label>
            <label class="cell">Min / order
              <input class="cell-input" formControlName="minPerOrder" type="number" min="1" max="20" inputmode="numeric" [class.invalid]="badCell(g, 'minPerOrder')" />
            </label>
            <label class="cell">Max / order
              <input class="cell-input" formControlName="maxPerOrder" type="number" min="1" max="20" inputmode="numeric" [class.invalid]="badCell(g, 'maxPerOrder')" />
            </label>
            <button type="button" class="remove" (click)="removeTicket(i)" [disabled]="tickets.length <= 1" aria-label="Remove ticket type" title="Remove ticket type">×</button>
          </div>
        }
      </div>
      <button type="button" class="a-btn add" (click)="addTicket()">＋ Add ticket type</button>

      @if (error) { <div class="alert error" role="alert">{{ error }}</div> }

      <div class="submit-row">
        <a class="a-btn" routerLink="/admin/events">Cancel</a>
        <button class="a-btn primary big" type="submit" [disabled]="creating">{{ creating ? 'Creating…' : 'Create draft event' }} <span>→</span></button>
      </div>
    </form>
  `,
  styles: [ADMIN_UI_STYLES, `
    .back{display:inline-block;margin-bottom:18px;color:#6b6270;text-decoration:none;font-size:13px}.back:hover{color:var(--ink)}
    .grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.grid .wide{grid-column:1/-1}
    .tickets-head{display:flex;justify-content:space-between;align-items:flex-end;gap:12px;flex-wrap:wrap;margin-top:28px;padding-bottom:6px;border-bottom:1px solid #eee8e0}
    .tickets-head h2{margin:0}
    .muted{color:var(--muted);font-size:13px;display:inline-flex;align-items:center;gap:6px;flex-wrap:wrap}.muted b.over{color:#a24b4b}
    .cap{width:96px;min-height:34px;border:1px solid #d9d2c8;border-radius:9px;padding:0 8px;font:inherit;font-size:16px;text-align:center;background:#fff;color:var(--ink);color-scheme:light}
    .hint{margin:10px 0 14px!important;font-size:12px!important;color:#8a8190!important}
    .ticket-list{display:grid;gap:10px}
    .ticket{display:grid;grid-template-columns:minmax(0,2.2fr) minmax(0,1fr) minmax(0,1fr) minmax(0,.7fr) minmax(0,.7fr) 44px;gap:10px;align-items:end;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}
    .cell{display:grid;gap:6px;font-size:12px;font-weight:700;color:#4a414d;min-width:0}
    .remove{height:46px;border:1px solid #d8d0c7;border-radius:12px;background:#fff;color:#8f3e42;font-size:22px;line-height:1;cursor:pointer}
    .remove:disabled{opacity:.35;cursor:not-allowed}
    .add{margin-top:12px;border-style:dashed}
    .submit-row{display:flex;justify-content:flex-end;gap:10px;margin-top:26px;flex-wrap:wrap}
    .big{min-width:240px;justify-content:space-between;min-height:52px}
    @media(max-width:900px){.ticket{grid-template-columns:1fr 1fr}.ticket .name{grid-column:1/-1}.remove{grid-column:1/-1;height:42px;font-size:14px}.remove::after{content:' Remove ticket type';font-size:13px;font-weight:700}}
    @media(max-width:700px){.grid{grid-template-columns:1fr}.big{width:100%}.submit-row .a-btn{flex:1 1 100%}}
  `]
})
export class EventCreateComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  submitted = false;
  creating = false;
  error = '';
  slugTouched = false;

  readonly form = this.fb.nonNullable.group({
    organizerSlug: [''],
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(180)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/), Validators.maxLength(160)]],
    category: ['General', Validators.maxLength(120)],
    startsAt: ['', Validators.required],
    endsAt: [''],
    bookingStartsAt: [''],
    bookingEndsAt: [''],
    venueName: ['', [Validators.required, Validators.maxLength(255)]],
    city: ['Jaipur', Validators.maxLength(120)],
    venueAddress: ['', Validators.maxLength(500)],
    description: ['', Validators.maxLength(5000)],
    capacity: [5000, [Validators.required, Validators.min(1), Validators.max(1000000)]],
    paymentProvider: ['CASHFREE'],
    ticketTypes: this.fb.array([this.ticketGroup('General Entry', 599, 1000)])
  });

  get tickets(): FormArray { return this.form.controls.ticketTypes; }

  constructor() {
    // Pre-select the organizer as soon as the list arrives and there is only one.
    effect(() => {
      const list = this.store.organizers();
      if (list.length === 1 && !this.form.controls.organizerSlug.value) this.form.controls.organizerSlug.setValue(list[0].slug);
    });
  }

  ngOnInit(): void { this.store.loadOrganizers(); }

  private ticketGroup(name: string, price: number, qty: number) {
    return this.fb.nonNullable.group({
      name: [name, [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
      priceRupees: [price, [Validators.required, Validators.min(1), Validators.max(10000000)]],
      totalQuantity: [qty, [Validators.required, Validators.min(1), Validators.max(1000000)]],
      minPerOrder: [1, [Validators.required, Validators.min(1), Validators.max(20)]],
      maxPerOrder: [10, [Validators.required, Validators.min(1), Validators.max(20)]]
    });
  }

  addTicket(): void { this.tickets.push(this.ticketGroup('', 999, 100)); }
  removeTicket(i: number): void { if (this.tickets.length > 1) this.tickets.removeAt(i); }

  onNameInput(): void {
    if (!this.slugTouched) this.form.controls.slug.setValue(slugify(this.form.controls.name.value), { emitEvent: false });
  }

  bad(field: 'name' | 'slug' | 'startsAt' | 'venueName'): boolean {
    const c = this.form.controls[field]; return c.invalid && (c.touched || this.submitted);
  }
  badCell(group: AbstractControl, field: string): boolean {
    const c = group.get(field); return !!c && c.invalid && (c.touched || this.submitted);
  }
  organizerMissing(): boolean { return this.submitted && this.store.organizers().length > 1 && !this.form.controls.organizerSlug.value; }
  allocated(): number { return this.tickets.controls.reduce((s, g) => s + (Number(g.get('totalQuantity')?.value) || 0), 0); }
  over(): boolean { return this.allocated() > (Number(this.form.controls.capacity.value) || 0); }

  submit(): void {
    this.submitted = true; this.error = '';
    this.form.markAllAsTouched();
    if (this.form.invalid || this.organizerMissing()) { this.error = 'Some details are missing or invalid. Please fix the fields marked in red.'; return; }
    const v = this.form.getRawValue();
    const startsAt = toIsoInZone(v.startsAt, DEFAULT_TZ);
    const endsAt = v.endsAt ? toIsoInZone(v.endsAt, DEFAULT_TZ) : undefined;
    const bookingStartsAt = v.bookingStartsAt ? toIsoInZone(v.bookingStartsAt, DEFAULT_TZ) : undefined;
    const bookingEndsAtInput = v.bookingEndsAt ? toIsoInZone(v.bookingEndsAt, DEFAULT_TZ) : undefined;
    const bookingEndsAt = bookingEndsAtInput || (endsAt || undefined);
    if (!startsAt || (v.endsAt && !endsAt) || (v.bookingStartsAt && !bookingStartsAt) || (v.bookingEndsAt && !bookingEndsAtInput)) { this.error = 'Enter valid event and booking times.'; return; }
    const effectiveEventEnd = endsAt ?? startsAt;
    if (new Date(startsAt).getTime() < Date.now() - 5 * 60_000) { this.error = 'The event start time is in the past.'; return; }
    if (endsAt && new Date(endsAt).getTime() <= new Date(startsAt).getTime()) { this.error = 'Event end time must be after the start time.'; return; }
    if (bookingStartsAt && new Date(bookingStartsAt).getTime() >= new Date(startsAt).getTime()) { this.error = 'Booking must start before the event begins.'; return; }
    if (bookingStartsAt && bookingEndsAt && new Date(bookingEndsAt).getTime() <= new Date(bookingStartsAt).getTime()) { this.error = 'Booking end must be after booking start.'; return; }
    if (bookingEndsAt && effectiveEventEnd && new Date(bookingEndsAt).getTime() > new Date(effectiveEventEnd).getTime()) { this.error = 'Booking can remain open only until the event ends.'; return; }
    const tickets = v.ticketTypes.map((t: any) => ({
      name: String(t.name).trim(), description: '', priceMinorUnits: Math.round(Number(t.priceRupees) * 100),
      totalQuantity: Number(t.totalQuantity), minPerOrder: Number(t.minPerOrder), maxPerOrder: Number(t.maxPerOrder)
    }));
    if (tickets.some(t => t.maxPerOrder < t.minPerOrder)) { this.error = 'Maximum tickets per order must be at least the minimum.'; return; }
    if (this.over()) { this.error = `Ticket quantities (${this.allocated()}) exceed the event capacity (${v.capacity}). Raise the capacity or reduce the quantities.`; return; }

    this.creating = true;
    const body = {
      organizerSlug: v.organizerSlug || undefined, slug: v.slug, name: v.name.trim(), description: v.description || '',
      category: v.category.trim() || 'General', startsAt, endsAt, timezone: DEFAULT_TZ, capacity: Number(v.capacity),
      bookingStartsAt, bookingEndsAt,
      venueName: v.venueName.trim(), venueAddress: v.venueAddress || '', city: v.city || '',
      paymentProvider: v.paymentProvider, ticketTypes: tickets
    };
    this.api.createEvent(body).subscribe({
      next: created => {
        this.creating = false;
        this.store.load(true);
        this.router.navigate(['/admin/events', created.id], { queryParams: { created: 1 } });
      },
      error: e => { this.creating = false; this.error = e?.error?.message || 'Could not create the event. Please try again.'; }
    });
  }
}
