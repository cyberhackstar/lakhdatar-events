import { DOCUMENT } from '@angular/common';
import { Component, OnInit, RESPONSE_INIT, afterNextRender, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService } from '../../core/api/api.service';
import { EventView, TicketTypeView } from '../../core/api/api.models';
import { SeoService } from '../../core/seo/seo.service';
import { BookingStoreService } from '../../shared/booking-store.service';
import { SiteHeaderComponent } from '../../shared/site-header.component';
import { SiteFooterComponent } from '../../shared/site-footer.component';
import { StatePanelComponent } from '../../shared/state-panel.component';
import { SALES_LABEL, canBook, eventDate, eventDateTime, eventLongDate, eventTime, rupees, safeImage, toAbsoluteUrl } from '../../core/format';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'lk-event-page', standalone: true,
  imports: [RouterLink, SiteHeaderComponent, SiteFooterComponent, StatePanelComponent],
  template: `
    <lk-site-header />
    @if (event(); as e) {
      <article class="page" [style.--event-primary]="e.brand.primaryBrandColor || '#D4A747'" [style.--event-secondary]="e.brand.secondaryBrandColor || '#6E193F'">
        <header class="hero" [class.has-cover]="!!cover(e)">
          @if (cover(e); as c) { <img class="cover" [src]="c" alt="" fetchpriority="high" decoding="async" /> }
          <div class="shade" aria-hidden="true"></div>
          <div class="container hero-in">
            <nav class="crumbs" aria-label="Breadcrumb"><a routerLink="/">Home</a><span>/</span><a routerLink="/events">Events</a></nav>
            <div class="tags">
              @if (e.category) { <span class="tag">{{ e.category }}</span> }
              <span [class]="'badge ' + e.salesState">{{ label(e) }}</span>
              @if (e.ageRestriction) { <span class="tag">{{ e.ageRestriction }}</span> }
            </div>
            <h1 class="display">{{ e.name }}</h1>
            @if (e.shortDescription) { <p class="short">{{ e.shortDescription }}</p> }
            <dl class="facts">
              <div><dt>Date</dt><dd>{{ longDate(e) }}</dd></div>
              <div><dt>Time</dt><dd>{{ time(e) }}@if (e.endsAt) { – {{ endTime(e) }} }</dd></div>
              @if (e.venueName) { <div><dt>Venue</dt><dd>{{ e.venueName }}@if (e.city) {, {{ e.city }} }</dd></div> }
              @if (e.organizer) { <div><dt>Presented by</dt><dd>{{ e.organizer.name }}</dd></div> }
            </dl>
          </div>
        </header>

        @if (e.salesState === 'CANCELLED') {
          <div class="notice bad container" role="alert"><strong>This event has been cancelled.</strong> If you hold a ticket, refunds are handled by the organizer — use "Find my ticket" to check your order.</div>
        } @else if (e.salesState === 'COMPLETED') {
          <div class="notice container" role="status"><strong>This event has taken place.</strong> Thank you to everyone who joined us.</div>
        }

        <div class="container layout">
          <div class="main">
            <section aria-labelledby="about">
              <div class="eyebrow">About</div><h2 id="about" class="display">About this event</h2>
              @for (para of paragraphs(e.description || e.shortDescription); track $index) { <p class="prose">{{ para }}</p> }
            </section>

            @if (e.highlights?.length) {
              <section aria-labelledby="hl">
                <div class="eyebrow">Highlights</div><h2 id="hl" class="display">What to expect</h2>
                <ul class="highlights">@for (h of e.highlights; track $index) { <li>{{ h }}</li> }</ul>
              </section>
            }

            @if (gallery(e).length) {
              <section aria-labelledby="gal">
                <div class="eyebrow">Gallery</div><h2 id="gal" class="display">Moments</h2>
                <div class="gallery">@for (g of gallery(e); track g) { <img [src]="g" alt="" loading="lazy" decoding="async" width="480" height="320" /> }</div>
              </section>
            }

            <section aria-labelledby="venue">
              <div class="eyebrow">Venue</div><h2 id="venue" class="display">Getting there</h2>
              <div class="card">
                <strong>{{ e.venueName || 'Venue to be announced' }}</strong>
                @if (e.venueAddress) { <span>{{ e.venueAddress }}@if (e.state) {, {{ e.state }} }</span> }
                @if (mapLink(e); as m) { <a class="btn btn-ghost sm" [href]="m" target="_blank" rel="noopener noreferrer">Open in maps ↗</a> }
              </div>
            </section>

            <section aria-labelledby="info">
              <div class="eyebrow">Good to know</div><h2 id="info" class="display">Important information</h2>
              <ul class="info">
                <li>Your QR ticket is issued instantly after payment and can be shown on your phone.</li>
                <li>Each QR ticket admits one guest and can be scanned once at entry.</li>
                @if (e.ageRestriction) { <li>Age policy: {{ e.ageRestriction }}.</li> }
                <li>Keep a photo ID handy. Lost your ticket? Recover it with your email and order number.</li>
              </ul>
              @if (e.terms) { <details><summary>Terms &amp; conditions</summary>@for (para of paragraphs(e.terms); track $index) { <p>{{ para }}</p> }</details> }
              <details open><summary>Cancellation &amp; refund policy</summary>
                @if (e.refundPolicy) { @for (para of paragraphs(e.refundPolicy); track $index) { <p>{{ para }}</p> } }
                @else { <p>Tickets are non-transferable and non-refundable unless the event is cancelled or rescheduled by the organizer.</p> }
              </details>
            </section>

            @if (e.organizer; as o) {
              <section aria-labelledby="org">
                <div class="eyebrow">Organizer</div><h2 id="org" class="display">{{ o.name }}</h2>
                <div class="card org">
                  @if (orgLogo(e); as l) { <img class="logo" [src]="l" [alt]="o.name" width="72" height="72" loading="lazy" /> }
                  <div>
                    @if (o.description) { <p>{{ o.description }}</p> }
                    <ul class="contact">
                      @if (o.contactEmail) { <li><span>Email</span><a [href]="'mailto:' + o.contactEmail">{{ o.contactEmail }}</a></li> }
                      @if (o.contactPhone) { <li><span>Phone</span><a [href]="'tel:' + o.contactPhone">{{ o.contactPhone }}</a></li> }
                      @if (o.supportHours) { <li><span>Support hours</span>{{ o.supportHours }}</li> }
                      @if (o.website) { <li><span>Website</span><a [href]="o.website" target="_blank" rel="noopener noreferrer">{{ o.website }}</a></li> }
                    </ul>
                  </div>
                </div>
              </section>
            }
          </div>

          <aside class="book" id="tickets" aria-labelledby="tk">
            <div class="panel">
              <div class="eyebrow">Tickets</div>
              <h2 id="tk" class="display">Choose your pass</h2>
              @if (!bookable(e)) {
                <div class="closed">
                  <strong>{{ closedTitle(e) }}</strong>
                  <span>{{ closedMessage(e) }}</span>
                  <a class="btn btn-ghost" routerLink="/events">Browse other events</a>
                </div>
              }
              <div class="types">
                @for (t of e.ticketTypes; track t.id) {
                  <div class="type" [class.off]="!canSelect(t) || !bookable(e)">
                    <div class="type-top">
                      <div><h3>{{ t.name }}</h3>@if (t.description) { <p>{{ t.description }}</p> }</div>
                      <div class="tp">{{ money(t.priceMinorUnits, t.currency) }}</div>
                    </div>
                    <div class="type-foot">
                      <span class="stock" [class.sold]="!canSelect(t)">{{ ticketStateLabel(t) }}</span>
                      @if (canSelect(t) && bookable(e)) {
                        <div class="qty" role="group" [attr.aria-label]="'Quantity for ' + t.name">
                          <button type="button" (click)="decrement(t)" [disabled]="quantity(t.id) === 0" aria-label="Decrease">−</button>
                          <output>{{ quantity(t.id) }}</output>
                          <button type="button" (click)="increment(t)" [disabled]="quantity(t.id) >= max(t)" aria-label="Increase">+</button>
                        </div>
                      }
                    </div>
                    @if (canSelect(t) && t.minPerOrder > 1) { <small>Minimum {{ t.minPerOrder }} per order</small> }
                    @if (ticketWindowLabel(t); as w) { <small>{{ w }}</small> }
                  </div>
                }
              </div>
              <div class="summary" aria-live="polite">
                <div><span>{{ cartCount() }} {{ cartCount() === 1 ? 'ticket' : 'tickets' }}</span><strong>{{ money(cartTotal(), e.currency) }}</strong></div>
                <button class="btn btn-primary" type="button" [disabled]="cartCount() === 0 || !bookable(e)" (click)="continueToCheckout()">Continue to checkout →</button>
                <p class="fine">Final price is confirmed securely at checkout. Payments by Razorpay.</p>
              </div>
            </div>
          </aside>
        </div>

        @if (bookable(e)) {
          <div class="sticky" role="region" aria-label="Book tickets">
            <div><small>{{ cartCount() > 0 ? cartCount() + ' selected' : 'From' }}</small><strong>{{ cartCount() > 0 ? money(cartTotal(), e.currency) : money(e.startingPriceMinor, e.currency) }}</strong></div>
            @if (cartCount() > 0) { <button class="btn btn-primary" type="button" (click)="continueToCheckout()">Checkout →</button> }
            @else { <button class="btn btn-primary" type="button" (click)="scrollToTickets()">Book tickets</button> }
          </div>
        }
      </article>
      <lk-site-footer [organizerName]="e.organizer?.name" />
    } @else if (failure() === 'notfound') {
      <lk-state icon="⌕" eyebrow="404" title="We can't find that event" message="It may have been removed, unpublished, or the link is incorrect.">
        <a class="btn btn-primary" routerLink="/events">Browse all events</a><a class="btn btn-ghost" routerLink="/">Home</a>
      </lk-state>
      <lk-site-footer />
    } @else if (failure() === 'error') {
      <lk-state icon="⚡" eyebrow="Connection problem" title="We couldn't load this event" message="Please check your connection and try again.">
        <button class="btn btn-primary" type="button" (click)="load()">Try again</button><a class="btn btn-ghost" routerLink="/events">All events</a>
      </lk-state>
      <lk-site-footer />
    } @else {
      <div class="container sk"><div class="skeleton sk-hero"></div><div class="skeleton sk-line"></div><div class="skeleton sk-line short"></div></div>
    }
  `,
  styles: [`
    .page{padding-bottom:0}
    .hero{position:relative;overflow:hidden;min-height:clamp(420px,62vh,640px);display:flex;align-items:flex-end;border-bottom:1px solid var(--line);background:radial-gradient(circle at 15% 25%,color-mix(in srgb,var(--event-secondary) 60%,transparent),transparent 45%),radial-gradient(circle at 85% 10%,color-mix(in srgb,var(--event-primary) 28%,transparent),transparent 40%),#0d0b11}
    .cover{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}
    .shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(8,7,11,.35) 0%,rgba(8,7,11,.55) 45%,rgba(8,7,11,.96) 100%)}
    .hero-in{position:relative;z-index:1;display:grid;gap:16px;padding-top:56px;padding-bottom:clamp(32px,5vw,56px)}
    .crumbs{display:flex;gap:8px;font-size:13px;color:#b9b2bd}.crumbs a{text-decoration:none}.crumbs a:hover{color:var(--gold-2)}
    .tags{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.tag{display:inline-flex;align-items:center;height:26px;padding:0 12px;border-radius:999px;font-size:11px;letter-spacing:.1em;text-transform:uppercase;border:1px solid rgba(255,255,255,.22);color:#e5dfe8;background:rgba(0,0,0,.35)}
    h1{margin:0;font-size:clamp(40px,7vw,92px);max-width:16ch;text-wrap:balance}.short{margin:0;max-width:60ch;font-size:clamp(16px,2vw,20px);line-height:1.6;color:#d3ccd7}
    .facts{display:flex;flex-wrap:wrap;gap:12px 40px;margin:8px 0 0}.facts div{display:grid;gap:3px}.facts dt{font-size:11px;letter-spacing:.14em;text-transform:uppercase;color:var(--gold)}.facts dd{margin:0;font-size:16px;font-weight:600}
    .notice{margin-top:22px;padding:16px 20px;border-radius:14px;border:1px solid var(--line);background:var(--surface);font-size:14.5px;color:#cfc9d3}.notice.bad{border-color:rgba(239,107,107,.45);background:rgba(239,107,107,.08)}
    .layout{display:grid;grid-template-columns:minmax(0,1fr) 400px;gap:clamp(28px,4vw,64px);align-items:start;padding-top:clamp(32px,5vw,64px)}
    .main{display:grid;gap:clamp(36px,5vw,64px);min-width:0}h2{margin:8px 0 16px;font-size:clamp(28px,3.6vw,40px)}
    .prose{margin:0 0 14px;color:#c4bdc8;line-height:1.75;font-size:16.5px;max-width:68ch}
    .highlights{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px}.highlights li{padding:16px 18px 16px 44px;position:relative;border:1px solid var(--line);border-radius:14px;background:var(--surface);color:#d6d0d9;line-height:1.5}.highlights li::before{content:"✦";position:absolute;left:16px;top:15px;color:var(--gold)}
    .gallery{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px}.gallery img{width:100%;aspect-ratio:3/2;object-fit:cover;border-radius:14px;border:1px solid var(--line)}
    .card{display:grid;gap:10px;padding:22px 24px;border:1px solid var(--line);border-radius:var(--radius);background:var(--surface);justify-items:start}.card strong{font-family:var(--display);font-size:22px}.card span,.card p{color:#b9b2bd;line-height:1.6;margin:0}
    .sm{min-height:40px;font-size:14px;padding:0 18px}
    .org{grid-template-columns:auto 1fr;align-items:start;gap:20px}.logo{width:72px;height:72px;object-fit:contain;border-radius:16px;background:#fff1;padding:8px}
    .contact{list-style:none;margin:12px 0 0;padding:0;display:grid;gap:8px;font-size:14.5px}.contact span{display:inline-block;min-width:110px;color:var(--muted)}.contact a{color:var(--gold-2);overflow-wrap:anywhere}
    .info{margin:0 0 18px;padding-left:20px;color:#c4bdc8;line-height:1.75;display:grid;gap:4px}
    details{border:1px solid var(--line);border-radius:14px;background:var(--surface);padding:0 20px;margin-top:12px}summary{cursor:pointer;padding:16px 0;font-weight:600}details p{margin:0 0 14px;color:#b9b2bd;line-height:1.7}
    .book{position:sticky;top:88px}.panel{border:1px solid rgba(212,166,78,.28);border-radius:24px;padding:24px;background:linear-gradient(180deg,#17131c,#100e14);box-shadow:0 30px 80px rgba(0,0,0,.45)}.panel h2{font-size:28px;margin:6px 0 18px}
    .closed{display:grid;gap:8px;padding:16px;border-radius:14px;background:rgba(255,255,255,.04);border:1px solid var(--line);margin-bottom:16px;justify-items:start}.closed span{color:#b9b2bd;font-size:14px;line-height:1.5}
    .types{display:grid;gap:12px}.type{padding:16px;border:1px solid var(--line);border-radius:16px;background:rgba(255,255,255,.02);display:grid;gap:10px}.type.off{opacity:.55}
    .type-top{display:flex;justify-content:space-between;gap:14px}.type h3{margin:0;font-size:17px}.type p{margin:4px 0 0;font-size:13px;color:#a59eaa;line-height:1.45}.tp{font-weight:700;font-size:18px;white-space:nowrap}
    .type-foot{display:flex;align-items:center;justify-content:space-between;gap:10px}.stock{font-size:12.5px;color:var(--ok)}.stock.sold{color:var(--bad)}.type small{color:var(--muted);font-size:12px}
    .qty{display:inline-flex;align-items:center;border:1px solid var(--line);border-radius:999px;overflow:hidden}.qty button{width:42px;height:40px;background:transparent;border:0;color:var(--text);font-size:20px;cursor:pointer}.qty button:disabled{opacity:.3;cursor:not-allowed}.qty button:hover:not(:disabled){background:rgba(212,166,78,.15)}.qty output{min-width:34px;text-align:center;font-weight:700}
    .summary{margin-top:18px;padding-top:18px;border-top:1px solid var(--line);display:grid;gap:12px}.summary>div{display:flex;justify-content:space-between;align-items:baseline}.summary strong{font-size:24px}.summary .btn{width:100%}.fine{margin:0;font-size:12px;color:var(--muted);text-align:center}
    .sticky{display:none}
    .sk{padding-top:24px;display:grid;gap:16px}.sk-hero{height:min(52vh,460px)}.sk-line{height:28px;width:70%}.sk-line.short{width:40%}
    @media(max-width:1000px){.layout{grid-template-columns:1fr}.book{position:static;order:-1}.main{order:2}.book{scroll-margin-top:80px}}
    @media(max-width:1000px){.sticky{display:flex;position:fixed;left:0;right:0;bottom:0;z-index:40;justify-content:space-between;align-items:center;gap:14px;padding:12px clamp(16px,4vw,28px) calc(12px + env(safe-area-inset-bottom));background:rgba(12,10,16,.94);backdrop-filter:blur(14px);border-top:1px solid var(--line)}.sticky small{display:block;color:var(--muted);font-size:11px;letter-spacing:.08em;text-transform:uppercase}.sticky strong{font-size:20px}:host{display:block;padding-bottom:78px}}
    @media(max-width:620px){.hero{min-height:480px}.org{grid-template-columns:1fr}.facts{gap:14px 28px}}
  `]
})
export class EventPageComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly booking = inject(BookingStoreService);
  private readonly seo = inject(SeoService);
  private readonly doc = inject(DOCUMENT);
  private readonly response = inject(RESPONSE_INIT, { optional: true });

  readonly event = signal<EventView | null>(null);
  readonly failure = signal<'notfound' | 'error' | null>(null);
  readonly cart = signal<Map<string, number>>(new Map());
  readonly nowMs = signal(Date.now());
  readonly cartCount = computed(() => Array.from(this.cart().values()).reduce((a, b) => a + b, 0));
  readonly cartTotal = computed(() => Array.from(this.cart().entries()).reduce((sum, [id, q]) => sum + (this.event()?.ticketTypes.find(t => t.id === id)?.priceMinorUnits || 0) * q, 0));

  constructor() {
    // Browser-only clock so sale windows flip without a reload. Never runs during server rendering.
    afterNextRender(() => { const h = setInterval(() => this.nowMs.set(Date.now()), 30000); this.doc.defaultView?.addEventListener('pagehide', () => clearInterval(h), { once: true }); });
  }

  ngOnInit(): void { this.load(); }

  load(): void {
    const slug = this.route.snapshot.paramMap.get('slug') || '';
    this.failure.set(null);
    this.api.event(slug).subscribe({
      next: e => { this.event.set(e); this.booking.setEvent(e); this.applySeo(e); },
      error: (err: HttpErrorResponse) => {
        const notFound = err?.status === 404 || err?.status === 400;
        this.failure.set(notFound ? 'notfound' : 'error');
        if (this.response) this.response.status = notFound ? 404 : 503;
        this.seo.set({ title: notFound ? 'Event not found — Neelastack Events' : 'Event unavailable — Neelastack Events', description: 'This event is not available right now.', path: '/events/' + encodeURIComponent(slug), noindex: true });
      }
    });
  }

  // ---------- SEO ----------
  private applySeo(e: EventView): void {
    const site = environment.siteUrl.replace(/\/$/, '');
    const url = `${site}/events/${e.slug}`;
    const where = [e.venueName, e.city].filter(Boolean).join(', ');
    const desc = e.shortDescription || (e.description || '').slice(0, 200) || `Book tickets for ${e.name}${where ? ' at ' + where : ''}.`;
    const image = toAbsoluteUrl(site, this.cover(e));
    const organizer = e.organizer?.name || e.brand.organizerName;
    const cancelled = e.salesState === 'CANCELLED';
    const offers = e.ticketTypes.filter(t => t.status === 'ACTIVE').map(t => ({
      '@type': 'Offer', name: t.name, url, price: (t.priceMinorUnits / 100).toFixed(2), priceCurrency: t.currency,
      availability: t.availableQuantity > 0 ? 'https://schema.org/InStock' : 'https://schema.org/SoldOut',
      ...(t.saleStartsAt ? { validFrom: t.saleStartsAt } : {})
    }));
    this.seo.set({
      title: `${e.name} — Tickets | ${environment.platformName}`,
      description: `${desc}${desc.length < 120 ? ` ${eventDate(e.startsAt, e.timezone)}.` : ''}`,
      path: '/events/' + e.slug, image, type: 'event',
      jsonLd: {
        '@context': 'https://schema.org', '@type': 'Event', name: e.name, description: desc,
        startDate: e.startsAt, ...(e.endsAt ? { endDate: e.endsAt } : {}),
        eventStatus: cancelled ? 'https://schema.org/EventCancelled' : 'https://schema.org/EventScheduled',
        eventAttendanceMode: 'https://schema.org/OfflineEventAttendanceMode',
        location: { '@type': 'Place', name: e.venueName || where || 'Venue', address: { '@type': 'PostalAddress', streetAddress: e.venueAddress, addressLocality: e.city, addressRegion: e.state, addressCountry: e.country || 'IN' } },
        ...(image ? { image: [image] } : {}),
        organizer: { '@type': 'Organization', name: organizer, ...(e.organizer?.website ? { url: e.organizer.website } : {}) },
        ...(offers.length ? { offers } : {}), url
      }
    });
  }

  // ---------- presentation helpers ----------
  cover(e: EventView) { return safeImage(e.coverImageUrl || e.brand.eventBannerUrl); }
  gallery(e: EventView) { return (e.gallery || []).map(safeImage).filter((x): x is string => !!x); }
  orgLogo(e: EventView) { return safeImage(e.organizer?.logoUrl || e.brand.organizerLogoUrl); }
  mapLink(e: EventView) { return e.mapUrl && /^https:\/\//i.test(e.mapUrl) ? e.mapUrl : null; }
  paragraphs(text?: string | null) { return (text || '').split(/\n{1,}/).map(x => x.trim()).filter(Boolean); }
  label(e: EventView) { return SALES_LABEL[e.salesState || 'AVAILABLE'] || ''; }
  longDate(e: EventView) { return eventLongDate(e.startsAt, e.timezone); }
  time(e: EventView) { return eventTime(e.startsAt, e.timezone); }
  endTime(e: EventView) { return eventTime(e.endsAt, e.timezone); }
  money(minor: number | null | undefined, currency = 'INR') { return rupees(minor, currency); }
  bookable(e: EventView) { return canBook(e.salesState); }
  closedTitle(e: EventView) { switch (e.salesState) { case 'SOLD_OUT': return 'Sold out'; case 'BOOKING_NOT_STARTED': return 'Booking opens soon'; case 'BOOKING_CLOSED': return 'Booking is closed'; case 'CANCELLED': return 'Event cancelled'; default: return 'Event completed'; } }
  closedMessage(e: EventView) {
    switch (e.salesState) {
      case 'SOLD_OUT': return 'All tickets have been claimed. Check back — released tickets may reappear.';
      case 'BOOKING_NOT_STARTED': return e.bookingStartsAt ? `Ticket sales open on ${eventDateTime(e.bookingStartsAt, e.timezone)}.` : 'Ticket sales have not opened yet.';
      case 'BOOKING_CLOSED': return 'Online ticket sales have ended for this event.';
      case 'CANCELLED': return 'This event will not take place.';
      default: return 'This event has already taken place.';
    }
  }

  // ---------- ticket selection (unchanged behaviour; server re-validates everything at checkout) ----------
  ticketState(t: TicketTypeView): 'ON_SALE' | 'COMING_SOON' | 'CLOSED' | 'SOLD_OUT' | 'PAUSED' {
    if (t.availableQuantity <= 0) return 'SOLD_OUT';
    if (t.status !== 'ACTIVE') return t.status === 'PAUSED' ? 'PAUSED' : 'CLOSED';
    const now = this.nowMs();
    if (t.saleStartsAt && new Date(t.saleStartsAt).getTime() > now) return 'COMING_SOON';
    if (t.saleEndsAt && new Date(t.saleEndsAt).getTime() <= now) return 'CLOSED';
    return 'ON_SALE';
  }
  canSelect(t: TicketTypeView): boolean { return this.ticketState(t) === 'ON_SALE' && t.availableQuantity >= t.minPerOrder; }
  ticketStateLabel(t: TicketTypeView): string {
    switch (this.ticketState(t)) { case 'COMING_SOON': return 'Coming soon'; case 'CLOSED': return 'Sales closed'; case 'SOLD_OUT': return 'Sold out'; case 'PAUSED': return 'Temporarily paused'; default: return `${t.availableQuantity} available`; }
  }
  ticketWindowLabel(t: TicketTypeView): string {
    const now = this.nowMs(); const tz = this.event()?.timezone;
    if (t.saleStartsAt && new Date(t.saleStartsAt).getTime() > now) return `Sales open ${eventDateTime(t.saleStartsAt, tz)}`;
    if (t.saleEndsAt && new Date(t.saleEndsAt).getTime() > now) return `Sales close ${eventDateTime(t.saleEndsAt, tz)}`;
    return '';
  }
  quantity(id: string) { return this.cart().get(id) || 0; }
  max(t: TicketTypeView) { return Math.min(t.maxPerOrder, t.availableQuantity); }
  increment(t: TicketTypeView) {
    if (!this.canSelect(t)) return;
    const current = this.quantity(t.id); const upper = this.max(t);
    const next = current === 0 ? Math.min(t.minPerOrder, upper) : Math.min(current + 1, upper);
    if (next > 0) this.cart.update(m => new Map(m).set(t.id, next));
  }
  decrement(t: TicketTypeView) {
    const current = this.quantity(t.id); if (current <= 0) return;
    this.cart.update(m => { const n = new Map(m); if (current <= t.minPerOrder) n.delete(t.id); else n.set(t.id, current - 1); return n; });
  }
  scrollToTickets() { this.doc.getElementById('tickets')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  continueToCheckout() {
    const e = this.event(); if (!e || this.cartCount() === 0 || !this.bookable(e)) return;
    const valid = new Map(this.cart());
    for (const [id, q] of valid) { const t = e.ticketTypes.find(x => x.id === id); if (!t || !this.canSelect(t) || q < t.minPerOrder || q > t.maxPerOrder || q > t.availableQuantity) valid.delete(id); }
    this.cart.set(valid); if (valid.size === 0) return;
    this.booking.setCart(Array.from(valid.entries()).map(([ticketTypeId, quantity]) => ({ ticketTypeId, quantity })));
    this.router.navigate(['/checkout'], { queryParams: { slug: e.slug } });
  }
}
