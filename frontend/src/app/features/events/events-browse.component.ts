import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap, tap } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { EventCard, EventQuery, Facets, PageView } from '../../core/api/api.models';
import { SeoService } from '../../core/seo/seo.service';
import { EventCardComponent } from '../../shared/event-card.component';
import { SiteHeaderComponent } from '../../shared/site-header.component';
import { SiteFooterComponent } from '../../shared/site-footer.component';
import { StatePanelComponent } from '../../shared/state-panel.component';

const PAGE_SIZE = 12;

@Component({
  selector: 'lk-events-browse', standalone: true,
  imports: [RouterLink, EventCardComponent, SiteHeaderComponent, SiteFooterComponent, StatePanelComponent],
  template: `
    <lk-site-header />
    <main class="container wrap">
      <div class="eyebrow">Discover</div>
      <h1 class="display">All events</h1>

      <form class="filters" (submit)="$event.preventDefault(); apply(q.value, cat.value, city.value, from.value, to.value, price.value)">
        <label class="f wide"><span>Search</span><input #q type="search" [value]="query.q || ''" placeholder="Event, venue or city" maxlength="100" /></label>
        <label class="f"><span>Category</span>
          <select #cat><option value="">All categories</option>@for (c of facets().categories; track c) { <option [value]="c" [selected]="c.toLowerCase() === (query.category || '').toLowerCase()">{{ c }}</option> }</select></label>
        <label class="f"><span>City</span>
          <select #city><option value="">All cities</option>@for (c of facets().cities; track c) { <option [value]="c" [selected]="c.toLowerCase() === (query.city || '').toLowerCase()">{{ c }}</option> }</select></label>
        <label class="f"><span>From</span><input #from type="date" [value]="query.from || ''" /></label>
        <label class="f"><span>To</span><input #to type="date" [value]="query.to || ''" /></label>
        <label class="f"><span>Max price</span>
          <select #price><option value="">Any price</option>@for (p of prices; track p) { <option [value]="p" [selected]="p === query.maxPrice">Up to ₹{{ p }}</option> }</select></label>
        <div class="f actions"><button class="btn btn-primary" type="submit">Apply</button>@if (hasFilters()) { <a class="btn btn-ghost" routerLink="/events">Clear</a> }</div>
      </form>

      @if (state() === 'loading') {
        <div class="grid-cards">@for (i of [1,2,3,4,5,6]; track i) { <div class="skeleton sk-card"></div> }</div>
      } @else if (state() === 'error') {
        <lk-state icon="⚡" eyebrow="Connection problem" title="We couldn't load events" message="Please check your connection and try again.">
          <button class="btn btn-primary" type="button" (click)="reload()">Try again</button>
        </lk-state>
      } @else if (!result().items.length) {
        <lk-state icon="⌕" eyebrow="No matches" title="No events found" message="Try different keywords or clear your filters to see everything that's on.">
          <a class="btn btn-primary" routerLink="/events">Clear filters</a>
        </lk-state>
      } @else {
        <p class="count" aria-live="polite">{{ result().total }} {{ result().total === 1 ? 'event' : 'events' }}</p>
        <div class="grid-cards">@for (e of result().items; track e.id; let i = $index) { <lk-event-card [e]="e" [priority]="i < 3" /> }</div>
        @if (result().totalPages > 1) {
          <nav class="pager" aria-label="Pagination">
            <button class="btn btn-ghost" type="button" [disabled]="result().page <= 0" (click)="go(result().page - 1)">← Previous</button>
            <span>Page {{ result().page + 1 }} of {{ result().totalPages }}</span>
            <button class="btn btn-ghost" type="button" [disabled]="result().page + 1 >= result().totalPages" (click)="go(result().page + 1)">Next →</button>
          </nav>
        }
      }
    </main>
    <lk-site-footer />
  `,
  styles: [`
    .wrap{padding-top:clamp(36px,6vw,72px)}h1{margin:8px 0 28px;font-size:clamp(38px,6vw,64px)}
    .filters{display:grid;grid-template-columns:2fr repeat(5,1fr) auto;gap:12px;align-items:end;padding:18px;border:1px solid var(--line);border-radius:var(--radius);background:var(--surface);margin-bottom:30px}
    .f{display:grid;gap:6px;min-width:0}.f span{font-size:11px;letter-spacing:.1em;text-transform:uppercase;color:var(--muted)}
    .f input,.f select{height:48px;border-radius:13px;border:1px solid var(--line);background:#0e0c12;color:var(--text)!important;-webkit-text-fill-color:var(--text);padding:0 13px;font-size:16px;min-width:0;width:100%;color-scheme:dark}.f input::placeholder{color:#7f7785;opacity:1}
    .f input:focus,.f select:focus{outline:none;border-color:var(--gold);box-shadow:0 0 0 4px rgba(212,166,78,.07)}
    .actions{display:flex;flex-direction:row;gap:8px}.actions .btn{min-height:46px;padding:0 20px}
    .count{color:var(--muted);font-size:14px;margin:0 0 16px}
    .sk-card{height:400px}
    .pager{display:flex;align-items:center;justify-content:center;gap:18px;margin-top:40px;flex-wrap:wrap;color:#b9b2bd;font-size:14px}
    @media(max-width:1100px){.filters{grid-template-columns:repeat(3,1fr)}.f.wide{grid-column:1/-1}}
    @media(max-width:620px){.filters{grid-template-columns:1fr 1fr}.actions{grid-column:1/-1}.actions .btn{flex:1}}
  `]
})
export class EventsBrowseComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  readonly prices = [500, 1000, 2500, 5000];
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly result = signal<PageView<EventCard>>({ items: [], page: 0, size: PAGE_SIZE, total: 0, totalPages: 0 });
  readonly facets = signal<Facets>({ categories: [], cities: [] });
  query: EventQuery = {};

  ngOnInit(): void {
    this.api.eventFacets().pipe(catchError(() => of<Facets>({ categories: [], cities: [] }))).subscribe(f => this.facets.set(f));
    this.route.queryParamMap.pipe(
      tap(p => {
        const num = (k: string) => { const v = Number(p.get(k)); return p.get(k) && Number.isFinite(v) && v >= 0 ? v : undefined; };
        this.query = { q: p.get('q') || undefined, category: p.get('category') || undefined, city: p.get('city') || undefined,
          from: p.get('from') || undefined, to: p.get('to') || undefined, maxPrice: num('maxPrice'), page: Math.floor(num('page') || 0), size: PAGE_SIZE };
        this.state.set('loading');
        this.seo.set({
          title: this.query.category ? `${this.query.category} events — Neelastack Events` : 'All events — Neelastack Events',
          description: 'Browse upcoming live events and book tickets securely online.',
          path: '/events', noindex: this.hasFilters()
        });
      }),
      switchMap(() => this.fetch())
    ).subscribe(r => this.accept(r));
  }

  private fetch() { return this.api.events(this.query).pipe(catchError(() => of(null))); }
  private accept(r: PageView<EventCard> | null): void { if (r) { this.result.set(r); this.state.set('ready'); } else { this.state.set('error'); } }

  hasFilters(): boolean { const q = this.query; return !!(q.q || q.category || q.city || q.from || q.to || q.maxPrice !== undefined || (q.page ?? 0) > 0); }

  apply(q: string, category: string, city: string, from: string, to: string, price: string): void {
    this.router.navigate(['/events'], { queryParams: { q: q.trim() || null, category: category || null, city: city || null, from: from || null, to: to || null, maxPrice: price || null, page: null } });
  }
  go(page: number): void { this.router.navigate(['/events'], { queryParams: { page: page || null }, queryParamsHandling: 'merge' }); }
  reload(): void { this.state.set('loading'); this.fetch().subscribe(r => this.accept(r)); }
}
