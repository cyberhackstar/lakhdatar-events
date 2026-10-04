import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { environment } from '../../environments/environment';

@Component({
  selector: 'lk-site-footer', standalone: true, imports: [RouterLink],
  template: `
    <footer class="ftr">
      <div class="container ftr-in">
        <div class="ftr-col">
          @if (organizerName) { <strong>{{ organizerName }}</strong><span>Tickets and events by {{ organizerName }} — powered by Neelastack</span> }
          @else { <strong>Neelastack Events</strong><span>Event ticketing platform by Neelastack</span> }
        </div>
        <nav class="ftr-links" aria-label="Footer">
          <a routerLink="/events">All events</a>
          <a routerLink="/recover">Find my ticket</a>
          <a [href]="neelastackUrl" target="_blank" rel="noopener noreferrer">Neelastack ↗</a>
        </nav>
      </div>
      <div class="container legal">© {{ year }} Neelastack. Event ticketing technology by Neelastack. Payments processed securely by our payment partners.</div>
    </footer>
  `,
  styles: [`
    .ftr{margin-top:clamp(56px,8vw,110px);border-top:1px solid var(--line);background:#0a090d;padding:36px 0 30px}
    .ftr-in{display:flex;justify-content:space-between;gap:24px;flex-wrap:wrap}
    .ftr-col{display:grid;gap:6px;max-width:460px}.ftr-col strong{font-family:var(--display);font-size:20px}.ftr-col span{color:var(--muted);font-size:14px;line-height:1.5}
    .ftr-links{display:flex;gap:20px;flex-wrap:wrap;align-items:flex-start}.ftr-links a{color:#cfc9d3;text-decoration:none;font-size:14px}.ftr-links a:hover{color:var(--gold-2)}
    .legal{margin-top:26px;color:#6f6875;font-size:12px}
  `]
})
export class SiteFooterComponent { @Input() organizerName?: string | null; year = new Date().getFullYear(); neelastackUrl = environment.neelastackPublicUrl; }
