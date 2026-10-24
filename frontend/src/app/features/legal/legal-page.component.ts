import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { SiteFooterComponent } from '../../shared/site-footer.component';
import { LEGAL } from './legal.config';

type Section = { h: string; p: string[] };
type Doc = { title: string; intro: string; sections: Section[] };

const L = LEGAL;

export const LEGAL_DOCS: Record<'privacy' | 'terms' | 'refund', Doc> = {
  privacy: {
    title: 'Privacy Policy',
    intro: `${L.platform} is operated by ${L.operator}. This policy explains what personal data we collect when you buy tickets or use the platform, why we collect it, and the choices you have.`,
    sections: [
      { h: 'Data we collect', p: [
        'Order details: your name, email address and phone number, and the tickets you buy.',
        'Account details for organizers and staff: email, hashed password, role, and multi-factor authentication data (stored encrypted).',
        'Technical data: IP address, browser type and security/audit logs needed to keep the service safe.',
        'We do not receive or store your card, UPI or bank details. Payments are handled by our payment partners (Cashfree or Razorpay), who process that data under their own policies.' ] },
      { h: 'How we use it', p: [
        'To process your order, issue tickets and QR codes, and send your confirmation and ticket links by email.',
        'To prevent fraud and abuse, enforce rate limits, and investigate payment or refund issues.',
        'To let the organizer of an event you booked manage attendance and contact attendees about that event.',
        'To comply with legal, tax and accounting obligations.' ] },
      { h: 'Sharing', p: [
        'We share order data with the organizer of the event you booked, with our payment partners, and with service providers who help us run the platform (hosting, email delivery, media storage).',
        'We do not sell your personal data. We may disclose data when required by law or to protect rights and safety.' ] },
      { h: 'Cookies', p: [
        'We use a secure, HTTP-only cookie to keep organizers and staff signed in. We do not use advertising cookies.' ] },
      { h: 'Retention and security', p: [
        'We keep order and payment records for as long as needed for the purposes above and for legal, tax and audit requirements, then delete or anonymize them.',
        'Data is encrypted in transit; secrets and MFA data are encrypted at rest; access is restricted to authorized operators.' ] },
      { h: 'Your rights', p: [
        `You may ask to access, correct or delete your personal data, or withdraw consent, by writing to ${L.supportEmail}. We will respond within a reasonable time and as required by applicable law.` ] },
      { h: 'Grievances', p: [
        `${L.grievanceOfficer} — ${L.grievanceEmail}. Address: ${L.address}.` ] },
      { h: 'Changes', p: [ 'We may update this policy; the date above shows the latest version.' ] }
    ]
  },
  terms: {
    title: 'Terms of Service',
    intro: `By using ${L.platform} to browse events or buy tickets you agree to these terms.`,
    sections: [
      { h: 'Our role', p: [
        `${L.platform} is a ticketing platform. Events are created and run by independent organizers, who are responsible for the event, its content, schedule, venue and entry rules. ${L.operator} is not the organizer unless stated on the event page.` ] },
      { h: 'Buying tickets', p: [
        'Prices are shown in Indian rupees. Tickets are held for a limited time while you pay; if payment is not completed in that time the hold is released.',
        'A ticket is issued only after your payment is confirmed by our payment partner. Keep your ticket link and QR code private — anyone who has them can use the ticket.',
        'Each QR code is valid for one entry. Duplicate or tampered tickets will be refused.' ] },
      { h: 'Acceptable use', p: [
        'Do not attempt to bypass limits, scrape the service, resell tickets in breach of the organizer\'s rules, or interfere with the platform\'s security.' ] },
      { h: 'Cancellations and refunds', p: [ 'Refunds are governed by the Refund & Cancellation Policy.' ] },
      { h: 'Liability', p: [
        'To the extent permitted by law, we are not liable for the organizer\'s acts or omissions, or for indirect losses. Nothing in these terms limits liability that cannot be limited under applicable law.' ] },
      { h: 'Governing law', p: [ 'These terms are governed by the laws of India. Courts at Jaipur, Rajasthan have jurisdiction, subject to applicable consumer protection law.' ] },
      { h: 'Contact', p: [ `${L.supportEmail}, ${L.address}.` ] }
    ]
  },
  refund: {
    title: 'Refund & Cancellation Policy',
    intro: 'This policy explains when you can get your money back for tickets bought on this platform.',
    sections: [
      { h: 'If an event is cancelled', p: [
        'If the organizer cancels an event, tickets for it are refunded in full to the original payment method automatically. You do not need to apply.' ] },
      { h: 'If you can no longer attend', p: [
        'Unless the event page states otherwise, tickets are non-refundable once issued. Some organizers allow cancellations or transfers; any such terms appear on the event page and apply to that event.' ] },
      { h: 'If you were charged but received no ticket', p: [
        `Use "Find my ticket" first. If the payment was captured but no ticket was issued, the amount is refunded to you, or the ticket is issued, once the payment is reconciled. Write to ${L.supportEmail} with your order details if it is not resolved.` ] },
      { h: 'Timing', p: [
        `Approved refunds are initiated promptly and typically reach your account within ${L.refundProcessingDays}, depending on your bank or payment method.` ] },
      { h: 'Contact', p: [ `${L.supportEmail}` ] }
    ]
  }
};

@Component({
  selector: 'lk-legal-page', standalone: true, imports: [RouterLink, SiteFooterComponent],
  template: `
    <main class="legal-wrap">
      <a routerLink="/" class="back">← Neelastack Events</a>
      <h1>{{ doc.title }}</h1>
      <p class="upd">Last updated: {{ updated }}</p>
      <p class="intro">{{ doc.intro }}</p>
      @for (s of doc.sections; track s.h) {
        <section>
          <h2>{{ s.h }}</h2>
          @for (para of s.p; track para) { <p>{{ para }}</p> }
        </section>
      }
      <nav class="others" aria-label="Legal">
        <a routerLink="/privacy">Privacy</a><a routerLink="/terms">Terms</a><a routerLink="/refund-policy">Refunds</a>
      </nav>
    </main>
    <lk-site-footer />
  `,
  styles: [`
    .legal-wrap{max-width:760px;margin:0 auto;padding:48px 20px 0;color:#e9e5ec;line-height:1.7}
    .back{color:#cfc9d3;text-decoration:none;font-size:14px}
    h1{font-size:clamp(30px,5vw,44px);margin:18px 0 4px;letter-spacing:-.03em}
    h2{font-size:18px;margin:28px 0 6px}
    p{margin:6px 0;color:#c9c3cd;font-size:15px}
    .upd{color:#8b8490;font-size:13px}.intro{font-size:16px}
    .others{display:flex;gap:18px;margin-top:36px}.others a{color:#d5a84e;font-size:14px}
  `]
})
export class LegalPageComponent {
  doc: Doc; updated = LEGAL.lastUpdated;
  constructor() {
    const kind = inject(ActivatedRoute).snapshot.data['legal'] as keyof typeof LEGAL_DOCS;
    this.doc = LEGAL_DOCS[kind];
    inject(Title).setTitle(`${this.doc.title} · ${LEGAL.platform}`);
    inject(Meta).updateTag({ name: 'description', content: this.doc.intro });
  }
}
