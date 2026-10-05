import { CommonModule } from '@angular/common';
import { Component, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { map, Subscription } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { AdminEventView, PublishReadiness } from '../../core/api/api.models';
import { DEFAULT_TZ, TIMEZONES, toIsoInZone, toLocalInput } from '../../core/datetime';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';
import { EventTeamPanelComponent } from './event-team-panel.component';

@Component({
  selector: 'lk-event-editor',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, EventTeamPanelComponent],
  template: `
    <a class="back" routerLink="/admin/events">← All events</a>
    <section class="editor" id="event-editor" *ngIf="event">
      <div class="editor-head">
        <div>
          <div class="eyebrow">Event editor</div>
          <h2>{{ event.name }}</h2>
          <p class="editor-sub"><span class="state-pill" [class.published]="event.status==='PUBLISHED'">{{ event.status }}</span> /{{ event.slug }} · {{ event.organizerName }}</p>
        </div>
        <div class="editor-actions">
          <a class="a-btn" [href]="'/events/' + event.slug" target="_blank" rel="noopener">View public page ↗</a>
          <a class="a-btn" [routerLink]="['/admin/events', event.id, 'operations']">Issued tickets & orders</a>
          <a class="a-btn" routerLink="/admin/events">Close</a>
        </div>
      </div>

      <div class="editor-message success" *ngIf="created && !success" role="status">Draft created. Add branding and review the details below, then publish when you are ready.</div>
      <div class="editor-message error" *ngIf="error" role="alert">{{ error }}</div>
      <div class="editor-message success" *ngIf="success" role="status">{{ success }}</div>

      <section class="publish-readiness" *ngIf="event && (event.status==='DRAFT' || event.status==='UNPUBLISHED')" aria-live="polite">
        <div class="readiness-head"><div><span class="eyebrow">Release gate</span><h3>Publish readiness</h3><p>Server-validated checks prevent incomplete events from going live.</p></div><span class="readiness-state" [class.ready]="publishReadiness?.ready" [class.pending]="publishReadinessLoading">{{ publishReadinessLoading ? 'CHECKING' : (publishReadiness?.ready ? 'READY TO PUBLISH' : 'ACTION REQUIRED') }}</span></div>
        <div *ngIf="publishReadinessLoading" class="readiness-loading">Checking event, ticket inventory and payment configuration…</div>
        <div *ngIf="publishReadiness && !publishReadinessLoading">
          <div class="readiness-item blocker" *ngFor="let blocker of publishReadiness.blockers"><span>!</span><div>{{ blocker }}</div></div>
          <div class="readiness-item warning" *ngFor="let warning of publishReadiness.warnings"><span>i</span><div>{{ warning }}</div></div>
          <div class="readiness-ok" *ngIf="publishReadiness.ready">✓ All publication gates passed. Publishing is safe to proceed.</div>
        </div>
      </section>

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
              <select formControlName="timezone"><option *ngFor="let tz of timezones" [value]="tz">{{ tz }}</option></select>
            </label>
            <label>Event capacity <input formControlName="capacity" type="number" readonly aria-readonly="true" /><small>Capacity is fixed after creation for inventory safety.</small></label>
            <label>Starts<input formControlName="startsAt" type="datetime-local" /></label>
            <label>Ends<input formControlName="endsAt" type="datetime-local" /><small>Multi-day events can accept bookings through this end time.</small></label>
            <label>Booking starts<input formControlName="bookingStartsAt" type="datetime-local" /><small>Must be before the event begins.</small></label>
            <label>Booking ends<input formControlName="bookingEndsAt" type="datetime-local" /><small>Sales stay open through this time. Leave it tied to the event end to accept bookings until the event finishes.</small></label>
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
            <label>Payment provider<select formControlName="paymentProvider"><option value="RAZORPAY">Razorpay</option><option value="CASHFREE">Cashfree</option></select><small>Only changes when you intentionally choose a different provider.</small></label>
            <label>Brand display<select formControlName="brandingMode"><option value="BOTH">Logo + text</option><option value="LOGO_ONLY">Logo only</option><option value="TEXT_ONLY">Text only</option></select></label>
            <label class="toggle"><input type="checkbox" formControlName="featured" /><span>Featured event</span></label>
            <label>Display order<input formControlName="displayOrder" type="number" min="0" /></label>
            <div class="read-only-brand"><span>Organizer</span><strong>{{ event.organizerName }}</strong><small>Platform owner: Neelastack</small></div>
          </div>
          <div class="asset-grid">
            <div class="asset-card"><div><strong>Organizer logo</strong><small>Updates the organizer identity across its events.</small></div><button type="button" class="small-action" (click)="chooseFile('ORGANIZER_LOGO')" [disabled]="eventEditsLocked">Upload</button></div>
            <div class="asset-card"><div><strong>Event logo</strong><small>Displayed as the event-specific mark.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_LOGO')" [disabled]="eventEditsLocked">Upload</button></div>
            <div class="asset-card"><div><strong>Event banner</strong><small>Premium hero/banner artwork.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_BANNER')" [disabled]="eventEditsLocked">Upload</button></div>
            <div class="asset-card"><div><strong>Event cover</strong><small>Used by cards and catalogue surfaces.</small></div><button type="button" class="small-action" (click)="chooseFile('EVENT_COVER')" [disabled]="eventEditsLocked">Upload</button></div>
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
                <label>Price (₹)<input formControlName="priceRupees" type="number" min="1" step="1" inputmode="numeric" /></label>
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
                <button type="button" class="small-action" (click)="saveTicket(i)" [disabled]="eventEditsLocked || savingTicketIndex===i || group.invalid">{{savingTicketIndex===i?'Saving…':(group.get('id')?.value?'Save ticket':'Add ticket')}}</button>
              </div>
            </article>
          </div>
          <button type="button" class="add-ticket" (click)="addTicket()" [disabled]="eventEditsLocked">+ Add ticket type</button>
        </div>

        <div class="editor-footer">
          <div class="lifecycle">
            <span class="state">{{event.status}}</span>
            <button type="button" *ngIf="event.status==='DRAFT' || event.status==='UNPUBLISHED'" [disabled]="transitioningAction!==null || publishReadinessLoading" [title]="publishHint" (click)="transition('publish')">{{transitioningAction==='publish'?'Publishing…':'Publish'}}</button>
            <button type="button" *ngIf="event.status==='PUBLISHED'" [disabled]="transitioningAction!==null" (click)="transition('unpublish')">{{transitioningAction==='unpublish'?'Unpublishing…':'Unpublish'}}</button>
            <button type="button" class="danger" *ngIf="event.status==='DRAFT' || event.status==='PUBLISHED' || event.status==='UNPUBLISHED'" [disabled]="transitioningAction!==null" (click)="transition('cancel')">{{transitioningAction==='cancel'?'Cancelling…':'Cancel event'}}</button>
            <button type="button" *ngIf="canComplete" [disabled]="transitioningAction!==null" (click)="transition('complete')">{{transitioningAction==='complete'?'Completing…':'Complete event'}}</button>
            <button type="button" *ngIf="event.status!=='PUBLISHED' && event.status!=='ARCHIVED'" [disabled]="transitioningAction!==null" (click)="transition('archive')">{{transitioningAction==='archive'?'Archiving…':'Archive'}}</button>
          </div>
          <button class="save-event" type="submit" [disabled]="eventEditsLocked || saving || form.invalid || transitioningAction!==null">{{saving?'Saving…':'Save event changes'}} <span>→</span></button>
        </div>
      </form>
    </section>

    <lk-event-team-panel class="team-panel" *ngIf="event && event.organizerSlug" [eventId]="event.id" [organizerSlug]="event.organizerSlug" />

    <div class="editor-loading" *ngIf="loading && !event">Loading event editor…</div>
    <div class="editor-message error standalone" *ngIf="!event && !loading && error" role="alert">{{ error }} <a class="a-btn sm" routerLink="/admin/events">Back to events</a></div>
  `,
  styles: [ADMIN_UI_STYLES, `
    .team-panel{display:block;margin-top:16px}
    .back{display:inline-block;margin-bottom:18px;color:#6b6270;text-decoration:none;font-size:13px}.back:hover{color:var(--ink)}
    .editor{background:#fff;border:1px solid var(--line);border-radius:22px;overflow:hidden;box-shadow:0 18px 55px rgba(33,24,31,.06)}
    .editor-head{display:flex;justify-content:space-between;gap:20px;flex-wrap:wrap;padding:26px 28px;border-bottom:1px solid #eee8e0;background:linear-gradient(145deg,#fffdf9,#f8f3eb)}
    .editor-head h2{margin:7px 0 6px;font-family:var(--display);font-size:clamp(26px,3.4vw,36px);letter-spacing:-.04em;font-weight:600}
    .editor-sub{margin:0;color:#8a818b;font-size:13px;display:flex;gap:8px;align-items:center;flex-wrap:wrap}
    .state-pill{font-size:10px;font-weight:800;letter-spacing:.08em;padding:4px 9px;border-radius:999px;background:#fff3d6;color:#7a5a12}.state-pill.published{background:#e4f2e7;color:#2f6a3d}
    .editor-actions{display:flex;gap:8px;align-items:flex-start;flex-wrap:wrap}
    .editor-message{margin:16px 24px 0;padding:12px 14px;border-radius:12px;font-size:13px;line-height:1.5}.editor-message.standalone{margin:0}
    .editor-message.error{background:#fdeeee;border:1px solid #efb9b9;color:#8c2f2f}.editor-message.success{background:#eaf6ee;border:1px solid #b8dcc3;color:#23623a}
    .editor-section{padding:26px 28px;border-bottom:1px solid #eee8e0}
    .section-title{display:grid;grid-template-columns:34px 1fr;gap:12px;align-items:start;margin-bottom:18px}
    .section-title>span{display:grid;place-items:center;width:28px;height:28px;border-radius:9px;background:#17121a;color:#fff;font-size:10px;font-weight:800}
    .section-title h3{margin:0 0 3px;font-family:var(--display);font-size:20px;letter-spacing:-.02em;font-weight:600}.section-title p{margin:0;color:#8a8190;font-size:13px;line-height:1.5}
    .grid{display:grid;gap:14px}.grid.two{grid-template-columns:1fr 1fr}.grid.three{grid-template-columns:1fr .65fr 1fr}.grid .wide{grid-column:1/-1}
    .grid label{display:grid;gap:6px;color:#4a414d;font-size:13px;font-weight:700;min-width:0}
    .grid label small{font-size:12px;color:#8a8190;font-weight:400;line-height:1.4}
    .grid input,.grid select,.grid textarea{width:100%;min-width:0;min-height:46px;border:1px solid #d9d2c8;border-radius:12px;padding:10px 13px;background:#fff;color:var(--ink);font:inherit;font-size:16px;line-height:1.35;outline:0;color-scheme:light}
    .grid textarea{resize:vertical;min-height:90px}
    .grid input:focus,.grid select:focus,.grid textarea:focus,.ticket-top select:focus{border-color:#9e8150;box-shadow:0 0 0 3px rgba(158,129,80,.14)}
    .grid input[readonly]{background:#f7f4ef;color:#847b84}
    .grid input.ng-invalid.ng-touched,.grid select.ng-invalid.ng-touched{border-color:#d49a9a;background:#fffafa}
    .toggle{display:flex!important;align-items:center;gap:10px!important;min-height:46px}.toggle input{width:20px;min-height:20px;height:20px;accent-color:#17121a}
    .read-only-brand{border:1px dashed #d6cec4;background:var(--soft);border-radius:12px;padding:12px;display:grid;gap:4px;align-content:center}.read-only-brand span,.read-only-brand small{font-size:12px;color:#8a8190}.read-only-brand strong{font-size:14px}
    .asset-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:16px}
    .asset-card{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px;border:1px solid var(--line);border-radius:14px;background:var(--soft)}
    .asset-card strong{display:block;font-size:14px}.asset-card small{display:block;color:#8a8190;font-size:12px;margin-top:4px;line-height:1.4}
    .ticket-editor-list{display:grid;gap:14px}.ticket-editor{border:1px solid #e2dbd3;border-radius:16px;padding:18px;background:var(--soft)}
    .ticket-top{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:14px}.ticket-top strong{display:block;font-size:16px}.ticket-top small{display:block;color:#9a9198;font-size:11px;margin-top:3px}
    .ticket-top select{min-height:40px;border:1px solid #d9d2c8;border-radius:10px;background:#fff;color:var(--ink);padding:0 10px;font-size:16px;color-scheme:light}
    .ticket-grid{grid-template-columns:1.35fr .75fr .8fr .65fr .65fr}
    .ticket-stats{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-top:14px;padding-top:12px;border-top:1px solid #e7e0d8}.ticket-stats span{font-size:13px;color:#7a717b}.ticket-stats b{color:var(--ink)}
    .small-action{margin-left:auto;min-height:38px;border:1px solid #d8d0c7;background:#fff;color:#342d35;border-radius:10px;padding:0 14px;font-size:13px;font-weight:700;cursor:pointer}.small-action:disabled{opacity:.45;cursor:not-allowed}
    .add-ticket{margin-top:12px;min-height:42px;border:1px dashed #c8c0b7;background:transparent;color:#342d35;border-radius:10px;padding:0 14px;font-size:13px;font-weight:700;cursor:pointer}
    .editor-footer{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap;padding:20px 28px;background:var(--soft)}
    .lifecycle{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
    .lifecycle .state{padding:8px 12px;border-radius:999px;background:#ece6dc;color:#5b5045;font-size:11px;font-weight:800;letter-spacing:.06em}
    .lifecycle button{min-height:38px;border:1px solid #d8d0c7;background:#fff;color:var(--ink);border-radius:10px;padding:0 14px;font-size:13px;font-weight:700;cursor:pointer}.lifecycle button.danger{color:#8f3e42;border-color:#ebcdcf}
    .save-event{min-width:210px;min-height:48px;border:0;background:#17121a;color:#fff;border-radius:12px;padding:0 18px;font-weight:800;font-size:14px;cursor:pointer;display:inline-flex;justify-content:space-between;align-items:center;gap:12px}.save-event:disabled{opacity:.45;cursor:not-allowed}
    .editor-loading{text-align:center;padding:40px;color:#8b828c;font-size:14px}
    .publish-readiness{margin:0 0 20px;padding:20px;border:1px solid #e3ddd5;border-radius:18px;background:linear-gradient(180deg,#fffdf9,#faf7f1)}
    .readiness-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:16px}.readiness-head h3{margin:4px 0;font-family:var(--display);font-size:22px}.readiness-head p{margin:0;color:#776d79;font-size:12px}.readiness-state{display:inline-flex;align-items:center;min-height:30px;padding:0 10px;border-radius:999px;background:#f1ece4;color:#766b76;font-size:10px;font-weight:800;letter-spacing:.1em}.readiness-state.ready{background:#e4f2e7;color:#2e6b3c}.readiness-state.pending{background:#fff4d9;color:#79601c}.readiness-loading{padding:12px 14px;border-radius:12px;background:#f4f0ea;color:#756b76;font-size:12px}.readiness-item{display:flex;gap:10px;align-items:flex-start;padding:10px 12px;border-radius:11px;font-size:12px;line-height:1.45;margin-top:8px}.readiness-item.blocker{background:#fff0f0;color:#8b3030;border:1px solid #f1caca}.readiness-item.warning{background:#fff8e6;color:#775c17;border:1px solid #eddca8}.readiness-item span{display:grid;place-items:center;flex:0 0 18px;width:18px;height:18px;border-radius:50%;background:currentColor;color:#fff;font-weight:900;font-size:11px}.readiness-ok{margin-top:8px;padding:11px 12px;border-radius:11px;background:#eaf6ee;color:#23623a;border:1px solid #c1dfc9;font-size:12px;font-weight:700}
    @media(max-width:900px){.grid.two,.grid.three{grid-template-columns:1fr}.ticket-grid{grid-template-columns:1fr 1fr}.asset-grid{grid-template-columns:1fr}.small-action{margin-left:0}.editor-footer{align-items:stretch;flex-direction:column}.save-event{width:100%}}
    @media(max-width:600px){.editor-head{padding:20px 16px}.editor-section{padding:22px 16px}.section-title{grid-template-columns:30px 1fr}.ticket-grid{grid-template-columns:1fr}.editor-footer{padding:18px 16px}.lifecycle button{flex:1 1 40%}.editor-actions{width:100%}.editor-actions .a-btn{flex:1}}
  `]
})
export class EventEditorComponent implements OnChanges, OnDestroy {
  /** Bound from the route parameter `/admin/events/:eventId`. */
  @Input({ required: true }) eventId = '';
  /** Bound from `?created=1` right after the create page redirects here. */
  @Input() created: string | number | null = null;
  @ViewChild('assetInput') private assetInput?: ElementRef<HTMLInputElement>;

  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly store = inject(AdminStore);
  private readonly subscriptions = new Subscription();
  readonly timezones = TIMEZONES;

