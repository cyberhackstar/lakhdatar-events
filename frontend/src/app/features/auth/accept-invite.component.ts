import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { SET_PASSWORD_STYLES } from './set-password.styles';

/** Landing page for the emailed invite link: set a password once, then you are signed in. */
@Component({
  selector: 'lk-accept-invite',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="page"><div class="card">
      <div class="brand"><lk-brand-mark label="Events" [height]="48" /></div>
      <div class="eyebrow">Team invitation</div>
      <h1>Set your password</h1>
      @if (!token) {
        <div class="alert error" role="alert">This invite link is incomplete. Open the link from your invitation email again, or ask your organizer to resend it.</div>
        <a class="back" routerLink="/login">← Go to sign in</a>
      } @else {
        <p>Choose a password to activate your account. This link works once and expires 48 hours after it was sent.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>New password
            <input type="password" formControlName="password" autocomplete="new-password" maxlength="128" placeholder="At least 12 characters" [class.invalid]="bad('password')" />
            @if (bad('password')) { <span class="err">Use 12–128 characters.</span> }
          </label>
          <label>Confirm password
            <input type="password" formControlName="confirm" autocomplete="new-password" maxlength="128" [class.invalid]="bad('confirm') || mismatch()" />
            @if (mismatch()) { <span class="err">The passwords do not match.</span> }
          </label>
          @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
          <button class="go" type="submit" [disabled]="busy()">{{ busy() ? 'Activating…' : 'Activate account' }}</button>
        </form>
        <a class="back" routerLink="/login">Already set a password? Sign in</a>
      }
    </div></div>
  `,
  styles: [SET_PASSWORD_STYLES]
})
export class AcceptInviteComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  token = '';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirm: ['', [Validators.required]]
  });

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    // Keep the one-time token out of the address bar and browser history.
    if (this.token) this.router.navigate([], { queryParams: {}, replaceUrl: true });
  }

  bad(f: 'password' | 'confirm'): boolean { const c = this.form.controls[f]; return c.invalid && c.touched; }
  mismatch(): boolean { const v = this.form.getRawValue(); return this.form.controls.confirm.touched && v.confirm !== v.password; }

  submit(): void {
    this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    if (this.form.invalid || v.password !== v.confirm) return;
    this.busy.set(true); this.error.set('');
    this.auth.acceptInvite(this.token, v.password).subscribe({
      next: r => {
        this.busy.set(false);
        this.router.navigateByUrl(r.role === 'STAFF' ? '/staff' : ['ADMIN', 'ORGANIZER', 'EVENT_MANAGER', 'FINANCE'].includes(r.role) ? '/admin' : '/', { replaceUrl: true });
      },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'This invite link is invalid or has expired. Ask your organizer to resend it.'); }
    });
  }
}
