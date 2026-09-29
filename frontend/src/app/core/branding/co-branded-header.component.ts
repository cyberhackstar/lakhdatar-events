import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { BrandConfig } from './branding.model';

@Component({
  selector: 'lk-co-branded-header', standalone: true, imports: [CommonModule],
  template: `
    <header class="brand-header">
      <a *ngIf="brand.technologyPartnerEnabled && brand.technologyPartnerLogoUrl" class="partner-brand" [href]="brand.technologyPartnerUrl" target="_blank" rel="noopener noreferrer" aria-label="Open technology partner">
        <img [src]="brand.technologyPartnerLogoUrl" [alt]="brand.technologyPartnerName" />
        <span>Technology partner</span>
      </a>
      <div class="header-spacer"></div>
      <div class="organizer-brand" [attr.aria-label]="brand.organizerName">
        <img [src]="brand.eventLogoUrl || brand.organizerLogoUrl" [alt]="brand.organizerName" />
        <span>{{ brand.organizerName }}</span>
      </div>
    </header>
  `,
  styles: [`
    .brand-header{height:84px;display:flex;align-items:center;padding:0 clamp(18px,4vw,52px);gap:20px;position:relative;z-index:4;background:rgba(10,8,14,.72);backdrop-filter:blur(18px);border-bottom:1px solid rgba(255,255,255,.08);position:sticky;top:0}
    .header-spacer{flex:1}.partner-brand,.organizer-brand{display:flex;align-items:center;color:inherit;gap:11px}.partner-brand{opacity:.9;text-decoration:none}.partner-brand img{height:30px;width:auto}.organizer-brand img{height:46px;width:auto;max-width:220px;object-fit:contain}.partner-brand span{font-size:9px;text-transform:uppercase;letter-spacing:.16em;color:#a7a0ad}.organizer-brand span{display:none;color:#fff;font-size:12px;font-weight:700;letter-spacing:.08em}    @media(max-width:560px){.brand-header{height:70px;padding:0 14px;gap:10px}.partner-brand span{display:none}.partner-brand img{height:26px}.organizer-brand img{height:38px;max-width:170px}}
  `]
})
export class CoBrandedHeaderComponent { @Input({ required: true }) brand!: BrandConfig; }
