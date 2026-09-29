import { Component, Input } from '@angular/core';

/** Calm, customer-safe full-width state (empty / error / unavailable). Never shows technical detail. */
@Component({
  selector: 'lk-state', standalone: true,
  template: `
    <section class="state" role="status">
      <div class="mark" aria-hidden="true">{{ icon }}</div>
      <div class="eyebrow">{{ eyebrow }}</div>
      <h1 class="display">{{ title }}</h1>
      <p>{{ message }}</p>
      <div class="actions"><ng-content /></div>
    </section>
  `,
  styles: [`
    .state{max-width:560px;margin:clamp(48px,10vw,120px) auto;padding:0 20px;text-align:center;display:grid;justify-items:center;gap:14px}
    .mark{width:64px;height:64px;border-radius:50%;display:grid;place-items:center;font-size:26px;color:var(--gold-2);background:rgba(212,166,78,.1);border:1px solid rgba(212,166,78,.3)}
    h1{margin:6px 0 0;font-size:clamp(30px,5vw,44px)}p{margin:0;color:#b6afba;line-height:1.6}.actions{display:flex;gap:12px;flex-wrap:wrap;justify-content:center;margin-top:10px}
  `]
})
export class StatePanelComponent { @Input() icon = '✦'; @Input() eyebrow = ''; @Input({ required: true }) title = ''; @Input() message = ''; }
