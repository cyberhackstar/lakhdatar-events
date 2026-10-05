import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { SET_PASSWORD_STYLES } from './set-password.styles';

@Component({
  selector: 'lk-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="page"><div class="card">
      <div class="brand"><lk-brand-mark label="Events" [height]="48" /></div>
      <div class="eyebrow">Account recovery</div>
      <h1>Choose a new password.</h1>
      @if (done()) {
        <div class="alert info" role="status">Your password has been changed. Sign in with your new password.</div>
        <a class="back" routerLink="/login">Go to sign in →</a>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>New password
            <input type="password" formControlName="next" autocomplete="new-password" maxlength="128" placeholder="At least 12 characters" [class.invalid]="bad('next')" />
            @if (bad('next')) { <span class="err">Use 12–128 characters.</span> }
          </label>
          <label>Confirm password
            <input type="password" formControlName="confirm" autocomplete="new-password" maxlength="128" [class.invalid]="mismatch()" />
            @if (mismatch()) { <span class="err">The passwords do not match.</span> }
          </label>
          @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
          <button class="go" type="submit" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Set new password' }}</button>
        </form>
      }
    </div></div>
  `,
  styles: [SET_PASSWORD_STYLES]
})
export class ResetPasswordComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly busy = signal(false);
  readonly done = signal(false);
  readonly error = signal('');
  token = '';
  readonly form = this.fb.nonNullable.group({ next: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]], confirm: ['', [Validators.required]] });
  ngOnInit(): void {
    const parts = new URLSearchParams((this.route.snapshot.fragment || '').replace(/^#/, ''));
    this.token = parts.get('token') || '';
    if (this.token) {
      try { this.token = decodeURIComponent(this.token); } catch { this.token = ''; }
    }
    if (!this.token) this.error.set('This reset link is incomplete or expired.');
    else this.router.navigate([], { fragment: undefined, replaceUrl: true });
  }
  bad(name: 'next' | 'confirm'): boolean { const c = this.form.controls[name]; return c.touched && c.invalid; }
  mismatch(): boolean { const v=this.form.getRawValue(); return this.form.controls.confirm.touched && v.confirm !== v.next; }
  submit(): void {
    this.form.markAllAsTouched(); const v=this.form.getRawValue(); if(this.form.invalid || v.next!==v.confirm || !this.token) return;
    this.busy.set(true); this.error.set('');
    this.auth.completePasswordReset(this.token, v.next).subscribe({
      next: () => { this.busy.set(false); this.done.set(true); },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'This reset link is invalid or has expired.'); }
    });
  }
}
