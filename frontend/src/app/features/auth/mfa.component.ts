import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { MfaEnrollment } from '../../core/api/api.models';
import { SET_PASSWORD_STYLES } from './set-password.styles';

@Component({
  selector: 'lk-mfa',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="page"><div class="card">
      <div class="brand"><lk-brand-mark label="Events" [height]="48" /></div>
      <div class="eyebrow">Multi-factor authentication</div>
      @if (setup) {
        <h1>Protect your account.</h1>
        <p>Scan the QR code with an authenticator app, then enter the six-digit code to finish setup.</p>
        @if (enrollment(); as e) {
          <div class="qr-wrap"><img [src]="e.qrDataUri" alt="Authenticator setup QR code" width="260" height="260" /></div>
          <div class="secret"><span>Manual setup key</span><code>{{ e.secret }}</code></div>
        } @else if (busy()) { <div class="alert info">Preparing secure MFA setup…</div> }
      } @else {
        <h1>Verify your sign-in.</h1>
        <p>Enter the six-digit code from your authenticator app.</p>
      }
      @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label>Authenticator code
          <input type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" formControlName="code" [class.invalid]="bad()" placeholder="000000" />
          @if (bad()) { <span class="err">Enter the six-digit code.</span> }
        </label>
        <button class="go" type="submit" [disabled]="busy() || form.invalid || (setup && !enrollment())">{{ busy() ? 'Verifying…' : setup ? 'Enable MFA' : 'Verify and continue' }}</button>
      </form>
      <a class="back" routerLink="/login">← Back to sign in</a>
    </div></div>
  `,
  styles: [SET_PASSWORD_STYLES + `
    .qr-wrap{display:grid;place-items:center;background:#fff;border-radius:18px;padding:14px;margin:22px auto;width:max-content;max-width:100%}.qr-wrap img{display:block;border-radius:8px}.secret{display:grid;gap:8px;margin:14px 0 20px}.secret span{font-size:10px;text-transform:uppercase;letter-spacing:.1em;color:#8b8390;font-weight:800}.secret code{display:block;word-break:break-all;background:#100d14;border:1px solid rgba(255,255,255,.1);border-radius:12px;padding:13px;color:#fff;font-size:12px;letter-spacing:.08em}
  `]
})
export class MfaComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly enrollment = signal<MfaEnrollment | null>(null);
  setup = false;
  token = '';
  returnUrl: string | null = null;
  readonly form = this.fb.nonNullable.group({ code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]] });

  ngOnInit(): void {
    const parts = new URLSearchParams((this.route.snapshot.fragment || '').replace(/^#/, ''));
    this.token = parts.get('challenge') || '';
    this.setup = parts.get('setup') === '1';
    this.returnUrl = this.safeReturn(parts.get('returnUrl'));
    if (this.token) this.router.navigate([], { fragment: undefined, replaceUrl: true });
    if (!this.token) { this.error.set('This MFA challenge is missing or expired. Please sign in again.'); return; }
    if (this.setup) {
      this.busy.set(true);
      this.auth.mfaEnroll(this.token).subscribe({
        next: r => { this.busy.set(false); this.enrollment.set(r); },
        error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'MFA setup could not be started.'); }
      });
    }
  }

  bad(): boolean { return this.form.controls.code.touched && this.form.controls.code.invalid; }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || !this.token || (this.setup && !this.enrollment())) return;
    this.busy.set(true); this.error.set('');
    const code = this.form.controls.code.value;
    const req$ = this.setup ? this.auth.mfaConfirm(this.token, code) : this.auth.mfaVerify(this.token, code);
    req$.subscribe({
      next: r => {
        this.busy.set(false);
        this.auth.mustChangePassword().subscribe({
          next: must => { if (must) this.router.navigate(['/change-password'], { queryParams: { required: 1, ...(this.returnUrl ? { returnUrl: this.returnUrl } : {}) }, replaceUrl: true }); else this.finish(r.role); },
          error: () => this.finish(r.role)
        });
      },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'The MFA code is invalid or has expired.'); }
    });
  }

  private finish(role: string): void {
    const target = this.returnUrl || (role === 'STAFF' ? '/staff' : ['ADMIN','ORGANIZER','EVENT_MANAGER','FINANCE'].includes(role) ? '/admin' : '/');
    this.router.navigateByUrl(target, { replaceUrl: true });
  }

  private safeReturn(value: string | null): string | null {
    return value && value.startsWith('/') && !value.startsWith('//') && !value.includes('://') && !value.startsWith('/mfa') ? value : null;
  }
}
