import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { EventCard } from '../core/api/api.models';
import { SALES_LABEL, canBook, eventDay, eventMonth, eventTime, eventWeekday, rupees, safeImage } from '../core/format';

@Component({
  selector: 'lk-event-card', standalone: true, imports: [RouterLink],
  template: `
    <a class="card" [routerLink]="['/events', e.slug]" [attr.aria-label]="e.name + ', ' + label">
      <div class="media" [class.no-img]="!img">
        @if (img) { <img [src]="img" [alt]="''" width="640" height="400" [attr.loading]="priority ? 'eager' : 'lazy'" [attr.fetchpriority]="priority ? 'high' : null" decoding="async" /> }
        @else { <span class="ph" aria-hidden="true">{{ initial }}</span> }
        <span class="badge" [class]="'badge ' + e.salesState">{{ label }}</span>
        <div class="date" aria-hidden="true"><b>{{ day }}</b><span>{{ month }}</span></div>
      </div>
      <div class="body">
        @if (e.category) { <span class="cat">{{ e.category }}</span> }
        <h3>{{ e.name }}</h3>
        <p class="meta">{{ weekday }} · {{ time }}@if (e.venueName) { · {{ e.venueName }} }@if (e.city) {, {{ e.city }} }</p>
        <div class="foot">
          <div class="price">@if (e.startingPriceMinor != null) { <small>From</small> <strong>{{ price }}</strong> } @else { <small>Pricing soon</small> }</div>
          <span class="cta" [class.off]="!bookable">{{ cta }} <i aria-hidden="true">→</i></span>
        </div>
      </div>
    </a>
  `,
  styles: [`
    :host{display:block}
    .card{display:flex;flex-direction:column;height:100%;text-decoration:none;color:inherit;border-radius:var(--radius);overflow:hidden;background:var(--surface);border:1px solid var(--line);transition:transform .25s ease,border-color .25s ease,box-shadow .25s ease}
    .card:hover{transform:translateY(-4px);border-color:rgba(212,166,78,.45);box-shadow:0 24px 60px rgba(0,0,0,.45)}
    .media{position:relative;aspect-ratio:16/10;background:#15121a;overflow:hidden}
    .media img{width:100%;height:100%;object-fit:cover;display:block;transition:transform .6s ease}.card:hover .media img{transform:scale(1.04)}
    .media::after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,rgba(8,7,11,.05) 45%,rgba(8,7,11,.7));pointer-events:none}
    .no-img{background:radial-gradient(circle at 25% 30%,rgba(122,31,61,.75),transparent 55%),radial-gradient(circle at 80% 20%,rgba(212,166,78,.35),transparent 50%),#15121a}
    .ph{position:absolute;inset:0;display:grid;place-items:center;font-family:var(--display);font-size:72px;color:rgba(255,255,255,.2)}
    .badge{position:absolute;top:14px;left:14px;z-index:2}
    .date{position:absolute;top:12px;right:12px;z-index:2;width:54px;padding:7px 0 6px;text-align:center;border-radius:14px;background:rgba(8,7,11,.72);backdrop-filter:blur(8px);border:1px solid rgba(255,255,255,.14);display:grid;line-height:1.05}
    .date b{font-size:20px;font-family:var(--display)}.date span{font-size:10px;letter-spacing:.14em;color:var(--gold-2)}
    .body{display:flex;flex-direction:column;gap:8px;padding:18px 20px 20px;flex:1}
    .cat{font-size:11px;letter-spacing:.16em;text-transform:uppercase;color:var(--gold)}
    h3{margin:0;font-size:22px;line-height:1.2;font-weight:600}
    .meta{margin:0;color:#a59eaa;font-size:13.5px;line-height:1.5}
    .foot{margin-top:auto;padding-top:14px;display:flex;align-items:flex-end;justify-content:space-between;gap:12px}
    .price small{display:block;font-size:11px;color:var(--muted);letter-spacing:.06em}.price strong{font-size:19px}
    .cta{font-size:14px;font-weight:600;color:var(--gold-2);white-space:nowrap}.cta i{font-style:normal;display:inline-block;transition:transform .2s}.card:hover .cta i{transform:translateX(4px)}.cta.off{color:#8f8894}
    @media(prefers-reduced-motion:reduce){.card,.media img,.cta i{transition:none}}
  `]
})
export class EventCardComponent {
  @Input({ required: true }) e!: EventCard;
  @Input() priority = false;
  get img() { return safeImage(this.e.coverImageUrl); }
  get label() { return SALES_LABEL[this.e.salesState] || ''; }
  get bookable() { return canBook(this.e.salesState); }
  get day() { return eventDay(this.e.startsAt, this.e.timezone); }
  get month() { return eventMonth(this.e.startsAt, this.e.timezone); }
  get weekday() { return eventWeekday(this.e.startsAt, this.e.timezone); }
  get time() { return eventTime(this.e.startsAt, this.e.timezone); }
  get price() { return rupees(this.e.startingPriceMinor, this.e.currency); }
  get initial() { return (this.e.name || '?').charAt(0).toUpperCase(); }
  get cta() { switch (this.e.salesState) { case 'AVAILABLE': case 'SELLING_FAST': return 'Book tickets'; case 'BOOKING_NOT_STARTED': return 'View details'; default: return 'View event'; } }
}
