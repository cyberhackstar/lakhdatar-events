import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { ApiService } from '../../core/api/api.service';
import { EventCard, Facets } from '../../core/api/api.models';
import { SeoService } from '../../core/seo/seo.service';
import { environment } from '../../../environments/environment';
import { EventCardComponent } from '../../shared/event-card.component';
import { SiteHeaderComponent } from '../../shared/site-header.component';
import { SiteFooterComponent } from '../../shared/site-footer.component';
import { StatePanelComponent } from '../../shared/state-panel.component';

@Component({
  selector: 'lk-home', standalone: true,
  imports: [RouterLink, EventCardComponent, SiteHeaderComponent, SiteFooterComponent, StatePanelComponent],
  template: `
    <lk-site-header />
    <main>
      <section class="hero">
        <div class="glow" aria-hidden="true"></div>
        <div class="container hero-in">
          @if (organizerName()) { <div class="presenter"><span class="dot"></span> Now presenting <b>{{ organizerName() }}</b></div> }
          @else { <div class="presenter"><span class="dot"></span> Neelastack Event Platform</div> }
          <h1 class="display">Live experiences,<br /><em>beautifully ticketed.</em></h1>
          <p class="lede">Discover upcoming events, choose your pass and walk in with a secure digital QR ticket. Verified payments, instant confirmation.</p>
          <form class="search" (submit)="$event.preventDefault(); search(q.value)" role="search">
            <label class="sr-only" for="q">Search events</label>
            <input #q id="q" type="search" name="q" placeholder="Search events, venues or cities" maxlength="100" autocomplete="off" />
            <button class="btn btn-primary" type="submit">Search</button>
          </form>
          @if (facets().categories.length) {
            <div class="chips" aria-label="Browse by category">
              @for (c of facets().categories; track c) { <a class="chip" routerLink="/events" [queryParams]="{category: c}">{{ c }}</a> }
            </div>
          }
        </div>
      </section>

      @if (state() === 'loading') {
        <section class="container block"><div class="grid-cards">@for (i of [1,2,3]; track i) { <div class="skeleton sk-card"></div> }</div></section>
      } @else if (state() === 'error') {
        <lk-state icon="⚡" eyebrow="Connection problem" title="We couldn't load events" message="Please check your connection and try again in a moment.">
          <button class="btn btn-primary" type="button" (click)="load()">Try again</button>
        </lk-state>
      } @else if (!featured().length && !upcoming().length) {
        <lk-state icon="✦" eyebrow="Stay tuned" title="No events on sale right now" message="New events are announced regularly. Check back soon, or find a ticket you already bought.">
          <a class="btn btn-ghost" routerLink="/recover">Find my ticket</a>
        </lk-state>
      } @else {
        @if (featured().length) {
          <section class="container block" aria-labelledby="feat">
            <div class="head"><div><div class="eyebrow">Don't miss</div><h2 id="feat" class="display">Featured events</h2></div></div>
            <div class="grid-cards">@for (e of featured(); track e.id; let i = $index) { <lk-event-card [e]="e" [priority]="i < 3" /> }</div>
          </section>
        }
        @if (more().length) {
          <section class="container block" aria-labelledby="up">
            <div class="head"><div><div class="eyebrow">On the calendar</div><h2 id="up" class="display">Upcoming events</h2></div><a class="chip" routerLink="/events">Browse all events →</a></div>
            <div class="grid-cards">@for (e of more(); track e.id) { <lk-event-card [e]="e" /> }</div>
          </section>
        }
        <section class="container block cta-row">
          <a class="btn btn-ghost" routerLink="/events">Browse all events</a>
        </section>
      }

      <section class="container block trust" aria-label="Why book here">
        <div><b>Secure payments</b><span>Every payment is verified server-side before your ticket is issued.</span></div>
        <div><b>Instant QR tickets</b><span>Your ticket is ready the moment payment succeeds — no app needed.</span></div>
        <div><b>Never lose a ticket</b><span>Recover any paid order with your email and order number.</span></div>
      </section>
    </main>
    <lk-site-footer [organizerName]="organizerName()" />
  `,
  styles: [`
    .hero{position:relative;overflow:hidden;padding:clamp(56px,10vw,120px) 0 clamp(44px,7vw,84px);border-bottom:1px solid var(--line)}
    .glow{position:absolute;inset:0;background:radial-gradient(circle at 12% 30%,rgba(122,31,61,.55),transparent 42%),radial-gradient(circle at 88% 8%,rgba(212,166,78,.22),transparent 38%)}
    .hero-in{position:relative;display:grid;gap:22px;justify-items:start}
    .presenter{display:inline-flex;align-items:center;gap:10px;font-size:13px;letter-spacing:.06em;color:#cfc9d3;padding:8px 16px;border:1px solid var(--line);border-radius:999px;background:rgba(255,255,255,.03)}
    .presenter b{color:var(--gold-2);font-weight:600}.dot{width:7px;height:7px;border-radius:50%;background:var(--gold);box-shadow:0 0 12px var(--gold)}
    h1{margin:0;font-size:clamp(44px,8.5vw,104px)}h1 em{font-style:italic;color:var(--gold-2)}
    .lede{margin:0;max-width:640px;font-size:clamp(16px,2vw,19px);line-height:1.65;color:#b9b2bd}
    .search{display:flex;gap:10px;width:min(100%,640px);padding:8px;border-radius:999px;background:rgba(255,255,255,.05);border:1px solid var(--line)}
    .search input{flex:1;min-width:0;background:transparent;border:0;color:var(--text);font-size:16px;padding:0 16px;outline:none}.search input::placeholder{color:#8a8390}
    .search:focus-within{border-color:var(--gold)}
    .chips{display:flex;gap:10px;flex-wrap:wrap}
    .block{padding-top:clamp(40px,6vw,76px)}
    .head{display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:26px;flex-wrap:wrap}.head h2{margin:8px 0 0;font-size:clamp(30px,4.5vw,46px)}
    .sk-card{height:400px}
    .cta-row{display:flex;justify-content:center}
    .trust{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}.trust div{padding:22px 24px;border:1px solid var(--line);border-radius:var(--radius);background:var(--surface);display:grid;gap:8px}.trust b{font-family:var(--display);font-size:19px}.trust span{color:#a59eaa;font-size:14px;line-height:1.55}
    @media(max-width:760px){.trust{grid-template-columns:1fr}.search{border-radius:22px}.search .btn{padding:0 18px}}
  `]
})
export class HomeComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly seo = inject(SeoService);

  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  readonly featured = signal<EventCard[]>([]);
  readonly upcoming = signal<EventCard[]>([]);
  readonly facets = signal<Facets>({ categories: [], cities: [] });
  readonly more = computed(() => { const ids = new Set(this.featured().map(e => e.id)); return this.upcoming().filter(e => !ids.has(e.id)).slice(0, 6); });
  /** Shown only when every listed event belongs to a single organizer — nothing is hard-coded. */
  readonly organizerName = computed(() => {
    const names = new Set([...this.featured(), ...this.upcoming()].map(e => e.organizerName));
    return names.size === 1 ? [...names][0] : null;
  });

  ngOnInit(): void {
    this.seo.set({
      title: 'Neelastack Events — Book tickets for live events',
      description: 'Discover upcoming live events, pick your tickets and pay securely. Instant digital QR tickets, powered by Neelastack.',
      path: '/',
      jsonLd: { '@context': 'https://schema.org', '@type': 'WebSite', name: environment.platformName, url: environment.siteUrl,
        potentialAction: { '@type': 'SearchAction', target: environment.siteUrl + '/events?q={search_term_string}', 'query-input': 'required name=search_term_string' } }
    });
    this.load();
  }

  load(): void {
    this.state.set('loading');
    forkJoin({
      featured: this.api.featuredEvents(3),
      upcoming: this.api.upcomingEvents(0, 9),
      facets: this.api.eventFacets().pipe(catchError(() => of<Facets>({ categories: [], cities: [] })))
    }).subscribe({
      next: r => { this.featured.set(r.featured.items); this.upcoming.set(r.upcoming.items); this.facets.set(r.facets); this.state.set('ready'); },
      error: () => this.state.set('error')
    });
  }

  search(q: string): void { const v = q.trim(); this.router.navigate(['/events'], { queryParams: v ? { q: v } : {} }); }
}
