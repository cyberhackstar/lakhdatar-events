import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';
import { SET_PASSWORD_STYLES } from './set-password.styles';

/** Forced on first sign-in for accounts created with an initial password; also reachable any time at /change-password. */
@Component({
  selector: 'lk-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, BrandMarkComponent],
  template: `
    <div class="page"><div class="card">
      <div class="brand"><lk-brand-mark label="Events" [height]="34" /></div>
      <div class="eyebrow">Account security</div>
      <h1>Choose a new password</h1>
      @if (required) { <div class="alert info" role="status">Your account was created with an initial password. Choose your own to continue.</div> }
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <label>Current password
          <input type="password" formControlName="current" autocomplete="current-password" maxlength="128" [class.invalid]="bad('current')" />
          @if (bad('current')) { <span class="err">Enter your current password.</span> }
        </label>
        <label>New password
          <input type="password" formControlName="next" autocomplete="new-password" maxlength="128" placeholder="At least 12 characters" [class.invalid]="bad('next')" />
          @if (bad('next')) { <span class="err">Use 12–128 characters.</span> }
        </label>
        <label>Confirm new password
          <input type="password" formControlName="confirm" autocomplete="new-password" maxlength="128" [class.invalid]="mismatch()" />
          @if (mismatch()) { <span class="err">The passwords do not match.</span> }
        </label>
        @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
        <button class="go" type="submit" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Save password' }}</button>
      </form>
    </div></div>
  `,
  styles: [SET_PASSWORD_STYLES]
})
export class ChangePasswordComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly required = this.route.snapshot.queryParamMap.get('required') === '1';
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    current: ['', [Validators.required, Validators.maxLength(128)]],
    next: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirm: ['', [Validators.required]]
  });

  bad(f: 'current' | 'next'): boolean { const c = this.form.controls[f]; return c.invalid && c.touched; }
  mismatch(): boolean { const v = this.form.getRawValue(); return this.form.controls.confirm.touched && v.confirm !== v.next; }

  submit(): void {
    this.form.markAllAsTouched();
    const v = this.form.getRawValue();
    if (this.form.invalid || v.next !== v.confirm) return;
    this.busy.set(true); this.error.set('');
    this.auth.changePassword(v.current, v.next).subscribe({
      next: r => {
        this.busy.set(false);
        const back = this.route.snapshot.queryParamMap.get('returnUrl');
        const safe = back && back.startsWith('/') && !back.startsWith('//') && !back.includes('://') && !back.startsWith('/change-password') ? back : null;
        this.router.navigateByUrl(safe ?? (r.role === 'STAFF' ? '/staff' : ['ADMIN', 'ORGANIZER', 'EVENT_MANAGER', 'FINANCE'].includes(r.role) ? '/admin' : '/'), { replaceUrl: true });
      },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'The password could not be changed.'); }
    });
  }
}
