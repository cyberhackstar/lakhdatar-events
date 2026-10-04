import { Component, HostListener, input, output } from '@angular/core';

/** In-page confirmation (replaces window.confirm so it matches the console and works on mobile). */
@Component({
  selector: 'lk-confirm-dialog',
  standalone: true,
  template: `
    @if (open()) {
      <div class="backdrop" (click)="cancelled.emit()"></div>
      <div class="dialog" role="alertdialog" aria-modal="true" [attr.aria-label]="title()">
        <h3>{{ title() }}</h3>
        <p>{{ message() }}</p>
        <div class="actions">
          <button type="button" class="btn" (click)="cancelled.emit()">Cancel</button>
          <button type="button" class="btn" [class.danger]="danger()" [class.primary]="!danger()" [disabled]="busy()" (click)="confirmed.emit()">{{ busy() ? 'Working…' : confirmLabel() }}</button>
        </div>
      </div>
    }
  `,
  styles: [`
    :host{color-scheme:light}
    .backdrop{position:fixed;inset:0;background:rgba(14,10,18,.5);z-index:150}
    .dialog{position:fixed;z-index:160;left:50%;top:50%;transform:translate(-50%,-50%);width:min(420px,calc(100vw - 32px));background:#fff;color:#1a151b;border-radius:18px;padding:22px;box-shadow:0 24px 60px rgba(14,10,18,.35)}
    h3{margin:0 0 8px;font-size:18px;letter-spacing:-.02em}
    p{margin:0 0 18px;color:#6f6573;font-size:14px;line-height:1.55}
    .actions{display:flex;gap:10px;justify-content:flex-end;flex-wrap:wrap}
    .btn{min-height:44px;padding:0 18px;border-radius:12px;border:1px solid #d8d0c7;background:#fff;color:#1a151b;font:inherit;font-weight:700;font-size:14px;cursor:pointer}
    .btn.primary{background:#17121a;border-color:#17121a;color:#fff}
    .btn.danger{background:#8f3e42;border-color:#8f3e42;color:#fff}
    .btn:disabled{opacity:.5;cursor:not-allowed}
  `]
})
export class ConfirmDialogComponent {
  readonly open = input(false);
  readonly title = input('Are you sure?');
  readonly message = input('');
  readonly confirmLabel = input('Confirm');
  readonly danger = input(true);
  readonly busy = input(false);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
  @HostListener('document:keydown.escape') onEsc(): void { if (this.open()) this.cancelled.emit(); }
}
