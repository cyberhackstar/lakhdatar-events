import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Platform-level header: the Neelastack Events platform identity. Organizer identity is shown in page content. */
@Component({
  selector: 'lk-site-header', standalone: true, imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="hdr">
      <div class="container hdr-in">
        <a routerLink="/" class="brand" aria-label="Neelastack Events home">
          <img src="/assets/neelastack-logo.png" alt="" width="28" height="28" />
          <span><b>Neelastack</b> Events</span>
        </a>
        <nav aria-label="Primary">
          <a routerLink="/events" routerLinkActive="on" [routerLinkActiveOptions]="{exact:false}">All events</a>
          <a routerLink="/recover" routerLinkActive="on">Find my ticket</a>
        </nav>
      </div>
    </header>
  `,
  styles: [`
    .hdr{position:sticky;top:0;z-index:30;background:rgba(8,7,11,.82);backdrop-filter:blur(14px);border-bottom:1px solid var(--line)}
    .hdr-in{height:68px;display:flex;align-items:center;justify-content:space-between;gap:16px}
    .brand{display:flex;align-items:center;gap:11px;text-decoration:none;font-size:17px;letter-spacing:.01em;color:var(--text)}
    .brand b{letter-spacing:.14em;text-transform:uppercase;font-size:13px;color:var(--gold-2);margin-right:2px}
    nav{display:flex;gap:6px}nav a{padding:10px 14px;border-radius:999px;text-decoration:none;color:#cfc9d3;font-size:14px;transition:color .15s,background .15s}
    nav a:hover,nav a.on{color:#fff;background:rgba(255,255,255,.07)}
    @media(max-width:520px){.brand span{font-size:15px}nav a{padding:9px 10px;font-size:13px}}
  `]
})
export class SiteHeaderComponent {}
