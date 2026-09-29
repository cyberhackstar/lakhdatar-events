import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { environment } from '../../../environments/environment';
import { toAbsoluteUrl } from '../format';

export interface SeoConfig {
  title: string;
  description: string;
  path: string;                 // canonical path, e.g. /events/my-event
  image?: string | null;
  type?: 'website' | 'article' | 'event';
  noindex?: boolean;
  jsonLd?: Record<string, unknown> | null;
}

@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly doc = inject(DOCUMENT);
  private readonly site = environment.siteUrl.replace(/\/$/, '');
  readonly defaultImage = this.site + '/assets/og-default.png';

  set(c: SeoConfig): void {
    const url = this.site + (c.path.startsWith('/') ? c.path : '/' + c.path);
    const description = c.description.replace(/\s+/g, ' ').trim().slice(0, 300);
    const image = toAbsoluteUrl(this.site, c.image) || this.defaultImage;
    this.title.setTitle(c.title);
    this.tag('name', 'description', description);
    this.tag('name', 'robots', c.noindex ? 'noindex,nofollow' : 'index,follow,max-image-preview:large');
    this.tag('property', 'og:site_name', environment.platformName);
    this.tag('property', 'og:type', c.type === 'event' ? 'website' : (c.type || 'website'));
    this.tag('property', 'og:title', c.title);
    this.tag('property', 'og:description', description);
    this.tag('property', 'og:url', url);
    this.tag('property', 'og:image', image);
    this.tag('property', 'og:locale', 'en_IN');
    this.tag('name', 'twitter:card', 'summary_large_image');
    this.tag('name', 'twitter:title', c.title);
    this.tag('name', 'twitter:description', description);
    this.tag('name', 'twitter:image', image);
    this.canonical(url);
    this.jsonLd(c.jsonLd || null);
  }

  private tag(attr: 'name' | 'property', key: string, content: string): void {
    this.meta.updateTag({ [attr]: key, content }, `${attr}='${key}'`);
  }

  private canonical(url: string): void {
    let link = this.doc.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) { link = this.doc.createElement('link'); link.setAttribute('rel', 'canonical'); this.doc.head.appendChild(link); }
    link.setAttribute('href', url);
  }

  private jsonLd(data: Record<string, unknown> | null): void {
    const id = 'lk-jsonld';
    let el = this.doc.getElementById(id);
    if (!data) { el?.remove(); return; }
    if (!el) { el = this.doc.createElement('script'); el.setAttribute('type', 'application/ld+json'); el.setAttribute('id', id); this.doc.head.appendChild(el); }
    // Escape "<" so admin-authored text can never terminate the script element.
    el.textContent = JSON.stringify(data).replace(/</g, '\\u003c');
  }
}
