import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges, inject } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { map, Subscription } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { AdminEventView } from '../../core/api/api.models';

@Component({
  selector: 'lk-event-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <section class="editor" id="event-editor" *ngIf="!loading && event">
      <div class="editor-head">
        <div>
          <div class="eyebrow">Event editor</div>
          <h2>{{ event.name }}</h2>
          <p class="editor-sub">/{{ event.slug }} · {{ event.organizerName }} · {{ event.status }}</p>
        </div>
        <div class="editor-actions">
          <a class="ghost" [href]="'/events/' + event.slug" target="_blank" rel="noopener">View public page ↗</a>
          <button type="button" class="ghost" (click)="closed.emit()">Close</button>
        </div>
      </div>

      <div class="editor-message error" *ngIf="error" role="alert">{{ error }}</div>
      <div class="editor-message success" *ngIf="success" role="status">{{ success }}</div>

      <form [formGroup]="form" (ngSubmit)="saveEvent()" novalidate>
        <div class="editor-section">
          <div class="section-title"><span>01</span><div><h3>Identity</h3><p>Public-facing event information.</p></div></div>
          <div class="grid two">
            <label>Event name<input formControlName="name" autocomplete="off" /></label>
            <label>Category<input formControlName="category" placeholder="Concert, Wedding, Festival…" /></label>
            <label class="wide">Short description<input formControlName="shortDescription" maxlength="500" /></label>
            <label class="wide">Full description<textarea formControlName="description" rows="5"></textarea></label>
          </div>
        </div>

        <div class="editor-section">
          <div class="section-title"><span>02</span><div><h3>Schedule & booking</h3><p>Times are stored as instants and displayed in the selected event timezone.</p></div></div>
          <div class="grid two">
            <label>Timezone
              <select formControlName="timezone">
                <option value="Asia/Kolkata">Asia/Kolkata</option>
                <option value="Asia/Dubai">Asia/Dubai</option>
                <option value="Asia/Singapore">Asia/Singapore</option>
                <option value="Europe/London">Europe/London</option>
                <option value="Europe/Paris">Europe/Paris</option>
                <option value="America/New_York">America/New_York</option>
                <option value="America/Los_Angeles">America/Los_Angeles</option>
                <option value="Australia/Sydney">Australia/Sydney</option>
              </select>
            </label>
            <label>Event capacity <input formControlName="capacity" type="number" readonly aria-readonly="true" /><small>Capacity is fixed after creation for inventory safety.</small></label>
            <label>Starts<input formControlName="startsAt" type="datetime-local" /></label>
            <label>Ends<input formControlName="endsAt" type="datetime-local" /></label>
            <label>Booking starts<input formControlName="bookingStartsAt" type="datetime-local" /></label>
            <label>Booking ends<input formControlName="bookingEndsAt" type="datetime-local" /></label>
          </div>
        </div>

        <div class="editor-section">
          <div class="section-title"><span>03</span><div><h3>Venue</h3><p>Guest-facing location and navigation data.</p></div></div>
          <div class="grid two">
            <label>Venue name<input formControlName="venueName" /></label>
            <label>City<input formControlName="city" /></label>
            <label>State<input formControlName="state" /></label>
            <label>Map URL<input formControlName="mapUrl" inputmode="url" autocapitalize="none" /></label>
            <label class="wide">Venue address<textarea formControlName="venueAddress" rows="3"></textarea></label>
          </div>
        </div>

        <div class="editor-section">
          <div class="section-title"><span>04</span><div><h3>Media & highlights</h3><p>One URL or highlight per line.</p></div></div>
          <div class="grid two">
            <label>Cover image URL<input formControlName="coverImageUrl" inputmode="url" autocapitalize="none" /></label>
            <label>Age restriction<input formControlName="ageRestriction" placeholder="18+ / Family friendly" /></label>
            <label class="wide">Gallery URLs<textarea formControlName="gallery" rows="5" placeholder="https://…\nhttps://…"></textarea></label>
            <label class="wide">Highlights<textarea formControlName="highlights" rows="5" placeholder="Live DJ\nPremium seating\nFood & beverages"></textarea></label>
          </div>
        </div>

        <div class="editor-section">
          <div class="section-title"><span>05</span><div><h3>Policies</h3><p>Customer-facing terms and cancellation information.</p></div></div>
          <div class="grid two">
            <label class="wide">Terms & conditions<textarea formControlName="terms" rows="5"></textarea></label>
            <label class="wide">Refund / cancellation policy<textarea formControlName="refundPolicy" rows="5"></textarea></label>
          </div>
        </div>

        <div class="editor-section">
          <div class="section-title"><span>06</span><div><h3>Presentation & payments</h3><p>Configure the event brand and payment gateway without exposing provider secrets.</p></div></div>
          <div class="grid three">
            <label>Payment provider<select formControlName="paymentProvider"><option value="RAZORPAY">Razorpay</option><option value="CASHFREE">Cashfree</option></select><small>Changing after payment activity is blocked.</small></label>
            <label>Brand display<select formControlName="brandingMode"><option value="BOTH">Logo + text</option><option value="LOGO_ONLY">Logo only</option><option value="TEXT_ONLY">Text only</option></select></label>
            <label class="toggle"><input type="checkbox" formControlName="featured" /><span>Featured event</span></label>
            <label>Display order<input formControlName="displayOrder" type="number" min="0" /></label>
            <div class="read-only-brand"><span>Organizer</span><strong>{{ event.organizerName }}</strong><small>Platform owner: Neelastack</small></div>
          </div>
          <div class="asset-grid">
            <div class="asset-card"><div><strong>Organizer logo</strong><small>Updates the organizer identity across its events.</small></div><button type="button" class="small-action" (click)="chooseFile('ORGANIZER_LOGO')">Upload</button></div>
            <div class="asset-card"><div><strong>Event logo</strong><small>Displayed as the event-specific mark.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_LOGO')">Upload</button></div>
            <div class="asset-card"><div><strong>Event banner</strong><small>Premium hero/banner artwork.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_BANNER')">Upload</button></div>
            <div class="asset-card"><div><strong>Event cover</strong><small>Used by cards and catalogue surfaces.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_COVER')">Upload</button></div>
          </div>
          <input #assetInput type="file" accept="image/jpeg,image/png" hidden (change)="uploadSelected($event)" />
        </div>

        <div class="editor-section tickets">
          <div class="section-title"><span>07</span><div><h3>Ticket inventory</h3><p>Edit ticket tiers without changing historical orders.</p></div></div>
          <div class="ticket-editor-list" formArrayName="ticketTypes">
            <article class="ticket-editor" *ngFor="let group of ticketForms.controls; let i = index" [formGroupName]="i">
              <div class="ticket-top">
                <div><strong>{{ group.get('name')?.value || 'New ticket' }}</strong><small *ngIf="group.get('id')?.value">{{ group.get('id')?.value }}</small></div>
                <select formControlName="status" aria-label="Ticket status">
                  <option value="ACTIVE">ACTIVE</option><option value="PAUSED">PAUSED</option><option value="CLOSED">CLOSED</option>
                </select>
              </div>
              <div class="grid ticket-grid">
                <label>Name<input formControlName="name" /></label>
                <label>Price (₹)<input formControlName="priceRupees" type="number" min="0" step="1" /></label>
                <label>Total inventory<input formControlName="totalQuantity" type="number" min="1" /></label>
                <label>Min / order<input formControlName="minPerOrder" type="number" min="1" max="20" /></label>
                <label>Max / order<input formControlName="maxPerOrder" type="number" min="1" max="20" /></label>
                <label>Sale starts<input formControlName="saleStartsAt" type="datetime-local" /></label>
                <label>Sale ends<input formControlName="saleEndsAt" type="datetime-local" /></label>
                <label class="wide">Description<textarea formControlName="description" rows="2"></textarea></label>
              </div>
              <div class="ticket-stats">
                <span>Sold <b>{{group.get('soldQuantity')?.value || 0}}</b></span>
                <span>Reserved <b>{{group.get('reservedQuantity')?.value || 0}}</b></span>
                <span>Available <b>{{group.get('availableQuantity')?.value || 0}}</b></span>
                <button type="button" class="small-action" (click)="saveTicket(i)" [disabled]="savingTicketIndex===i || group.invalid">{{savingTicketIndex===i?'Saving…':(group.get('id')?.value?'Save ticket':'Add ticket')}}</button>
              </div>
            </article>
          </div>
          <button type="button" class="add-ticket" (click)="addTicket()">+ Add ticket type</button>
        </div>

        <div class="editor-footer">
          <div class="lifecycle">
            <span class="state">{{event.status}}</span>
            <button type="button" *ngIf="event.status==='DRAFT' || event.status==='UNPUBLISHED'" (click)="transition('publish')">Publish</button>
            <button type="button" *ngIf="event.status==='PUBLISHED'" (click)="transition('unpublish')">Unpublish</button>
            <button type="button" class="danger" *ngIf="event.status==='DRAFT' || event.status==='PUBLISHED' || event.status==='UNPUBLISHED'" (click)="transition('cancel')">Cancel event</button>
            <button type="button" *ngIf="event.status==='PUBLISHED'" (click)="transition('complete')">Complete</button>
            <button type="button" *ngIf="event.status!=='PUBLISHED' && event.status!=='ARCHIVED'" (click)="transition('archive')">Archive</button>
          </div>
          <button class="save-event" type="submit" [disabled]="saving || form.invalid">{{saving?'Saving…':'Save event changes'}} <span>→</span></button>
        </div>
      </form>
    </section>

    <div class="editor-loading" *ngIf="loading">Loading event editor…</div>
  `,
  styles: [`
    .asset-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:16px}.asset-card{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px;border:1px solid rgba(255,255,255,.08);border-radius:14px;background:rgba(255,255,255,.025)}.asset-card strong{display:block;font-size:13px}.asset-card small{display:block;color:#8f8794;font-size:11px;margin-top:4px;line-height:1.4}@media(max-width:720px){.asset-grid{grid-template-columns:1fr}}
    :host{display:block}.editor{margin-top:18px;background:#fff;border:1px solid #e5dfd7;border-radius:26px;overflow:hidden;box-shadow:0 18px 55px rgba(33,24,31,.07)}
    .editor-head{display:flex;justify-content:space-between;gap:20px;padding:28px;border-bottom:1px solid #eee8e0;background:linear-gradient(145deg,#fffdf9,#f8f3eb)}
    .editor-head h2{margin:7px 0 5px;font-size:32px;letter-spacing:-.045em;color:#211923}.editor-sub{margin:0;color:#8a818b;font-size:11px}.editor-actions{display:flex;gap:8px;align-items:flex-start}.ghost{border:1px solid #ddd5cb;background:#fff;color:#403843;border-radius:11px;padding:10px 12px;font-size:10px;font-weight:800;text-decoration:none;cursor:pointer}
    .editor-message{margin:16px 24px 0;padding:11px 13px;border-radius:11px;font-size:11px}.editor-message.error{background:#fff0f0;color:#8d3f43}.editor-message.success{background:#edf7ef;color:#416b4b}
    .editor form{display:block}.editor-section{padding:26px 28px;border-bottom:1px solid #eee8e0}.section-title{display:grid;grid-template-columns:34px 1fr;gap:12px;align-items:start;margin-bottom:18px}.section-title>span{display:grid;place-items:center;width:28px;height:28px;border-radius:9px;background:#17121a;color:#fff;font-size:9px;font-weight:800}.section-title h3{margin:0 0 3px;font-size:19px;letter-spacing:-.02em}.section-title p{margin:0;color:#918992;font-size:10px;line-height:1.5}
    .grid{display:grid;gap:12px}.grid.two{grid-template-columns:1fr 1fr}.grid.three{grid-template-columns:1fr .65fr 1fr}.grid .wide,.grid label.wide{grid-column:1/-1}.grid label{display:grid;gap:7px;color:#625965;font-size:9px;font-weight:800;letter-spacing:.08em;text-transform:uppercase}.grid label small{font-size:9px;text-transform:none;letter-spacing:0;color:#9a929a;font-weight:500}.grid input,.grid select,.grid textarea{width:100%;box-sizing:border-box;border:1px solid #dcd4cb;background:#fff;border-radius:11px;padding:11px 12px;color:#211923;outline:0;font:inherit;font-size:16px;line-height:1.35;letter-spacing:0;text-transform:none}.grid textarea{resize:vertical;min-height:82px}.grid input:focus,.grid select:focus,.grid textarea:focus{border-color:#9e8150;box-shadow:0 0 0 3px rgba(158,129,80,.09)}.grid input[readonly]{background:#f7f4ef;color:#847b84}.toggle{display:flex!important;align-items:center;grid-template-columns:none!important;gap:10px!important;min-height:44px}.toggle input{width:20px;height:20px;accent-color:#17121a}.toggle span{text-transform:none;letter-spacing:0;font-size:11px}
    .read-only-brand{border:1px dashed #d6cec4;background:#faf7f3;border-radius:12px;padding:12px;display:grid;gap:4px;align-content:center}.read-only-brand span,.read-only-brand small{font-size:9px;color:#938a92}.read-only-brand strong{font-size:12px}
    .ticket-editor-list{display:grid;gap:14px}.ticket-editor{border:1px solid #e2dbd3;border-radius:18px;padding:18px;background:#fcfaf7}.ticket-top{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:14px}.ticket-top strong{display:block;font-size:15px}.ticket-top small{display:block;color:#9a9198;font-size:8px;margin-top:3px}.ticket-top select{border:1px solid #dcd4cb;border-radius:9px;background:#fff;padding:8px 10px;font-size:16px}.ticket-grid{grid-template-columns:1.35fr .75fr .8fr .65fr .65fr}.ticket-stats{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:14px;padding-top:12px;border-top:1px solid #e7e0d8}.ticket-stats span{font-size:9px;color:#867d86}.ticket-stats b{color:#2b242c}.small-action,.add-ticket{border:1px solid #d8d0c7;background:#fff;color:#342d35;border-radius:10px;padding:9px 12px;font-size:10px;font-weight:800;cursor:pointer}.small-action{margin-left:auto}.small-action:disabled{opacity:.45}.add-ticket{margin-top:12px;border-style:dashed}
    .editor-footer{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:20px 28px;background:#faf8f5}.lifecycle{display:flex;align-items:center;gap:7px;flex-wrap:wrap}.lifecycle .state{padding:8px 10px;border-radius:999px;background:#ece6dc;color:#5b5045;font-size:9px;font-weight:800}.lifecycle button{border:1px solid #d8d0c7;background:#fff;border-radius:9px;padding:9px 11px;font-size:9px;font-weight:800;cursor:pointer}.lifecycle button.danger{color:#8f3e42;border-color:#ebcdcf}.save-event{min-width:190px;border:0;background:#17121a;color:#fff;border-radius:12px;padding:14px 16px;font-weight:800;cursor:pointer}.save-event:disabled{opacity:.45}.editor-loading{text-align:center;padding:34px;color:#8b828c;font-size:11px}
    @media(max-width:900px){.grid.two,.grid.three{grid-template-columns:1fr}.ticket-grid{grid-template-columns:1fr 1fr}.editor-footer{align-items:stretch;flex-direction:column}.save-event{width:100%}.small-action{margin-left:0}.editor-actions{flex-wrap:wrap;justify-content:flex-end}}
    @media(max-width:600px){.editor-head{padding:22px 16px;display:block}.editor-head h2{font-size:26px}.editor-actions{margin-top:14px;justify-content:stretch}.editor-actions>*{flex:1;text-align:center}.editor-section{padding:22px 16px}.section-title{grid-template-columns:30px 1fr}.ticket-grid{grid-template-columns:1fr}.ticket-top{align-items:flex-start}.editor-footer{padding:18px 16px}.lifecycle{display:grid;grid-template-columns:1fr 1fr}.lifecycle .state{grid-column:1/-1;text-align:center}.lifecycle button{min-height:42px}.editor .grid input,.editor .grid select,.editor .grid textarea{font-size:16px}}
  `]
})
export class EventEditorComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) eventId = '';
  @Output() saved = new EventEmitter<void>();
  @Output() closed = new EventEmitter<void>();

  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly subscriptions = new Subscription();

  event?: AdminEventView;
  loading = false;
  saving = false;
  savingTicketIndex = -1;
  error = '';
  success = '';

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(180)]],
    shortDescription: ['', Validators.maxLength(500)],
    description: ['', Validators.maxLength(10000)],
    category: ['', Validators.maxLength(120)],
    startsAt: ['', Validators.required],
    endsAt: [''],
    bookingStartsAt: [''],
    bookingEndsAt: [''],
    timezone: ['Asia/Kolkata', Validators.required],
    capacity: [{ value: 0, disabled: true }],
    venueName: ['', Validators.maxLength(255)],
    venueAddress: ['', Validators.maxLength(1000)],
    city: ['', Validators.maxLength(120)],
    state: ['', Validators.maxLength(120)],
    mapUrl: ['', Validators.maxLength(500)],
    coverImageUrl: ['', Validators.maxLength(1000)],
    gallery: [''],
    highlights: [''],
    terms: ['', Validators.maxLength(10000)],
    refundPolicy: ['', Validators.maxLength(10000)],
    ageRestriction: ['', Validators.maxLength(80)],
    featured: [false],
    displayOrder: [0, [Validators.min(0), Validators.max(100000)]],
    paymentProvider: ['RAZORPAY'],
    brandingMode: ['BOTH'],
    organizerLogoUrl: [''],
    eventLogoUrl: [''],
    eventBannerUrl: [''],
    ticketTypes: this.fb.array([])
  });

  get ticketForms(): FormArray { return this.form.controls.ticketTypes as FormArray; }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['eventId'] && this.eventId) this.load();
  }

  ngOnDestroy(): void { this.subscriptions.unsubscribe(); }

  private load(): void {
    this.loading = true; this.error = ''; this.success = '';
    this.subscriptions.add(this.api.adminEvent(this.eventId).subscribe({
      next: e => { this.event = e; this.populate(e); this.loading = false; },
      error: err => { this.loading = false; this.error = err?.error?.message || 'Event details could not be loaded.'; }
    }));
  }

  private populate(e: AdminEventView): void {
    this.form.patchValue({
      name:e.name, shortDescription:e.shortDescription || '', description:e.description || '', category:e.category || '',
      startsAt:this.toLocalInput(e.startsAt, e.timezone), endsAt:this.toLocalInput(e.endsAt, e.timezone),
      bookingStartsAt:this.toLocalInput(e.bookingStartsAt, e.timezone), bookingEndsAt:this.toLocalInput(e.bookingEndsAt, e.timezone),
      timezone:e.timezone || 'Asia/Kolkata', capacity:e.capacity || 0, venueName:e.venueName || '', venueAddress:e.venueAddress || '',
      city:e.city || '', state:e.state || '', mapUrl:e.mapUrl || '', coverImageUrl:e.coverImageUrl || '',
      gallery:(e.gallery || []).join('\n'), highlights:(e.highlights || []).join('\n'), terms:e.terms || '', refundPolicy:e.refundPolicy || '',
      ageRestriction:e.ageRestriction || '', featured:e.featured, displayOrder:e.displayOrder || 0, paymentProvider:e.paymentProvider || 'RAZORPAY', brandingMode:e.brandingMode || 'BOTH', organizerLogoUrl:e.organizerLogoUrl || '', eventLogoUrl:e.eventLogoUrl || '', eventBannerUrl:e.eventBannerUrl || ''
    });
    this.ticketForms.clear();
    for (const t of e.ticketTypes) this.ticketForms.push(this.ticketGroup(t));
  }

  private ticketGroup(t: AdminEventView['ticketTypes'][number]) {
    return this.fb.group({
      id: [t.id], name: [t.name, [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
      description: [t.description || '', Validators.maxLength(2000)], priceRupees: [Math.round(t.priceMinorUnits / 100), [Validators.required, Validators.min(0), Validators.max(10000000)]],
      totalQuantity: [t.totalQuantity, [Validators.required, Validators.min(1), Validators.max(1000000)]], minPerOrder: [t.minPerOrder, [Validators.required, Validators.min(1), Validators.max(20)]],
      maxPerOrder: [t.maxPerOrder, [Validators.required, Validators.min(1), Validators.max(20)]],
      saleStartsAt: [this.toLocalInput(t.saleStartsAt, this.event?.timezone || 'Asia/Kolkata')], saleEndsAt: [this.toLocalInput(t.saleEndsAt, this.event?.timezone || 'Asia/Kolkata')],
      status: [t.status, Validators.required], soldQuantity: [t.soldQuantity], reservedQuantity: [t.reservedQuantity], availableQuantity: [t.availableQuantity]
    });
  }

  addTicket(): void {
    const fallback: AdminEventView['ticketTypes'][number] = { id:'', name:'New ticket', description:'', priceMinorUnits:99900, currency:this.event?.currency || 'INR', totalQuantity:100, soldQuantity:0, reservedQuantity:0, availableQuantity:100, minPerOrder:1, maxPerOrder:10, status:'ACTIVE' };
    this.ticketForms.push(this.ticketGroup(fallback));
  }

  private selectedPurpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER' = 'EVENT_COVER';

  chooseFile(purpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER'): void {
    this.selectedPurpose = purpose;
    const input = document.querySelector('#event-editor input[type="file"]') as HTMLInputElement | null;
    input?.click();
  }

  uploadSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.event) return;
    if (file.size > 5 * 1024 * 1024) { this.error = 'Image must be 5 MB or smaller.'; return; }
    this.error = ''; this.success = '';
    this.subscriptions.add(this.api.uploadAdminAsset(file, this.selectedPurpose, this.event.id, this.event.organizerSlug).subscribe({
      next: () => { this.success = 'Brand asset uploaded securely.'; this.load(); },
      error: err => { this.error = err?.error?.message || 'Image upload failed.'; }
    }));
  }

  saveEvent(): void {
    if (!this.event || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const timezone = String(v.timezone || this.event.timezone || 'Asia/Kolkata');
    const startsAt = this.toIsoInZone(v.startsAt || '', timezone);
    const endsAt = this.optionalIso(v.endsAt, timezone);
    const bookingStartsAt = this.optionalIso(v.bookingStartsAt, timezone);
    const bookingEndsAt = this.optionalIso(v.bookingEndsAt, timezone);
    if (!startsAt) { this.error = 'Enter a valid event start time.'; return; }
    if (endsAt && new Date(endsAt).getTime() <= new Date(startsAt).getTime()) { this.error = 'Event end must be after the start.'; return; }
    if (bookingStartsAt && bookingEndsAt && new Date(bookingEndsAt).getTime() <= new Date(bookingStartsAt).getTime()) { this.error = 'Booking end must be after booking start.'; return; }
    if (bookingStartsAt && new Date(bookingStartsAt).getTime() >= new Date(startsAt).getTime()) { this.error = 'Booking must start before the event begins.'; return; }
    if (bookingEndsAt && new Date(bookingEndsAt).getTime() > new Date(startsAt).getTime()) { this.error = 'Booking must end on or before the event start.'; return; }
    this.saving = true; this.error=''; this.success='';
    const body: Record<string, unknown> = {
      name: String(v.name || '').trim(), shortDescription: v.shortDescription || '', description: v.description || '', category: String(v.category || '').trim(),
      startsAt, endsAt, clearEndsAt: !v.endsAt, timezone,
      bookingStartsAt, bookingEndsAt, clearBookingStartsAt: !v.bookingStartsAt, clearBookingEndsAt: !v.bookingEndsAt,
      venueName: v.venueName || '', venueAddress: v.venueAddress || '', city: v.city || '', state: v.state || '', mapUrl: v.mapUrl || '',
      coverImageUrl: v.coverImageUrl || '', galleryUrls: this.lines(v.gallery), highlights: this.lines(v.highlights), terms: v.terms || '', refundPolicy: v.refundPolicy || '', ageRestriction: v.ageRestriction || '',
      featured: !!v.featured, displayOrder: Number(v.displayOrder || 0), paymentProvider: String(v.paymentProvider || 'RAZORPAY'), organizerLogoUrl: String(v.organizerLogoUrl || ''), eventLogoUrl: String(v.eventLogoUrl || ''), eventBannerUrl: String(v.eventBannerUrl || ''), brandingMode: String(v.brandingMode || 'BOTH')
    };
    // The backend update contract intentionally does not alter currency, slug, capacity, or organizer ownership.
    this.subscriptions.add(this.api.updateEvent(this.event.id, body).subscribe({
      next: () => { this.saving=false; this.success='Event changes saved.'; this.load(); this.saved.emit(); },
      error: err => { this.saving=false; this.error=err?.error?.message || 'Event could not be updated.'; }
    }));
  }

  saveTicket(index: number): void {
    const group = this.ticketForms.at(index) as any;
    if (!group || group.invalid || !this.event) { group?.markAllAsTouched(); return; }
    const v = group.getRawValue();
    if (Number(v.maxPerOrder) < Number(v.minPerOrder)) { this.error='Maximum tickets per order must be at least the minimum.'; return; }
    if (Number(v.priceRupees) < 0) { this.error='Ticket price cannot be negative.'; return; }
    const timezone = this.event.timezone || 'Asia/Kolkata';
    const saleStartsAt = this.optionalIso(v.saleStartsAt, timezone);
    const saleEndsAt = this.optionalIso(v.saleEndsAt, timezone);
    if (saleStartsAt && saleEndsAt && new Date(saleEndsAt).getTime() <= new Date(saleStartsAt).getTime()) { this.error='Ticket sale end must be after sale start.'; return; }
    const existingId = String(v.id || '');
    const body: Record<string, unknown> = {
      name: String(v.name || '').trim(), description: v.description || '', priceMinorUnits: Math.round(Number(v.priceRupees) * 100), totalQuantity: Number(v.totalQuantity),
      minPerOrder:Number(v.minPerOrder), maxPerOrder:Number(v.maxPerOrder), saleStartsAt, saleEndsAt, clearSaleStartsAt: !v.saleStartsAt, clearSaleEndsAt: !v.saleEndsAt, status:v.status
    };
    this.savingTicketIndex=index; this.error=''; this.success='';
    const request$ = existingId
      ? this.api.updateTicketType(existingId, body)
      : this.api.addTicketType(this.event.id, body).pipe(map(() => void 0));
    this.subscriptions.add(request$.subscribe({
      next: () => { this.savingTicketIndex=-1; this.success=existingId?'Ticket type updated.':'Ticket type added.'; this.load(); },
      error: err => { this.savingTicketIndex=-1; this.error=err?.error?.message || 'Ticket type could not be saved.'; }
    }));
  }

  transition(action: 'publish'|'unpublish'|'cancel'|'complete'|'archive'): void {
    if (!this.event) return;
    if ((action === 'cancel' || action === 'archive') && !globalThis.confirm(`Are you sure you want to ${action} this event?`)) return;
    this.error=''; this.success='';
    const label = action.charAt(0).toUpperCase()+action.slice(1);
    const request$ = action === 'publish'
      ? this.api.publishEvent(this.event.id)
      : this.api.adminTransition(this.event.id, action);
    this.subscriptions.add(request$.subscribe({
      next: () => { this.success=`Event ${action}ed successfully.`; this.load(); this.saved.emit(); },
      error: err => { this.error=err?.error?.message || `${label} failed.`; }
    }));
  }

  private lines(value: string | null | undefined): string[] { return String(value || '').split(/\r?\n/).map(x=>x.trim()).filter(Boolean); }
  private optionalIso(value: string | null | undefined, timezone: string): string | undefined { return value ? this.toIsoInZone(value, timezone) : undefined; }

  private toIsoInZone(local: string, timezone: string): string | undefined {
    if (!local) return undefined;
    const m = local.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/); if (!m) return undefined;
    const [year,month,day,hour,minute] = m.slice(1).map(Number);
    const naiveMs = Date.UTC(year, month-1, day, hour, minute);
    const offset1 = this.offsetMinutes(new Date(naiveMs), timezone);
    const candidate = new Date(naiveMs - offset1*60000);
    const offset2 = this.offsetMinutes(candidate, timezone);
    return new Date(naiveMs - offset2*60000).toISOString();
  }

  private offsetMinutes(date: Date, timezone: string): number {
    const parts = new Intl.DateTimeFormat('en-US', { timeZone: timezone, timeZoneName:'longOffset', hour:'2-digit', minute:'2-digit', year:'numeric', month:'2-digit', day:'2-digit' }).formatToParts(date);
    const raw = parts.find(p=>p.type==='timeZoneName')?.value || 'GMT';
    const match = raw.match(/GMT([+-])(\d{2}):(\d{2})/);
    if (!match) return 0;
    const mins = Number(match[2])*60 + Number(match[3]); return match[1]==='-' ? -mins : mins;
  }

  private toLocalInput(iso: string | undefined | null, timezone: string): string {
    if (!iso) return '';
    try {
      const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hourCycle:'h23' }).formatToParts(new Date(iso));
      const get=(type:string)=>parts.find(p=>p.type===type)?.value || '';
      return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
    } catch { return ''; }
  }
}
