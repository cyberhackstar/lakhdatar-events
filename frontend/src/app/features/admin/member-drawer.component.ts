import { Component, HostListener, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { CreatedTeamMember, TeamKind } from '../../core/api/api.models';
import { ToastService } from '../../shared/toast.service';
import { ADMIN_UI_STYLES } from './admin.styles';

const COPY: Record<TeamKind, { title: string; blurb: string; noun: string }> = {
  staff: { title: 'Add gate staff', noun: 'staff member', blurb: 'Gate staff can sign in to the scanner and check tickets only at the gates you assign them to.' },
  managers: { title: 'Add event manager', noun: 'event manager', blurb: 'Event managers can scan and issue complimentary tickets, but only for the events you assign them to.' },
  owners: { title: 'Add organizer owner', noun: 'owner', blurb: 'Owners manage this organizer end to end: events, payments views and the whole team.' }
};

/** Side drawer (full-screen sheet on phones) for creating a team member by invite or initial password. */
@Component({
  selector: 'lk-member-drawer',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    @if (open()) {
      <div class="backdrop" (click)="close()"></div>
      <aside class="drawer" role="dialog" aria-modal="true" [attr.aria-label]="copy().title">
        <header>
          <div><div class="eyebrow">{{ organizerName() }}</div><h2>{{ copy().title }}</h2></div>
          <button type="button" class="x" aria-label="Close" (click)="close()">×</button>
        </header>
        <p class="blurb">{{ copy().blurb }}</p>

        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label class="field">Full name <span class="req">*</span>
            <input formControlName="name" autocomplete="off" placeholder="e.g. Aarav Sharma" [class.invalid]="bad('name')" />
            @if (bad('name')) { <span class="err">Enter a name (2–120 characters).</span> }
          </label>
          <label class="field">Email <span class="req">*</span>
            <input formControlName="email" type="email" autocomplete="off" inputmode="email" placeholder="name@company.com" [class.invalid]="bad('email')" />
            @if (bad('email')) { <span class="err">Enter a valid email address.</span> }
            <small>Used to sign in. One email can belong to one account on the whole platform.</small>
          </label>
          <label class="field">Phone <small>(optional)</small>
            <input formControlName="phone" type="tel" autocomplete="off" inputmode="tel" placeholder="+91 98765 43210" [class.invalid]="bad('phone')" />
            @if (bad('phone')) { <span class="err">Use digits, spaces, + ( ) or - only.</span> }
          </label>

          <fieldset class="how">
            <legend>How should they get access?</legend>
            @if (inviteEnabled()) {
              <label class="choice"><input type="radio" name="mode" value="invite" [checked]="mode() === 'invite'" (change)="setMode('invite')" />
                <span><b>Send an email invite</b><small>They set their own password from a one-time link that expires in 48 hours.</small></span></label>
            }
            <label class="choice"><input type="radio" name="mode" value="password" [checked]="mode() === 'password'" (change)="setMode('password')" />
              <span><b>Set an initial password</b><small>{{ inviteEnabled() ? 'Share it securely. They must change it the first time they sign in.' : 'Email invites are not set up on this server, so choose a password and share it securely. They must change it at first sign-in.' }}</small></span></label>
          </fieldset>

          @if (mode() === 'password') {
            <label class="field">Initial password <span class="req">*</span>
              <input formControlName="password" type="password" autocomplete="new-password" placeholder="At least 12 characters" [class.invalid]="bad('password')" />
              @if (bad('password')) { <span class="err">Use 12–128 characters.</span> }
            </label>
          }

          @if (error()) { <div class="alert error" role="alert">{{ error() }}</div> }
          <div class="actions">
            <button type="button" class="a-btn" (click)="close()">Cancel</button>
            <button type="submit" class="a-btn primary" [disabled]="busy()">{{ busy() ? 'Adding…' : (mode() === 'invite' ? 'Send invite' : 'Create ' + copy().noun) }}</button>
          </div>
        </form>
      </aside>
    }
  `,
  styles: [ADMIN_UI_STYLES, `
    :host{display:contents}
    .backdrop{position:fixed;inset:0;background:rgba(14,10,18,.5);z-index:120}
    .drawer{position:fixed;z-index:130;top:0;right:0;bottom:0;width:min(460px,100vw);background:#fff;padding:24px;overflow-y:auto;box-shadow:-24px 0 60px rgba(14,10,18,.25);padding-bottom:calc(24px + env(safe-area-inset-bottom,0px));padding-top:calc(24px + env(safe-area-inset-top,0px))}
    header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}
    header h2{font-family:var(--display);font-size:26px;letter-spacing:-.03em;margin:6px 0 0;font-weight:600}
    .x{border:0;background:#f4f1ec;border-radius:10px;width:40px;height:40px;font-size:22px;cursor:pointer;color:#4a414d}
    .blurb{color:#6f6573;font-size:13px;line-height:1.6;margin:10px 0 18px}
    form{display:grid;gap:16px}
    .how{border:1px solid #e5dfd7;border-radius:14px;padding:12px 14px;margin:0;display:grid;gap:10px;min-width:0}
    .how legend{font-size:13px;font-weight:700;color:#4a414d;padding:0 6px}
    .choice{display:flex;gap:10px;align-items:flex-start;cursor:pointer}
    .choice input{margin-top:4px;width:18px;height:18px;flex:none;accent-color:#17121a}
    .choice span{display:grid;gap:2px;font-size:14px}.choice small{color:#8a8190;font-size:12px;line-height:1.45}
    .actions{display:flex;gap:10px;justify-content:flex-end;flex-wrap:wrap;position:sticky;bottom:0;background:#fff;padding-top:8px}
    .actions .a-btn{min-height:48px}
    @media(max-width:560px){.drawer{width:100vw;padding:18px}.actions .a-btn{flex:1}}
  `]
})
export class MemberDrawerComponent {
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  readonly open = input(false);
  readonly kind = input<TeamKind>('staff');
  readonly slug = input('');
  readonly organizerName = input('');
  readonly inviteEnabled = input(false);
  readonly closed = output<void>();
  readonly created = output<CreatedTeamMember>();

  readonly busy = signal(false);
  readonly error = signal('');
  readonly mode = signal<'invite' | 'password'>('password');
  readonly copy = () => COPY[this.kind()];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.pattern(/^$|^\+?[0-9][0-9 ()\-]{5,29}$/)]],
    password: ['']
  });

  constructor() {
    // Every time the drawer opens: clean form, best default mode for this server.
    effect(() => {
      if (!this.open()) return;
      const invite = this.inviteEnabled();
      untracked(() => {
        this.form.reset({ name: '', email: '', phone: '', password: '' });
        this.error.set(''); this.busy.set(false);
        this.setMode(invite ? 'invite' : 'password');
      });
    });
  }

  @HostListener('document:keydown.escape') onEsc(): void { if (this.open()) this.close(); }

  setMode(m: 'invite' | 'password'): void {
    this.mode.set(m);
    const pw = this.form.controls.password;
    if (m === 'password') pw.setValidators([Validators.required, Validators.minLength(12), Validators.maxLength(128)]);
    else pw.clearValidators();
    pw.setValue(''); pw.markAsUntouched(); pw.updateValueAndValidity();
  }

  bad(field: 'name' | 'email' | 'phone' | 'password'): boolean {
    const c = this.form.controls[field]; return c.invalid && c.touched;
  }

  close(): void { if (!this.busy()) this.closed.emit(); }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const v = this.form.getRawValue();
    this.busy.set(true); this.error.set('');
    this.api.createTeamMember(this.slug(), this.kind(), {
      name: v.name.trim(), email: v.email.trim().toLowerCase(),
      phone: v.phone.trim() || null,
      password: this.mode() === 'password' ? v.password : null
    }).subscribe({
      next: r => {
        this.busy.set(false);
        if (r.delivery === 'INVITE_SENT') this.toast.success(`Invite sent to ${r.member.email}. The link works once and expires in 48 hours.`);
        else if (r.delivery === 'INVITE_FAILED') this.toast.error(`${r.member.name} was added, but the invite email could not be sent. Use “Resend invite” on their row.`);
        else this.toast.success(`${r.member.name} was added. Share the initial password securely; they must change it at first sign-in.`);
        this.created.emit(r);
      },
      error: e => { this.busy.set(false); this.error.set(e?.error?.message || 'The account could not be created. Please try again.'); }
    });
  }
}
