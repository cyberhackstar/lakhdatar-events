import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { EventManagerView } from '../../core/api/api.models';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';

/** Event-day people: gate staff accounts, gate assignments, event managers and their event assignments. */
@Component({
  selector: 'lk-team',
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <div class="page-head">
      <div>
        <div class="eyebrow">Event-day access</div>
        <h1 class="title">Team &amp; access</h1>
        <p class="sub">Create accounts for gate staff and event managers, then attach them to the events they should operate. Nobody can see an event until it is explicitly assigned.</p>
      </div>
    </div>

    @if (!events().length && !store.loading()) {
      <div class="alert info">You have no events yet. Create an event first, then come back to assign staff and managers.</div>
    }

    <div class="grid">
      @if (isAdmin) {
        <section class="card">
          <div class="eyebrow">Step 1 · Gate staff</div><h2>Create a staff account</h2>
          <p>Gate staff can only sign in to the scanner console and check tickets at the gates they are assigned.</p>
          <form [formGroup]="staffForm" (ngSubmit)="createStaff()" novalidate>
            <label class="field">Email<input formControlName="email" type="email" placeholder="gate@example.com" autocomplete="off" [class.invalid]="bad(staffForm,'email')" /></label>
            <label class="field">Name<input formControlName="name" placeholder="Gate operator" autocomplete="off" [class.invalid]="bad(staffForm,'name')" /></label>
            <label class="field">Password<input formControlName="password" type="password" placeholder="12+ characters" autocomplete="new-password" [class.invalid]="bad(staffForm,'password')" /><small>At least 12 characters. Share it securely.</small></label>
            <button class="a-btn primary" type="submit" [disabled]="staffBusy()">{{ staffBusy() ? 'Creating…' : 'Create staff account' }} →</button>
          </form>
          @if (staffMessage()) { <div class="alert success" role="status">{{ staffMessage() }}</div> }
          @if (staffError()) { <div class="alert error" role="alert">{{ staffError() }}</div> }
        </section>
      }

      <section class="card">
        <div class="eyebrow">{{ isAdmin ? 'Step 2 · ' : '' }}Gate assignment</div><h2>Attach staff to an event gate</h2>
        <p>Only an account with the STAFF role can be assigned to a gate.</p>
        <form [formGroup]="assignForm" (ngSubmit)="assignStaff()" novalidate>
          <label class="field">Event
            <select formControlName="eventId" [class.invalid]="bad(assignForm,'eventId')"><option value="">Choose an event</option>@for (e of events(); track e.id) { <option [value]="e.id">{{ e.name }}</option> }</select>
          </label>
          <label class="field">Staff email<input formControlName="email" type="email" placeholder="gate@example.com" autocomplete="off" [class.invalid]="bad(assignForm,'email')" /></label>
          <label class="field">Gate<input formControlName="gate" placeholder="Main Gate" autocomplete="off" [class.invalid]="bad(assignForm,'gate')" /></label>
          <button class="a-btn primary" type="submit" [disabled]="assignBusy()">{{ assignBusy() ? 'Assigning…' : 'Assign gate' }} →</button>
        </form>
        @if (assignMessage()) { <div class="alert success" role="status">{{ assignMessage() }}</div> }
        @if (assignError()) { <div class="alert error" role="alert">{{ assignError() }}</div> }
      </section>

      @if (isAdmin) {
        <section class="card">
          <div class="eyebrow">Event managers</div><h2>Create a manager account</h2>
          <p>An EVENT_MANAGER can scan and issue complimentary tickets, but only for the events assigned to them.</p>
          <form [formGroup]="managerForm" (ngSubmit)="createManager()" novalidate>
            <label class="field">Email<input formControlName="email" type="email" placeholder="manager@example.com" autocomplete="off" [class.invalid]="bad(managerForm,'email')" /></label>
            <label class="field">Name<input formControlName="name" placeholder="Event manager" autocomplete="off" [class.invalid]="bad(managerForm,'name')" /></label>
            <label class="field">Password<input formControlName="password" type="password" placeholder="12+ characters" autocomplete="new-password" [class.invalid]="bad(managerForm,'password')" /><small>At least 12 characters.</small></label>
            <button class="a-btn primary" type="submit" [disabled]="managerBusy()">{{ managerBusy() ? 'Creating…' : 'Create manager account' }} →</button>
          </form>
          @if (managerMessage()) { <div class="alert success" role="status">{{ managerMessage() }}</div> }
          @if (managerError()) { <div class="alert error" role="alert">{{ managerError() }}</div> }
        </section>
      }

      <section class="card">
        <div class="eyebrow">Event managers</div><h2>Assign a manager to an event</h2>
        <p>Managers can scan only their assigned events and issue complimentary tickets only there.</p>
        <form [formGroup]="mAssignForm" (ngSubmit)="assignManager()" novalidate>
          <label class="field">Event
            <select formControlName="eventId" (change)="loadManagers($any($event.target).value)" [class.invalid]="bad(mAssignForm,'eventId')"><option value="">Choose an event</option>@for (e of events(); track e.id) { <option [value]="e.id">{{ e.name }}</option> }</select>
          </label>
          <label class="field">Manager email<input formControlName="email" type="email" placeholder="manager@example.com" autocomplete="off" [class.invalid]="bad(mAssignForm,'email')" /></label>
          <button class="a-btn primary" type="submit" [disabled]="mAssignBusy()">{{ mAssignBusy() ? 'Assigning…' : 'Assign manager' }} →</button>
        </form>
        @if (mAssignMessage()) { <div class="alert success" role="status">{{ mAssignMessage() }}</div> }
        @if (mAssignError()) { <div class="alert error" role="alert">{{ mAssignError() }}</div> }
        @if (managersEventId()) {
          <div class="members">
            <small>Managers assigned to this event</small>
            @for (m of managers(); track m.userId) {
              <div class="member"><span><b>{{ m.fullName }}</b><small>{{ m.email }}</small></span><button type="button" class="a-btn sm danger" (click)="unassign(m.email)">Remove</button></div>
            } @empty { <div class="none">No manager is assigned to this event yet.</div> }
          </div>
        }
      </section>
    </div>
  `,
  styles: [ADMIN_UI_STYLES, `
    .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;align-items:start}.grid .card{margin:0!important}
    form{display:grid;gap:14px}
    form .a-btn{justify-content:space-between;min-height:48px}
    .members{margin-top:18px;padding-top:14px;border-top:1px solid #eee8e0}.members>small{display:block;color:#8a8190;font-size:11px;text-transform:uppercase;letter-spacing:.1em;font-weight:800;margin-bottom:6px}
    .member{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid #f1ece5}.member span{display:grid;gap:2px;min-width:0}.member b{font-size:14px}.member small{color:#8a8190;font-size:12px;overflow-wrap:anywhere}
    .none{font-size:13px;color:#8a8190;padding:8px 0}
    @media(max-width:1000px){.grid{grid-template-columns:1fr}}
  `]
})
export class TeamComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  get isAdmin(): boolean { return this.auth.role() === 'ADMIN'; }
  events = () => this.store.dash()?.events ?? [];

  readonly staffBusy = signal(false); readonly staffMessage = signal(''); readonly staffError = signal('');
  readonly assignBusy = signal(false); readonly assignMessage = signal(''); readonly assignError = signal('');
  readonly managerBusy = signal(false); readonly managerMessage = signal(''); readonly managerError = signal('');
  readonly mAssignBusy = signal(false); readonly mAssignMessage = signal(''); readonly mAssignError = signal('');
  readonly managers = signal<EventManagerView[]>([]);
  readonly managersEventId = signal('');

  readonly staffForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]]
  });
  readonly assignForm = this.fb.nonNullable.group({
    eventId: ['', Validators.required],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    gate: ['Main Gate', [Validators.required, Validators.maxLength(80)]]
  });
  readonly managerForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]]
  });
  readonly mAssignForm = this.fb.nonNullable.group({
    eventId: ['', Validators.required],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]]
  });

  ngOnInit(): void { this.store.load(); }

  bad(form: { controls: Record<string, { invalid: boolean; touched: boolean }> }, field: string): boolean {
    const c = form.controls[field]; return c.invalid && c.touched;
  }

  createStaff(): void {
    if (this.staffForm.invalid) { this.staffForm.markAllAsTouched(); this.staffError.set('Enter a valid email, a name and a password of at least 12 characters.'); return; }
    this.staffBusy.set(true); this.staffMessage.set(''); this.staffError.set('');
    const v = this.staffForm.getRawValue();
    this.api.createStaff({ ...v, email: v.email.trim().toLowerCase() }).subscribe({
      next: () => { this.staffBusy.set(false); this.staffMessage.set(`Staff account ${v.email.trim().toLowerCase()} created. Assign it to an event gate next.`); this.assignForm.controls.email.setValue(v.email.trim().toLowerCase()); this.staffForm.reset({ email: '', name: '', password: '' }); },
      error: e => { this.staffBusy.set(false); this.staffError.set(e?.error?.message || 'Staff account could not be created.'); }
    });
  }

  assignStaff(): void {
    if (this.assignForm.invalid) { this.assignForm.markAllAsTouched(); this.assignError.set('Choose an event and enter the staff email and gate.'); return; }
    this.assignBusy.set(true); this.assignMessage.set(''); this.assignError.set('');
    const v = this.assignForm.getRawValue();
    this.api.assignStaff(v.eventId, { email: v.email.trim().toLowerCase(), gate: v.gate.trim() }).subscribe({
      next: () => { this.assignBusy.set(false); this.assignMessage.set(`Assigned ${v.email.trim().toLowerCase()} to ${v.gate.trim()}.`); },
      error: e => { this.assignBusy.set(false); this.assignError.set(e?.error?.message || 'Staff assignment failed.'); }
    });
  }

  createManager(): void {
    if (this.managerForm.invalid) { this.managerForm.markAllAsTouched(); this.managerError.set('Enter a valid email, a name and a password of at least 12 characters.'); return; }
    this.managerBusy.set(true); this.managerMessage.set(''); this.managerError.set('');
    const v = this.managerForm.getRawValue();
    this.api.createManager({ ...v, email: v.email.trim().toLowerCase() }).subscribe({
      next: () => { this.managerBusy.set(false); this.managerMessage.set(`Manager ${v.email.trim().toLowerCase()} created. Assign it to one or more events before it can operate them.`); this.mAssignForm.controls.email.setValue(v.email.trim().toLowerCase()); this.managerForm.reset({ email: '', name: '', password: '' }); },
      error: e => { this.managerBusy.set(false); this.managerError.set(e?.error?.message || 'Manager account could not be created.'); }
    });
  }

  loadManagers(eventId: string): void {
    this.managersEventId.set(eventId || ''); this.managers.set([]);
    if (!eventId) return;
    this.api.adminManagers(eventId).subscribe({
      next: list => this.managers.set(list),
      error: e => this.mAssignError.set(e?.error?.message || 'Could not load assigned managers.')
    });
  }

  assignManager(): void {
    if (this.mAssignForm.invalid) { this.mAssignForm.markAllAsTouched(); this.mAssignError.set('Choose an event and enter the manager email.'); return; }
    this.mAssignBusy.set(true); this.mAssignMessage.set(''); this.mAssignError.set('');
    const v = this.mAssignForm.getRawValue();
    const email = v.email.trim().toLowerCase();
    this.api.assignManager(v.eventId, { email }).subscribe({
      next: () => { this.mAssignBusy.set(false); this.mAssignMessage.set(`Manager ${email} is now assigned to this event.`); this.loadManagers(v.eventId); },
      error: e => { this.mAssignBusy.set(false); this.mAssignError.set(e?.error?.message || 'Manager assignment failed.'); }
    });
  }

  unassign(email: string): void {
    const eventId = this.managersEventId();
    if (!eventId || !globalThis.confirm(`Remove ${email} from this event?`)) return;
    this.mAssignMessage.set(''); this.mAssignError.set('');
    this.api.unassignManager(eventId, email).subscribe({
      next: () => { this.managers.update(list => list.filter(m => m.email.toLowerCase() !== email.toLowerCase())); this.mAssignMessage.set(`Manager ${email} removed from this event.`); },
      error: e => this.mAssignError.set(e?.error?.message || 'Manager removal failed.')
    });
  }
}
