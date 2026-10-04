import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'lk-toasts',
  standalone: true,
  template: `
    <div class="stack" aria-live="polite" aria-atomic="false">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast" [class]="t.kind" [attr.role]="t.kind === 'error' ? 'alert' : 'status'">
          <span>{{ t.text }}</span>
          <button type="button" aria-label="Dismiss" (click)="toast.dismiss(t.id)">×</button>
        </div>
      }
    </div>
  `,
  styles: [`
    .stack{position:fixed;z-index:200;right:16px;bottom:16px;left:16px;display:grid;gap:8px;justify-items:end;pointer-events:none;padding-bottom:env(safe-area-inset-bottom,0px)}
    .toast{pointer-events:auto;display:flex;align-items:flex-start;gap:12px;max-width:min(440px,100%);padding:12px 14px;border-radius:12px;font-size:13px;line-height:1.45;box-shadow:0 10px 30px rgba(20,12,24,.22);background:#17121a;color:#fff;border:1px solid #17121a}
    .toast.success{background:#eaf6ee;color:#1f5a35;border-color:#b8dcc3}
    .toast.error{background:#fdeeee;color:#8c2f2f;border-color:#efb9b9}
    .toast.info{background:#fff7e0;color:#6d5311;border-color:#ecd59a}
    .toast span{flex:1;min-width:0;overflow-wrap:anywhere}
    .toast button{background:transparent;border:0;color:inherit;font-size:20px;line-height:1;cursor:pointer;min-width:28px;min-height:28px;padding:0}
    @media(min-width:700px){.stack{left:auto}}
  `]
})
export class ToastsComponent { readonly toast = inject(ToastService); }
