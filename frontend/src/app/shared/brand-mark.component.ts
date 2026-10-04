import { AfterViewInit, Component, ElementRef, Input, ViewChild } from '@angular/core';

/**
 * Platform mark: the Neelastack logo (which already contains the word "Neelastack") followed by the product name as text.
 * If the logo file ever fails to load, the text wordmark is shown instead of a broken-image icon.
 */
@Component({
  selector: 'lk-brand-mark',
  standalone: true,
  template: `
    @if (!logoFailed) {
      <img #logo class="logo" src="/assets/neelastack-logo.png" alt="Neelastack" [style.height.px]="height" (error)="logoFailed = true" />
    } @else {
      <span class="fallback">Neelastack</span>
    }
    @if (label) { <span class="label">{{ label }}</span> }
  `,
  styles: [`
    :host{display:inline-flex;align-items:center;gap:.55em;line-height:1;color:inherit}
    .logo{display:block;width:auto;max-width:280px;max-height:48px;object-fit:contain}
    .fallback{font-weight:800;letter-spacing:.14em;text-transform:uppercase;font-size:.78em;color:var(--gold-2,#f0cf8c)}
    .label{font-weight:500;letter-spacing:.01em}
  `]
})
export class BrandMarkComponent implements AfterViewInit {
  /** Text shown after the logo. */
  @Input() label = 'Events';
  /** Rendered logo height in px; width follows the image's own proportions. */
  @Input() height = 30;
  logoFailed = false;
  @ViewChild('logo') private logo?: ElementRef<HTMLImageElement>;

  ngAfterViewInit(): void {
    // The image may already have failed before hydration attached the (error) listener.
    const img = this.logo?.nativeElement;
    if (img && img.complete && img.naturalWidth === 0) queueMicrotask(() => { this.logoFailed = true; });
  }
}
