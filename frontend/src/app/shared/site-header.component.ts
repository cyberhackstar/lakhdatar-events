import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { BrandMarkComponent } from './brand-mark.component';

/** Platform-level header: the Neelastack Events platform identity. Organizer identity is shown in page content. */
@Component({
  selector: 'lk-site-header', standalone: true, imports: [RouterLink, RouterLinkActive, BrandMarkComponent],
  template: `
    <header class="hdr">
      <div class="container hdr-in">
        <a routerLink="/" class="brand" aria-label="Neelastack Events home">
          <lk-brand-mark label="Events" [height]="48" />
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
    .hdr-in{min-height:78px;display:flex;align-items:center;justify-content:space-between;gap:20px}
    .brand{display:flex;align-items:center;text-decoration:none;font-size:18px;letter-spacing:.01em;color:var(--text);min-height:48px}
    nav{display:flex;gap:6px;align-items:center}nav a{padding:10px 14px;border-radius:999px;text-decoration:none;color:#cfc9d3;font-size:14px;transition:color .15s,background .15s}
    nav a:hover,nav a.on{color:#fff;background:rgba(255,255,255,.07);box-shadow:0 0 0 1px rgba(255,255,255,.05) inset}
    @media(max-width:520px){.hdr-in{min-height:70px}.brand{font-size:16px}.brand lk-brand-mark{transform:scale(.86);transform-origin:left center}nav a{padding:9px 10px;font-size:13px}}
  `]
})
export class SiteHeaderComponent {}
