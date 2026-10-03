import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { AdminStore } from './admin-store.service';

/** Console frame: persistent sidebar (tabs on mobile) + routed pages. Every link is a real route, nothing relies on #anchors. */
@Component({
  selector: 'lk-admin-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, BrandMarkComponent],
  template: `
    <div class="shell">
      <aside>
        <a routerLink="/admin" class="brand" aria-label="Console home"><lk-brand-mark label="Events" [height]="30" /></a>
        <div class="role">{{ roleLabel() }}</div>
        <nav aria-label="Console">
          <a routerLink="/admin" routerLinkActive="on" [routerLinkActiveOptions]="{exact:true}"><i>◧</i><span>Overview</span></a>
          <a routerLink="/admin/events" routerLinkActive="on"><i>◈</i><span>Events</span></a>
          @if (canAdminister()) { <a routerLink="/admin/events/new" routerLinkActive="on"><i>＋</i><span>Create event</span></a> }
          @if (isAdmin()) { <a routerLink="/admin/organizers" routerLinkActive="on"><i>▣</i><span>Organizers</span></a> }
          @if (canAdminister()) { <a routerLink="/admin/team" routerLinkActive="on"><i>◉</i><span>Team &amp; access</span></a> }
          @if (isManager()) { <a routerLink="/admin/complimentary" routerLinkActive="on"><i>✦</i><span>Complimentary tickets</span></a> }
          <a routerLink="/staff"><i>⌖</i><span>Scanner console</span></a>
        </nav>
        <div class="bottom">
          <div class="who">{{ auth.fullName() }}</div>
          <a routerLink="/" target="_blank">View public site ↗</a>
          <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Powered by Neelastack ↗</a>
          <button type="button" (click)="logout()">Sign out</button>
        </div>
      </aside>
      <main><router-outlet /></main>
    </div>
  `,
  styles: [`
    :host{display:block;color-scheme:light}
    .shell{min-height:100vh;background:#f4f1ec;color:#1a151b;display:grid;grid-template-columns:260px minmax(0,1fr)}
    aside{background:#0b090e;color:#fff;padding:24px 18px;display:flex;flex-direction:column;position:sticky;top:0;height:100vh;overflow-y:auto}
    .brand{display:inline-flex;text-decoration:none;color:#fff;padding:4px 8px;font-size:18px}
    .role{font-size:10px;letter-spacing:.18em;color:#7d7582;margin:30px 10px 10px;font-weight:700}
    nav{display:grid;gap:4px}
    nav a{display:flex;align-items:center;gap:12px;padding:12px;border-radius:11px;color:#a59cab;text-decoration:none;font-size:14px;font-weight:500}
    nav a i{font-style:normal;width:18px;text-align:center;opacity:.8}
    nav a:hover{background:rgba(255,255,255,.06);color:#fff}
    nav a.on{background:rgba(212,166,78,.14);color:#f0cf8c}
    .bottom{margin-top:auto;display:grid;gap:12px;padding:20px 10px 0}
    .who{font-size:13px;color:#d9d2dc;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
    .bottom a,.bottom button{font-size:12px;color:#8d8492;text-decoration:none;background:transparent;border:0;text-align:left;padding:0;cursor:pointer;min-height:0}
    .bottom a:hover,.bottom button:hover{color:#fff}
    main{padding:40px clamp(16px,4vw,56px) 72px;min-width:0}
    @media(max-width:860px){
      .shell{display:block}
      aside{position:static;height:auto;flex-direction:row;flex-wrap:wrap;align-items:center;padding:12px 14px 0;gap:6px 12px;overflow:visible}
      .role{display:none}
      .brand{order:1}
      .bottom{order:2;margin:0 0 0 auto;padding:0;display:flex;align-items:center;gap:14px}
      .bottom .who,.bottom a{display:none}
      nav{order:3;flex:1 0 100%;display:flex;overflow-x:auto;gap:6px;padding:10px 0 12px;-webkit-overflow-scrolling:touch;scrollbar-width:none}
      nav::-webkit-scrollbar{display:none}
      nav a{flex:0 0 auto;padding:9px 14px;border-radius:999px;background:rgba(255,255,255,.06);font-size:13px;white-space:nowrap}
      nav a i{display:none}
      main{padding:24px 14px 56px}
    }
  `]
})
export class AdminShellComponent {
  readonly auth = inject(AuthService);
  private readonly store = inject(AdminStore);

  isAdmin(): boolean { return this.auth.role() === 'ADMIN'; }
  isManager(): boolean { return this.auth.role() === 'EVENT_MANAGER'; }
  canAdminister(): boolean { const r = this.auth.role(); return r === 'ADMIN' || r === 'ORGANIZER'; }
  roleLabel(): string { return this.canAdminister() ? 'CONTROL CENTER' : this.isManager() ? 'ASSIGNED EVENTS ONLY' : 'OPERATIONS CONSOLE'; }

  logout(): void {
    this.store.reset();
    const done = () => { location.href = '/login'; };
    this.auth.logout().subscribe({ complete: done, error: done });
  }
}
