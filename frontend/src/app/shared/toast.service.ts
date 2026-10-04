import { Injectable, signal } from '@angular/core';

export interface Toast { id: number; kind: 'success' | 'error' | 'info'; text: string; }

/** Tiny app-wide toast queue. Rendered by <lk-toasts> inside the admin shell. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  success(text: string): void { this.push('success', text, 4500); }
  info(text: string): void { this.push('info', text, 5000); }
  /** Errors stay a little longer so they can be read. */
  error(text: string): void { this.push('error', text, 8000); }

  dismiss(id: number): void { this.toasts.update(list => list.filter(t => t.id !== id)); }

  private push(kind: Toast['kind'], text: string, ms: number): void {
    const id = ++this.seq;
    this.toasts.update(list => [...list.slice(-3), { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ms);
  }
}
