import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { SET_PASSWORD_STYLES } from './set-password.styles';

@Component({
  selector: 'lk-forgot-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="page"><div class="card">
      <div class="brand"><lk-brand-mark label="Events" [height]="48" /></div>
      <div class="eyebrow">Account recovery</div>
      <h1>Reset your password.</h1>
      @if (!sent()) {
        <p>Enter your account email. If an account exists, you will receive a one-time reset link.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>Email
            <input type="email" formControlName="email" autocomplete="username" maxlength="255" [class.invalid]="bad()" />
            @if (bad()) { <span class="err">Enter a valid email address.</span> }
          </label>
          @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
          <button class="go" type="submit" [disabled]="busy()">{{ busy() ? 'Sending…' : 'Send reset link' }}</button>
        </form>
      } @else {
        <div class="alert info" role="status">Check your email for a reset link. For security, this page does not reveal whether the account exists.</div>
      }
      <a class="back" routerLink="/login">← Back to sign in</a>
    </div></div>
  `,
  styles: [SET_PASSWORD_STYLES]
})
export class ForgotPasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  readonly busy = signal(false);
  readonly sent = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]] });
  bad(): boolean { return this.form.controls.email.touched && this.form.controls.email.invalid; }
  submit(): void {
    this.form.markAllAsTouched(); if (this.form.invalid) return;
    this.busy.set(true); this.error.set('');
    this.auth.requestPasswordReset(this.form.controls.email.value.trim().toLowerCase()).subscribe({
      next: () => { this.busy.set(false); this.sent.set(true); },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'The reset request could not be completed. Please try again later.'); }
    });
  }
}