  event?: AdminEventView;
  loading = false;
  saving = false;
  savingTicketIndex = -1;
  transitioningAction: 'publish' | 'unpublish' | 'cancel' | 'complete' | 'archive' | null = null;
  publishReadiness?: PublishReadiness;
  publishReadinessLoading = false;
  originalPaymentProvider = 'RAZORPAY';
  private _error = '';
  private _success = '';
  get error(): string { return this._error; }
  set error(v: string) { this._error = v; if (v) this.reveal(); }
  get success(): string { return this._success; }
  set success(v: string) { this._success = v; if (v) this.reveal(); }

  /** Messages render at the top of the editor; bring them into view so a failed save is never silent. */
  private reveal(): void {
    if (typeof document === 'undefined') return;
    setTimeout(() => document.querySelector('#event-editor .editor-message:not(.standalone)')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50);
  }

  form = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(180)]],
    shortDescription: ['', Validators.maxLength(500)],
    description: ['', Validators.maxLength(10000)],
    category: ['', Validators.maxLength(120)],
    startsAt: ['', Validators.required],
    endsAt: [''],
    bookingStartsAt: [''],
    bookingEndsAt: [''],
    timezone: [DEFAULT_TZ, Validators.required],
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
    paymentProvider: ['CASHFREE'],
    brandingMode: ['BOTH'],
    organizerLogoUrl: [''],
    eventLogoUrl: [''],
    eventBannerUrl: [''],
    ticketTypes: this.fb.array([])
  });

  get ticketForms(): FormArray { return this.form.controls.ticketTypes as FormArray; }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['eventId'] && this.eventId) { this.event = undefined; this.load(); }
  }

  ngOnDestroy(): void { this.subscriptions.unsubscribe(); }

  get eventEditsLocked(): boolean {
    return !!this.event && ['COMPLETED', 'ARCHIVED', 'CANCELLED'].includes(this.event.status);
  }

  get canComplete(): boolean {
    if (!this.event || this.event.status !== 'PUBLISHED') return false;
    const end = this.event.endsAt || this.event.startsAt;
    return !!end && new Date(end).getTime() <= Date.now();
  }

  get canPublish(): boolean {
    if (!this.event || !['DRAFT', 'UNPUBLISHED'].includes(this.event.status)) return false;
    if (this.publishReadiness) return this.publishReadiness.ready;
    const hasTickets = !!this.event.ticketTypes?.length;
    const startsValid = !!this.event.startsAt && new Date(this.event.startsAt).getTime() > Date.now();
    return hasTickets && startsValid;
  }

  get publishHint(): string {
    if (!this.event) return '';
    if (this.publishReadiness?.blockers?.length) return this.publishReadiness.blockers[0];
    if (this.publishReadiness?.ready) return 'Publish this event';
    if (!this.event.ticketTypes?.length) return 'Add at least one ticket type before publishing.';
    if (new Date(this.event.startsAt).getTime() <= Date.now()) return 'An event in the past cannot be published.';
    return 'Publication readiness is being checked.';
  }

  private load(keepMessages = false): void {
    this.loading = true;
    if (!keepMessages) { this.error = ''; this.success = ''; }
    this.subscriptions.add(this.api.adminEvent(this.eventId).subscribe({
      next: e => { this.event = e; this.populate(e); this.loading = false; this.refreshPublishReadiness(); },
      error: err => { this.loading = false; this.error = err?.error?.message || 'Event details could not be loaded.'; }
    }));
  }

  private refreshPublishReadiness(): void {
    if (!this.event || !['DRAFT', 'UNPUBLISHED'].includes(this.event.status)) {
      this.publishReadiness = undefined;
      this.publishReadinessLoading = false;
      return;
    }
    this.publishReadinessLoading = true;
    this.subscriptions.add(this.api.publishReadiness(this.event.id).subscribe({
      next: r => { this.publishReadiness = r; this.publishReadinessLoading = false; },
      error: err => {
        this.publishReadinessLoading = false;
        this.publishReadiness = { ready: false, blockers: [err?.error?.message || 'Publication readiness could not be verified. Retry before publishing.'], warnings: [] };
      }
    }));
  }

  private populate(e: AdminEventView): void {
    this.form.patchValue({
      name:e.name, shortDescription:e.shortDescription || '', description:e.description || '', category:e.category || '',
      startsAt:this.toLocalInput(e.startsAt, e.timezone), endsAt:this.toLocalInput(e.endsAt, e.timezone),
      bookingStartsAt:this.toLocalInput(e.bookingStartsAt, e.timezone), bookingEndsAt:this.toLocalInput(e.bookingEndsAt || e.endsAt, e.timezone),
      timezone:e.timezone || DEFAULT_TZ, capacity:e.capacity || 0, venueName:e.venueName || '', venueAddress:e.venueAddress || '',
      city:e.city || '', state:e.state || '', mapUrl:e.mapUrl || '', coverImageUrl:e.coverImageUrl || '',
      gallery:(e.gallery || []).join('\n'), highlights:(e.highlights || []).join('\n'), terms:e.terms || '', refundPolicy:e.refundPolicy || '',
      ageRestriction:e.ageRestriction || '', featured:e.featured, displayOrder:e.displayOrder || 0, paymentProvider:e.paymentProvider || 'CASHFREE', brandingMode:e.brandingMode || 'BOTH', organizerLogoUrl:e.organizerLogoUrl || '', eventLogoUrl:e.eventLogoUrl || '', eventBannerUrl:e.eventBannerUrl || ''
    });
    this.originalPaymentProvider = e.paymentProvider || 'RAZORPAY';
    this.ticketForms.clear();
    for (const t of e.ticketTypes) this.ticketForms.push(this.ticketGroup(t));
  }

  private ticketGroup(t: AdminEventView['ticketTypes'][number]) {
    return this.fb.group({
      id: [t.id], name: [t.name, [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
      description: [t.description || '', Validators.maxLength(2000)], priceRupees: [Math.round(t.priceMinorUnits / 100), [Validators.required, Validators.min(1), Validators.max(10000000)]],
      totalQuantity: [t.totalQuantity, [Validators.required, Validators.min(1), Validators.max(1000000)]], minPerOrder: [t.minPerOrder, [Validators.required, Validators.min(1), Validators.max(20)]],
      maxPerOrder: [t.maxPerOrder, [Validators.required, Validators.min(1), Validators.max(20)]],
      saleStartsAt: [this.toLocalInput(t.saleStartsAt, this.event?.timezone || DEFAULT_TZ)], saleEndsAt: [this.toLocalInput(t.saleEndsAt, this.event?.timezone || DEFAULT_TZ)],
      status: [t.status, Validators.required], soldQuantity: [t.soldQuantity], reservedQuantity: [t.reservedQuantity], availableQuantity: [t.availableQuantity]
    });
  }

  addTicket(): void {
    if (this.eventEditsLocked) return;
    const fallback: AdminEventView['ticketTypes'][number] = { id:'', name:'New ticket', description:'', priceMinorUnits:99900, currency:this.event?.currency || 'INR', totalQuantity:100, soldQuantity:0, reservedQuantity:0, availableQuantity:100, minPerOrder:1, maxPerOrder:10, status:'ACTIVE' };
    this.ticketForms.push(this.ticketGroup(fallback));
  }

  private selectedPurpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER' = 'EVENT_COVER';

  chooseFile(purpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER'): void {
    if (this.eventEditsLocked) return;
    this.selectedPurpose = purpose;
    this.assetInput?.nativeElement.click();
  }

  uploadSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || !this.event || this.eventEditsLocked) return;
    if (file.size > 5 * 1024 * 1024) { this.error = 'Image must be 5 MB or smaller.'; return; }
    this.error = ''; this.success = '';
    this.subscriptions.add(this.api.uploadAdminAsset(file, this.selectedPurpose, this.event.id, this.event.organizerSlug).subscribe({
      next: () => { this.success = 'Brand asset uploaded securely.'; this.load(true); },
      error: err => { this.error = err?.error?.message || 'Image upload failed.'; }
    }));
  }

  saveEvent(): void {
    if (this.eventEditsLocked) { this.error = 'This event is finalized and is now read-only.'; return; }
    if (!this.event || this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    const timezone = String(v.timezone || this.event.timezone || DEFAULT_TZ);
    const startsAt = this.toIsoInZone(v.startsAt || '', timezone);
    const endsAt = this.optionalIso(v.endsAt, timezone);
    const existingStartsAt = this.event.startsAt;
    const bookingStartsAt = this.optionalIso(v.bookingStartsAt, timezone);
    const bookingEndsAtInput = this.optionalIso(v.bookingEndsAt, timezone);
    const previousEventEnd = this.event.endsAt || this.event.startsAt;
    // When booking-end was implicitly tied to the previous event end, keep it tied
    // to the new event end. Explicit custom booking windows remain untouched.
    let bookingEndsAt: string | undefined;
    if (bookingEndsAtInput && previousEventEnd &&
        new Date(bookingEndsAtInput).getTime() === new Date(previousEventEnd).getTime()) {
      bookingEndsAt = endsAt || undefined;
    } else {
      bookingEndsAt = bookingEndsAtInput || (endsAt ? endsAt : undefined);
    }
    if (!startsAt) { this.error = 'Enter a valid event start time.'; return; }
    const effectiveEventEnd = endsAt ?? startsAt;
    const startChanged = !existingStartsAt || new Date(startsAt).getTime() !== new Date(existingStartsAt).getTime();
    if (startChanged && new Date(startsAt).getTime() < Date.now() - 5 * 60_000) { this.error = 'A changed event start time cannot be in the past.'; return; }
    if (endsAt && new Date(endsAt).getTime() <= new Date(startsAt).getTime()) { this.error = 'Event end must be after the start.'; return; }
    if (bookingStartsAt && new Date(bookingStartsAt).getTime() >= new Date(startsAt).getTime()) { this.error = 'Booking must start before the event begins.'; return; }
    if (bookingStartsAt && bookingEndsAt && new Date(bookingEndsAt).getTime() <= new Date(bookingStartsAt).getTime()) { this.error = 'Booking end must be after booking start.'; return; }
    if (bookingEndsAt && effectiveEventEnd && new Date(bookingEndsAt).getTime() > new Date(effectiveEventEnd).getTime()) { this.error = 'Booking can remain open only until the event ends.'; return; }
    this.saving = true; this.error=''; this.success='';
    const body: Record<string, unknown> = {
      name: String(v.name || '').trim(), shortDescription: v.shortDescription || '', description: v.description || '', category: String(v.category || '').trim(),
      startsAt, endsAt, clearEndsAt: !v.endsAt, timezone,
      bookingStartsAt, bookingEndsAt, clearBookingStartsAt: !bookingStartsAt, clearBookingEndsAt: !bookingEndsAt,
      venueName: v.venueName || '', venueAddress: v.venueAddress || '', city: v.city || '', state: v.state || '', mapUrl: v.mapUrl || '',
      coverImageUrl: v.coverImageUrl || '', galleryUrls: this.lines(v.gallery), highlights: this.lines(v.highlights), terms: v.terms || '', refundPolicy: v.refundPolicy || '', ageRestriction: v.ageRestriction || '',
      featured: !!v.featured, displayOrder: Number(v.displayOrder || 0), organizerLogoUrl: String(v.organizerLogoUrl || ''), eventLogoUrl: String(v.eventLogoUrl || ''), eventBannerUrl: String(v.eventBannerUrl || ''), brandingMode: String(v.brandingMode || 'BOTH')
    };
    // Do not accidentally submit the form's default provider value as a provider change.
    if (String(v.paymentProvider || 'CASHFREE') !== this.originalPaymentProvider) body.paymentProvider = String(v.paymentProvider || 'CASHFREE');
    // The backend update contract intentionally does not alter currency, slug, capacity, or organizer ownership.
    this.subscriptions.add(this.api.updateEvent(this.event.id, body).subscribe({
      next: () => { this.saving=false; this.success='Event changes saved.'; this.load(true); this.store.load(true); this.refreshPublishReadiness(); },
      error: err => { this.saving=false; this.error=err?.error?.message || 'Event could not be updated.'; }
    }));
  }

  saveTicket(index: number): void {
    if (this.eventEditsLocked) { this.error = 'This event is finalized and ticket inventory is now read-only.'; return; }
    const group = this.ticketForms.at(index) as any;
    if (!group || group.invalid || !this.event) { group?.markAllAsTouched(); return; }
    const v = group.getRawValue();
    if (Number(v.maxPerOrder) < Number(v.minPerOrder)) { this.error='Maximum tickets per order must be at least the minimum.'; return; }
    if (Number(v.priceRupees) < 1) { this.error='Ticket price must be at least ₹1. Use the complimentary tickets page for free passes.'; return; }
    const timezone = this.event.timezone || DEFAULT_TZ;
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
      next: () => { this.savingTicketIndex=-1; this.success=existingId?'Ticket type updated.':'Ticket type added.'; this.load(true); this.refreshPublishReadiness(); },
      error: err => { this.savingTicketIndex=-1; this.error=err?.error?.message || 'Ticket type could not be saved.'; }
    }));
  }

  transition(action: 'publish'|'unpublish'|'cancel'|'complete'|'archive'): void {
    if (!this.event || this.transitioningAction) return;
    const status = this.event.status;

    // Publishing always re-validates against the server immediately before the state change.
    // This avoids stale readiness data making the Publish button appear to do nothing.
    if (action === 'publish') {
      this.error = ''; this.success = '';
      this.transitioningAction = 'publish';
      this.publishReadinessLoading = true;
      const eventId = this.event.id;
      this.subscriptions.add(this.api.publishReadiness(eventId).subscribe({
        next: readiness => {
          this.publishReadiness = readiness;
          this.publishReadinessLoading = false;
          if (!readiness.ready) {
            this.transitioningAction = null;
            this.error = readiness.blockers[0] || 'This event is not ready to publish.';
            return;
          }
          this.subscriptions.add(this.api.publishEvent(eventId).subscribe({
            next: () => {
              this.transitioningAction = null;
              this.success = 'Event published successfully.';
              this.load(true); this.store.load(true);
            },
            error: err => {
              this.transitioningAction = null;
              this.error = err?.error?.message || 'Publishing failed. Publication readiness should be reviewed and retried.';
              this.refreshPublishReadiness();
            }
          }));
        },
        error: err => {
          this.publishReadinessLoading = false;
          this.transitioningAction = null;
          this.error = err?.error?.message || 'Publication readiness could not be verified. Please retry.';
        }
      }));
      return;
    }
    if (action === 'unpublish' && status !== 'PUBLISHED') {
      this.error = 'Only a published event can be unpublished.';
      return;
    }
    if (action === 'cancel' && !['DRAFT', 'PUBLISHED', 'UNPUBLISHED'].includes(status)) {
      this.error = 'This event can no longer be cancelled.';
      return;
    }
    if (action === 'complete' && !this.canComplete) {
      this.error = 'An event can be completed only after its end date and time.';
      return;
    }
    if (action === 'archive' && (status === 'PUBLISHED' || status === 'ARCHIVED')) {
      this.error = 'Unpublish or cancel the event before archiving.';
      return;
    }
    if (this.eventEditsLocked && action !== 'archive') return;
    if ((action === 'cancel' || action === 'archive') && !globalThis.confirm(`Are you sure you want to ${action} this event?`)) return;
    this.error=''; this.success='';
    const label = action.charAt(0).toUpperCase()+action.slice(1);
    const DONE: Record<string, string> = { publish: 'published', unpublish: 'unpublished', cancel: 'cancelled', complete: 'marked as completed', archive: 'archived' };
    this.transitioningAction = action;
    const request$ = this.api.adminTransition(this.event.id, action);
    this.subscriptions.add(request$.subscribe({
      next: () => { this.transitioningAction=null; this.success=`Event ${DONE[action]} successfully.`; this.load(true); this.store.load(true); },
      error: err => { this.transitioningAction=null; this.error=err?.error?.message || `${label} failed.`; }
    }));
  }

  private lines(value: string | null | undefined): string[] { return String(value || '').split(/\r?\n/).map(x=>x.trim()).filter(Boolean); }
  private optionalIso(value: string | null | undefined, timezone: string): string | undefined { return value ? toIsoInZone(value, timezone) : undefined; }
  private toIsoInZone(local: string, timezone: string): string | undefined { return toIsoInZone(local, timezone); }
  private toLocalInput(iso: string | undefined | null, timezone: string): string { return toLocalInput(iso, timezone); }
}
