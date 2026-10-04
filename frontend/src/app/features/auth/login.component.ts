import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { BrandMarkComponent } from '../../shared/brand-mark.component';

@Component({
  selector: 'lk-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, BrandMarkComponent],
  template: `
    <div class="login-page">
      <section class="login-brand">
        <div class="brand-mark"><lk-brand-mark label="Events" [height]="48" /></div>
        <small>Event operations platform</small>
        <div class="brand-rule"></div><p>One secure workspace for event management, payments and event-day entry.</p>
        <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Technology by Neelastack ↗</a>
      </section>
      <main>
        <div class="eyebrow">Authorized access</div><h1>Welcome back.</h1><p>Sign in to manage events, ticket sales and event-day check-in.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label for="email">Email
            <input id="email" type="email" formControlName="email" autocomplete="username" maxlength="255" required [attr.aria-invalid]="bad('email')" />
            @if (bad('email')) { <span class="field-error">Enter a valid email address.</span> }
          </label>
          <label for="password">Password
            <input id="password" type="password" formControlName="password" autocomplete="current-password" maxlength="128" required [attr.aria-invalid]="bad('password')" />
            @if (bad('password')) { <span class="field-error">Enter your password (minimum 8 characters).</span> }
          </label>
          <div class="error" *ngIf="error" role="alert">{{error}}</div>
          <button [disabled]="form.invalid || loading" type="submit">{{loading?'Signing in…':'Sign in'}} <span>→</span></button>
        </form>
        <a routerLink="/" class="back">← Back to all events</a>
      </main>
    </div>
  `,
  styles: [`
    .login-page{min-height:100vh;background:#08070b;color:#fff;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr)}.login-brand{display:flex;flex-direction:column;justify-content:center;padding:clamp(32px,8vw,110px);background:radial-gradient(circle at 25% 30%,rgba(116,47,84,.35),transparent 45%),linear-gradient(145deg,#130c14,#08070b)}.brand-mark{font-size:30px;margin-bottom:14px}.login-brand>small{color:#827985;font-size:12px}.brand-rule{width:74px;height:1px;background:rgba(255,255,255,.16);margin:38px 0 20px}.login-brand>p{max-width:390px;color:#8f8791;font-size:13px;line-height:1.7}.login-brand>a{margin-top:28px;color:#8d8290;text-decoration:none;font-size:10px}.login-page main{align-self:center;width:min(500px,100%);padding:48px;margin:auto}.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8b8390;font-size:10px;font-weight:800}.login-page h1{font-size:clamp(50px,6vw,70px);letter-spacing:-.06em;line-height:.94;margin:15px 0 10px}.login-page main>p{color:#8b838f;font-size:13px;line-height:1.6}.login-page form{display:grid;gap:18px;margin-top:34px}.login-page label{display:grid;gap:8px;text-transform:uppercase;letter-spacing:.1em;font-size:10px;font-weight:800}.login-page input{height:54px;background:#100d14;border:1px solid rgba(255,255,255,.11);border-radius:13px;color:#fff!important;-webkit-text-fill-color:#fff;caret-color:#fff;padding:0 14px;outline:none;color-scheme:dark}.login-page input[aria-invalid="true"]{border-color:#b86a6a}.login-page input:-webkit-autofill,.login-page input:-webkit-autofill:hover,.login-page input:-webkit-autofill:focus{-webkit-text-fill-color:#fff;box-shadow:0 0 0 1000px #100d14 inset;caret-color:#fff}.login-page input:focus{border-color:#d4a64e;box-shadow:0 0 0 4px rgba(212,166,78,.08)}.login-page button{height:56px;border:0;border-radius:14px;background:#fff;color:#171218;font-weight:800;cursor:pointer}.login-page button:disabled{opacity:.45}.login-page button span{float:right;font-size:19px}.field-error{color:#d9a0a0;font-size:11px;font-weight:600}.error{color:#db9393;font-size:11px}.back{display:inline-block;margin-top:25px;color:#746b78;text-decoration:none;font-size:11px}@media(max-width:800px){.login-page{grid-template-columns:1fr}.login-brand{padding:38px 28px;min-height:37vh}.login-page main{padding:34px 24px 48px}.login-page h1{font-size:48px}}
  `]
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  loading = false;
  error = '';
  form = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]], password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128)]] });

  bad(f: 'email' | 'password'): boolean { const c = this.form.controls[f]; return c.touched && c.invalid; }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.loading = true;
    this.error = '';
    const { email, password } = this.form.getRawValue();
    this.auth.login(email.trim().toLowerCase(), password).subscribe({
      next: r => {
        const returnUrl = this.safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl'));
        // Accounts created with an initial password must pick their own before anything else works.
        this.auth.mustChangePassword().subscribe({
          next: must => { this.loading = false; if (must) this.router.navigate(['/change-password'], { queryParams: { required: 1, ...(returnUrl ? { returnUrl } : {}) }, replaceUrl: true }); else this.finish(r.role, returnUrl); },
          error: () => { this.loading = false; this.finish(r.role, returnUrl); }
        });
      },
      error: e => { this.loading = false; this.error = e?.error?.message || 'Invalid email or password.'; }
    });
  }

  private finish(role: string, returnUrl: string | null): void {
    if (returnUrl) { this.router.navigateByUrl(returnUrl, { replaceUrl: true }); return; }
    if (role === 'STAFF') {
      this.router.navigateByUrl('/staff', { replaceUrl: true });
    } else if (['ADMIN', 'ORGANIZER', 'EVENT_MANAGER', 'FINANCE'].includes(role)) {
      this.router.navigateByUrl('/admin', { replaceUrl: true });
    } else {
      this.router.navigateByUrl('/', { replaceUrl: true });
    }
  }

  private safeReturnUrl(value: string | null): string | null {
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('://')) return null;
    return value;
  }
}
