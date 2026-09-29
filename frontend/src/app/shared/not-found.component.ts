import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lk-not-found',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="not-found">
      <div class="mark">404</div>
      <div class="eyebrow">Page not found</div>
      <h1>This event page<br>doesn’t exist.</h1>
      <p>The link may be outdated or the event may have been removed.</p>
      <a routerLink="/">Back to all events <span>→</span></a>
    </main>
  `,
  styles: [`
    .not-found{min-height:100vh;display:grid;place-items:center;align-content:center;gap:13px;padding:30px;background:#09080c;color:#fff;text-align:center}
    .mark{font-size:11px;letter-spacing:.2em;color:#d5a84e;font-weight:900}
    .eyebrow{font-size:9px;text-transform:uppercase;letter-spacing:.18em;color:#756e79;font-weight:800}
    h1{font-size:clamp(42px,7vw,72px);line-height:.93;letter-spacing:-.06em;margin:5px 0}
    p{color:#817884;max-width:430px;font-size:12px;line-height:1.6}
    a{margin-top:10px;background:#fff;color:#17121a;border-radius:999px;padding:13px 18px;text-decoration:none;font-size:11px;font-weight:800}
    a span{margin-left:8px;font-size:16px}
  `]
})
export class NotFoundComponent { constructor(){document.title="Page not found · Neelastack Events";} }
