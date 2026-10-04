import { CommonModule } from '@angular/common';
import { AfterViewInit, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode';
import { ApiService } from '../../core/api/api.service';
import { ScanResultCode } from '../../core/api/api.models';

@Component({
  selector: 'lk-scanner',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="scanner" [class.accept]="result==='ACCEPTED'" [class.reject]="!!result && result!=='ACCEPTED'">
      <header>
        <a routerLink="/staff" class="back" aria-label="Back to staff console">←</a>
        <div class="event-context"><strong>{{eventName || 'Event scanner'}}</strong><span>Gate {{gate}}</span></div>
        <span class="online" [class.offline]="offline"><i></i>{{offline?'OFFLINE':'ONLINE'}}</span>
      </header>
      <main>
        <div class="offline-banner" *ngIf="offline" role="status">Network unavailable. No entry will be granted until the server can verify a scan.</div>

        <div class="camera-card" [class.hidden]="state!=='scanning'">
          <div class="scan-title"><div><strong>Scan ticket</strong><small>Hold the QR inside the frame</small></div><span>Server verified</span></div>
          <div id="qr-reader" aria-label="QR scanner camera"></div>
          <div class="verify-overlay" *ngIf="verifying" role="status" aria-live="polite"><span class="verify-spinner"></span><strong>Verifying ticket…</strong><small>Checking the secure ticket credential with the server.</small></div>
          <div class="scan-help"><span>Keep the camera 15–30 cm from the code.</span><span>Camera remains active between scans.</span></div>
          <button class="manual" type="button" (click)="openManual()">Enter ticket code manually</button>
        </div>

        <div class="state-card" *ngIf="state==='camera-error'">
          <div class="state-icon">!</div><h1>Camera unavailable</h1>
          <p>Allow camera access for this site, then retry. You can also use manual ticket entry.</p>
          <button type="button" (click)="startScanner()">Retry camera</button>
          <button class="secondary" type="button" (click)="openManual()">Manual entry</button>
        </div>

        <div class="state-card" *ngIf="state==='network-error'">
          <div class="state-icon">×</div><h1>Unable to verify</h1>
          <p>The validation server could not be reached. The ticket was <strong>not</strong> accepted.</p>
          <button type="button" (click)="resume()">Retry</button>
        </div>

        <div class="result-card" *ngIf="state==='result'" [class.good]="result==='ACCEPTED'">
          <div class="result-icon">{{result==='ACCEPTED'?'✓':'×'}}</div>
          <div class="result-label">{{result==='ACCEPTED'?'ENTRY ACCEPTED':'ENTRY DENIED'}}</div>
          <h1>{{headline}}</h1>
          <p>{{message}}</p>
          <div class="ticket-mini" *ngIf="ticketNumber"><span>{{ticketNumber}}</span><small>{{attendeeName || 'Guest'}}<ng-container *ngIf="ticketType"> · {{ticketType}}</ng-container></small><strong class="seat-count" *ngIf="orderTicketCount > 0">{{orderTicketCount}} {{orderTicketCount === 1 ? 'seat' : 'seats'}} booked · ticket {{ticketPosition}} of {{orderTicketCount}}</strong><b class="issuer-badge" *ngIf="ticketSource === 'COMPLIMENTARY_MANAGER'">COMPLIMENTARY · MANAGER ISSUED<span *ngIf="issuedByName"> · {{issuedByName}}</span></b></div>
          <button type="button" (click)="resume()">Scan next ticket</button>
        </div>
      </main>

      <footer><span>Gate {{gate}}</span><span>Server-authoritative validation</span></footer>

      <div class="manual-backdrop" *ngIf="manualOpen" (click)="closeManual()">
        <section class="manual-modal" role="dialog" aria-modal="true" aria-labelledby="manual-title" (click)="$event.stopPropagation()">
          <button class="modal-close" type="button" aria-label="Close" (click)="closeManual()">×</button>
          <div class="eyebrow">Manual validation</div><h2 id="manual-title">Enter ticket code</h2>
          <p>Use the exact ticket credential shown by the attendee. The server will perform the same validation as a QR scan.</p>
          <label for="manual-code">Ticket code<input id="manual-code" [(ngModel)]="manualToken" (keyup.enter)="submitManual()" autocomplete="off" autocapitalize="none" spellcheck="false" maxlength="512"/></label>
          <div class="manual-error" *ngIf="manualError">{{manualError}}</div>
          <button class="manual-submit" type="button" [disabled]="!manualToken.trim() || offline" (click)="submitManual()">Validate ticket <span>→</span></button>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .scanner{min-height:100vh;background:#08070b;color:#fff;display:flex;flex-direction:column;position:relative}.scanner.accept{background:linear-gradient(145deg,#123b22,#08070b 70%)}.scanner.reject{background:linear-gradient(145deg,#43151a,#08070b 70%)}.scanner header{height:72px;display:flex;align-items:center;gap:13px;padding:0 16px;border-bottom:1px solid rgba(255,255,255,.08);position:relative;z-index:3}.back{color:#fff;text-decoration:none;font-size:21px;padding:8px}.event-context{display:grid;gap:3px;flex:1;min-width:0}.event-context strong{font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.event-context span{font-size:9px;color:#8b8290}.online{display:flex;align-items:center;gap:7px;font-size:8px!important;letter-spacing:.12em!important;color:#83b88f!important}.online i{display:block;width:6px;height:6px;border-radius:50%;background:currentColor;box-shadow:0 0 0 5px rgba(131,184,143,.08)}.online.offline{color:#d69999!important}.online.offline i{box-shadow:0 0 0 5px rgba(214,153,153,.08)}.scanner main{flex:1;display:grid;place-items:center;padding:24px;position:relative}.offline-banner{position:absolute;top:16px;left:50%;transform:translateX(-50%);width:min(620px,calc(100% - 32px));padding:10px 12px;background:#3a2a16;border:1px solid rgba(232,194,108,.2);border-radius:12px;color:#d9c081;font-size:10px;text-align:center;z-index:2}.camera-card,.state-card,.result-card{width:min(460px,100%);text-align:center;grid-area:1/1}.camera-card.hidden{visibility:hidden;pointer-events:none}.camera-card{position:relative;background:rgba(10,8,13,.68);border:1px solid rgba(255,255,255,.08);padding:18px;border-radius:24px;box-shadow:0 30px 90px rgba(0,0,0,.25);backdrop-filter:blur(14px)}.scan-title{display:flex;justify-content:space-between;gap:14px;align-items:end;padding:3px 5px 16px;text-align:left}.scan-title div{display:grid;gap:3px}.scan-title strong{font-size:11px}.scan-title small{font-size:9px;color:#807783}.scan-title>span{font-size:8px;text-transform:uppercase;letter-spacing:.12em;color:#8bbf97}.camera-card #qr-reader{overflow:hidden;border-radius:18px;background:#000;min-height:290px}.camera-card #qr-reader video{border-radius:18px!important;object-fit:cover}.verify-overlay{position:absolute;inset:64px 18px 58px;display:grid;place-content:center;gap:8px;background:rgba(8,7,11,.76);border-radius:18px;backdrop-filter:blur(5px);z-index:2}.verify-overlay strong{font-size:14px}.verify-overlay small{color:#a69da8;font-size:9px}.verify-spinner{width:30px;height:30px;border:2px solid rgba(255,255,255,.15);border-top-color:#fff;border-radius:50%;margin:0 auto;animation:scanner-spin .75s linear infinite}@keyframes scanner-spin{to{transform:rotate(360deg)}}.scan-help{display:flex;justify-content:space-between;gap:12px;margin:12px 2px 0;color:#77707c;font-size:9px;line-height:1.4;text-align:left}.manual{margin-top:15px;background:transparent;color:#aaa0ac;border:1px solid rgba(255,255,255,.1);border-radius:999px;padding:11px 16px;font-size:10px;cursor:pointer;width:100%}.result-card{background:rgba(10,8,13,.72);padding:40px 28px;border-radius:28px;border:1px solid rgba(255,255,255,.08);box-shadow:0 35px 100px rgba(0,0,0,.25);backdrop-filter:blur(16px)}.result-card.good{border-color:rgba(130,211,150,.18)}.result-icon{width:78px;height:78px;border-radius:50%;margin:0 auto 24px;display:grid;place-items:center;background:rgba(255,255,255,.08);font-size:35px}.result-card.good .result-icon{background:#204b30;color:#a7e0b8}.result-label{font-size:9px;text-transform:uppercase;letter-spacing:.16em;color:#aaa1ac}.result-card h1{font-size:clamp(36px,8vw,44px);letter-spacing:-.05em;margin:9px 0}.result-card p{color:#b0a7b1;font-size:12px;line-height:1.55;margin:0 auto;max-width:370px}.ticket-mini{margin:20px auto;padding:13px 15px;border:1px solid rgba(255,255,255,.09);border-radius:12px;display:grid;gap:3px;max-width:300px}.ticket-mini span{font-size:12px;font-weight:800}.ticket-mini small{font-size:9px;color:#8b808e}.seat-count{font-size:10px;color:#e3c16e;margin-top:5px}.issuer-badge{display:block;margin-top:6px;font-size:8px;letter-spacing:.08em;text-transform:uppercase;color:#e3c16e}.state-card h1{font-size:38px;letter-spacing:-.05em;margin:12px 0 7px}.state-card p{color:#aaa1aa;font-size:12px;max-width:360px;margin:0 auto 22px;line-height:1.55}.state-icon{width:68px;height:68px;border:1px solid rgba(255,255,255,.1);border-radius:50%;display:grid;place-items:center;margin:0 auto;color:#fff;font-size:26px}.state-card button,.result-card button{border:0;border-radius:999px;background:#fff;color:#161118;padding:14px 20px;font-weight:800;cursor:pointer;margin:6px}.state-card .secondary{background:transparent;color:#fff;border:1px solid rgba(255,255,255,.12)}.scanner footer{display:flex;justify-content:space-between;padding:14px 18px;color:rgba(255,255,255,.45);font-size:8px;text-transform:uppercase;letter-spacing:.1em}.manual-backdrop{position:fixed;inset:0;background:rgba(0,0,0,.7);backdrop-filter:blur(10px);display:grid;place-items:center;padding:18px;z-index:10}.manual-modal{width:min(440px,100%);background:#17131c;border:1px solid rgba(255,255,255,.1);border-radius:24px;padding:28px;color:#fff;position:relative;box-shadow:0 40px 120px rgba(0,0,0,.55)}.modal-close{position:absolute;top:12px;right:12px;width:34px;height:34px;border-radius:50%;border:1px solid rgba(255,255,255,.08);background:transparent;color:#aaa1ad;font-size:20px;cursor:pointer}.manual-modal .eyebrow{color:#8f8494;font-size:9px;text-transform:uppercase;letter-spacing:.14em}.manual-modal h2{font-size:36px;letter-spacing:-.05em;margin:9px 0}.manual-modal p{color:#8e8692;font-size:11px;line-height:1.55}.manual-modal label{display:grid;gap:8px;margin-top:20px;color:#aaa1ac;font-size:9px;text-transform:uppercase;letter-spacing:.1em;font-weight:800}.manual-modal input{height:52px;background:#100d14;border:1px solid rgba(255,255,255,.1);border-radius:12px;color:#fff;padding:0 13px;outline:none}.manual-modal input:focus{border-color:#d3a549}.manual-error{color:#d98e8e;font-size:10px;margin-top:9px}.manual-submit{width:100%;height:52px;border:0;border-radius:12px;background:#fff;color:#17121a;font-weight:800;cursor:pointer;margin-top:15px}.manual-submit:disabled{opacity:.4}.manual-submit span{float:right;font-size:17px}@media(max-width:560px){.scan-help{display:none}.camera-card{padding:12px}.scanner main{padding:16px}.result-card{padding:32px 22px}.manual-modal{padding:24px}.scanner footer{font-size:7px}}
  `]
})
export class ScannerComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private destroyed = false;
  private busy = false;
  private lastToken = '';
  private lastAt = 0;
  private started = false;
  private starting = false;
  private visibilityPaused = false;

  eventId = '';
  gate = 'Main Gate';
  eventName = '';
  state: 'scanning' | 'result' | 'camera-error' | 'network-error' = 'scanning';
  result: ScanResultCode | null = null;
  message = '';
  ticketNumber = '';
  attendeeName = '';
  ticketType = '';
  ticketSource = '';
  issuedByName = '';
  ticketPosition = 0;
  orderTicketCount = 0;
  offline = false;
  manualOpen = false;
  manualToken = '';
  manualError = '';
  verifying = false;
  scanner?: Html5Qrcode;

  ngOnInit(): void {
    this.eventId = this.route.snapshot.paramMap.get('eventId') || '';
    this.gate = this.route.snapshot.queryParamMap.get('gate') || 'Main Gate';
    this.eventName = this.route.snapshot.queryParamMap.get('eventName') || '';
    window.addEventListener('offline', this.offlineHandler);
    window.addEventListener('online', this.onlineHandler);
    document.addEventListener('visibilitychange', this.visibilityHandler);
    this.offline = !navigator.onLine;
  }

  ngAfterViewInit(): void { if (this.offline) this.state = 'network-error'; else void this.startScanner(); }

  ngOnDestroy(): void {
    this.destroyed = true;
    void this.stopScanner();
    window.removeEventListener('offline', this.offlineHandler);
    window.removeEventListener('online', this.onlineHandler);
    document.removeEventListener('visibilitychange', this.visibilityHandler);
  }

  private offlineHandler = (): void => { this.offline = true; void this.stopScanner(); this.state = 'network-error'; };
  private onlineHandler = (): void => {
    const wasOffline = this.offline;
    this.offline = false;
    if (wasOffline && !this.destroyed && this.state === 'network-error') void this.startScanner();
  };
  private visibilityHandler = (): void => {
    if (document.hidden) { this.visibilityPaused = true; void this.stopScanner(); }
    else if (this.visibilityPaused && !this.destroyed && this.state === 'scanning' && !this.offline) { this.visibilityPaused = false; void this.startScanner(); }
  };

  private async stopScanner(): Promise<void> {
    const current = this.scanner;
    this.scanner = undefined;
    this.started = false;
    this.starting = false;
    if (current) {
      try { await current.stop(); } catch { /* scanner may already be stopped */ }
      try { current.clear(); } catch { /* element may be gone */ }
    }
  }

  async startScanner(): Promise<void> {
    if (this.destroyed || this.offline) { if (this.offline) this.state = 'network-error'; return; }
    if (this.started && this.scanner) {
      this.state = 'scanning';
      this.result = null;
      this.message = '';
      this.ticketNumber = '';
      this.attendeeName = '';
      this.ticketType = '';
      this.ticketSource = '';
      this.issuedByName = '';
      this.ticketPosition = 0;
      this.orderTicketCount = 0;
      this.busy = false;
      this.verifying = false;
      return;
    }
    if (this.starting) return;

    this.state = 'scanning';
    this.result = null;
    this.message = '';
    this.ticketNumber = '';
    this.attendeeName = '';
    this.ticketType = '';
    this.ticketSource = '';
    this.issuedByName = '';
    this.busy = false;
    this.verifying = false;
    setTimeout(() => { if (!this.destroyed && this.state === 'scanning') this.launchScanner(); }, 0);
  }

  private launchScanner(): void {
    if (this.destroyed || this.state !== 'scanning' || this.started || this.starting || this.offline) return;
    this.starting = true;
    try {
      const scanner = new Html5Qrcode('qr-reader', { formatsToSupport: [Html5QrcodeSupportedFormats.QR_CODE], verbose: false });
      this.scanner = scanner;
      scanner.start(
        { facingMode: 'environment' },
        { fps: 12, qrbox: { width: 280, height: 280 }, aspectRatio: 1 },
        token => this.onScan(token),
        () => undefined
      ).then(() => {
        if (this.destroyed || this.offline || this.scanner !== scanner) return;
        this.started = true;
      }).catch(() => {
        if (this.scanner === scanner) {
          this.scanner = undefined;
          try { scanner.clear(); } catch { /* best effort */ }
        }
        this.started = false;
        this.state = 'camera-error';
      }).finally(() => { this.starting = false; });
    } catch {
      this.starting = false;
      this.state = 'camera-error';
    }
  }

  onScan(token: string): void {
    const normalized = token.trim();
    const now = Date.now();
    if (this.state !== 'scanning' || this.busy || !this.eventId || this.offline || !normalized || normalized.length > 512) return;
    if (normalized === this.lastToken && now - this.lastAt < 1800) return;
    this.busy = true;
    this.verifying = true;
    this.lastToken = normalized;
    this.lastAt = now;
    this.api.scan(this.eventId, this.gate, normalized).subscribe({
      next: response => {
        // Keep the live camera session. The result view hides the camera card, and resume()
        // brings it back without paying the browser getUserMedia/start-up cost again.
        this.result = response.result;
        this.message = response.message;
        this.ticketNumber = response.ticketNumber || '';
        this.attendeeName = response.attendeeName || '';
        this.ticketType = response.ticketType || '';
        this.ticketSource = response.ticketSource || '';
        this.issuedByName = response.issuedByName || '';
        this.ticketPosition = Number(response.ticketPosition || 0);
        this.orderTicketCount = Number(response.orderTicketCount || 0);
        this.verifying = false;
        this.busy = false;
        this.state = 'result';
        navigator.vibrate?.(response.result === 'ACCEPTED' ? 120 : [80, 60, 80]);
      },
      error: error => {
        this.verifying = false;
        this.busy = false;
        if (error?.status === 401) {
          void this.stopScanner();
          this.router.navigate(['/login'], { queryParams: { returnUrl: `/staff/events/${encodeURIComponent(this.eventId)}/scanner` }, replaceUrl: true });
          return;
        }
        if (error?.status === 403) { this.result = 'STAFF_NOT_ASSIGNED'; this.message = error?.error?.message || 'This staff account is not assigned to this event or gate.'; this.state = 'result'; return; }
        if (error?.status === 429) { this.result = 'RATE_LIMITED'; this.message = error?.error?.message || 'Too many scan attempts. Please wait a moment.'; this.state = 'result'; return; }
        if (!error?.status) { void this.stopScanner(); this.state = 'network-error'; this.message = 'The server could not be reached. No entry was granted.'; return; }
        this.result = (error?.error?.code as ScanResultCode) || 'INVALID';
        this.message = error?.error?.message || 'The server rejected this scan.';
        this.state = 'result';
      }
    });
  }

  resume(): void {
    this.manualOpen = false;
    this.manualToken = '';
    this.manualError = '';
    this.busy = false;
    this.verifying = false;
    if (this.offline) { this.state = 'network-error'; return; }
    void this.startScanner();
  }

  openManual(): void { this.manualError = ''; this.manualOpen = true; void this.stopScanner(); }
  closeManual(resumeScanner = true): void {
    this.manualOpen = false; this.manualToken = ''; this.manualError = '';
    if (resumeScanner && this.state === 'scanning' && !this.offline && !this.destroyed) void this.startScanner();
  }
  submitManual(): void {
    const token = this.manualToken.trim();
    if (!token || token.length > 512) { this.manualError = 'Enter a valid ticket code.'; return; }
    if (this.offline) { this.manualError = 'Reconnect to the server before validating a ticket.'; return; }
    this.closeManual(false);
    this.onScan(token);
  }

  get headline(): string {
    switch (this.result) {
      case 'ACCEPTED': return 'Entry cleared';
      case 'ALREADY_USED': return 'Ticket already used';
      case 'CANCELLED': return 'Ticket cancelled';
      case 'REFUNDED': return 'Ticket refunded';
      case 'WRONG_EVENT': return 'Wrong event';
      case 'STAFF_NOT_ASSIGNED': return 'Gate not assigned';
      case 'EVENT_CLOSED': return 'Event closed';
      case 'RATE_LIMITED': return 'Too many scans';
      default: return 'Invalid ticket';
    }
  }
}
