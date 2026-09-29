import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { BrandConfig } from './branding.model';

@Component({
  selector: 'lk-neelastack-promo', standalone: true, imports: [CommonModule],
  template: `
    <section class="promo" *ngIf="brand.promoEnabled && brand.technologyPartnerEnabled">
      <div class="promo-glow"></div>
      <div class="promo-inner">
        <div class="promo-logo"><img [src]="brand.technologyPartnerLogoUrl" [alt]="brand.technologyPartnerName" /></div>
        <div>
          <div class="eyebrow">Technology partner</div>
          <h2>{{ brand.promoTitle || ('Built with ' + brand.technologyPartnerName) }}</h2>
          <p>{{ brand.promoDescription || 'Digital products, business portals and premium web experiences for growing companies.' }}</p>
        </div>
        <a class="promo-cta" [href]="brand.promoCtaUrl || brand.technologyPartnerUrl" target="_blank" rel="noopener noreferrer">
          {{ brand.promoCtaText || 'Explore Neelastack' }} <span>↗</span>
        </a>
      </div>
    </section>
  `,
  styles: [`
    .promo{position:relative;overflow:hidden;margin:34px auto 0;max-width:1100px;border:1px solid rgba(255,255,255,.1);border-radius:28px;background:linear-gradient(135deg,rgba(36,24,51,.95),rgba(13,15,25,.96));box-shadow:0 25px 80px rgba(0,0,0,.3)}.promo-inner{position:relative;display:grid;grid-template-columns:auto 1fr auto;gap:24px;align-items:center;padding:28px}.promo-logo{width:56px;height:56px;border-radius:17px;background:rgba(255,255,255,.06);display:grid;place-items:center;border:1px solid rgba(255,255,255,.1)}.promo-logo img{width:38px;height:38px;object-fit:contain}.eyebrow{text-transform:uppercase;letter-spacing:.14em;font-size:10px;color:#a99fb2;margin-bottom:6px}.promo h2{font-size:24px;line-height:1.1;margin:0 0 6px;font-weight:700}.promo p{margin:0;color:#a59fac;max-width:650px;line-height:1.55;font-size:14px}.promo-cta{white-space:nowrap;text-decoration:none;border-radius:999px;padding:13px 18px;background:#fff;color:#141017;font-size:13px;font-weight:700}.promo-cta span{margin-left:7px}.promo-glow{position:absolute;inset:-80px auto auto 55%;width:260px;height:260px;border-radius:50%;background:rgba(124,92,255,.18);filter:blur(70px)}
    @media(max-width:760px){.promo-inner{grid-template-columns:1fr;gap:16px}.promo-cta{width:max-content}.promo{margin:24px 16px 0;border-radius:22px}}
  `]
})
export class NeelastackPromoComponent { @Input({ required: true }) brand!: BrandConfig; }
