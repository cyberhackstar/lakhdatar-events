import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api/api.service';
import { EventTeam, EventTeamStaffRow, TeamKind, TeamMember, TeamView } from '../../core/api/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ToastService } from '../../shared/toast.service';
import { ADMIN_UI_STYLES } from './admin.styles';
import { MemberDrawerComponent } from './member-drawer.component';

type Pending = { kind: 'staff' | 'manager'; email: string; name: string } | null;

/**
 * "Team for this event": gate -> staff table (change gate / remove), event managers (remove), and pickers that
 * only offer THIS organizer's active members who are not already on the event. Used on /admin/team and in the event editor.
 */
@Component({
  selector: 'lk-event-team-panel',
  standalone: true,
  imports: [FormsModule, ConfirmDialogComponent, MemberDrawerComponent],
  template: `
    <section class="card">
      <div class="eyebrow">Event-day access</div>
      <h2>Team for this event</h2>
      <p>Gate staff scan only at the gate you give them. Managers see and scan only the events assigned to them.</p>

      @if (loading()) {
        <div class="skeleton-line" style="width:60%"></div><div class="skeleton-line"></div><div class="skeleton-line" style="width:80%"></div>
      } @else if (loadError()) {
        <div class="alert error" role="alert">{{ loadError() }} <button type="button" class="a-btn sm" (click)="reload()">Retry</button></div>
      } @else {

        <h3>Gate staff</h3>
        @if (eventTeam()?.staff?.length) {
          <div class="tbl" role="table" aria-label="Gate staff for this event">
            <div class="tr th" role="row"><span role="columnheader">Gate</span><span role="columnheader">Staff</span><span role="columnheader">Status</span><span role="columnheader">Actions</span></div>
            @for (r of eventTeam()!.staff; track r.userId) {
              <div class="tr" role="row">
                <span class="gate" role="cell" data-label="Gate">
                  @if (editing() === r.userId) {
                    <input class="cell-input" [(ngModel)]="editGate" [attr.list]="listId" maxlength="80" aria-label="New gate name" (keydown.enter)="saveGate(r)" />
                  } @else { <b>{{ r.gate || '—' }}</b> }
                </span>
                <span role="cell" data-label="Staff"><b>{{ r.name }}</b><small>{{ r.email }}</small></span>
                <span role="cell" data-label="Status"><i class="chip" [class.off]="!r.active">{{ r.active ? 'Active' : 'Deactivated' }}</i></span>
                <span class="acts" role="cell">
                  @if (editing() === r.userId) {
                    <button type="button" class="a-btn sm primary" [disabled]="busy()" (click)="saveGate(r)">Save</button>
                    <button type="button" class="a-btn sm" (click)="editing.set('')">Cancel</button>
                  } @else {
                    <button type="button" class="a-btn sm" (click)="startEdit(r)">Change gate</button>
                    <button type="button" class="a-btn sm danger" (click)="pending.set({ kind: 'staff', email: r.email, name: r.name })">Remove</button>
                  }
                </span>
              </div>
            }
          </div>
        } @else { <div class="none">No staff are assigned to a gate yet.</div> }

        @if (!pool()?.staff?.length) {
          <div class="empty-cta"><b>No staff on this team yet.</b><button type="button" class="a-btn primary" (click)="drawer.set('staff')">Add your first staff member</button></div>
        } @else {
          <form class="row" (ngSubmit)="assignStaff()" novalidate>
            <label class="field">Staff member
              <select [(ngModel)]="staffPick" name="staffPick" [class.invalid]="staffTouched() && !staffPick">
                <option value="">{{ availableStaff().length ? 'Choose staff…' : 'Everyone is already assigned' }}</option>
                @for (m of availableStaff(); track m.id) { <option [value]="m.email">{{ m.name }} · {{ m.email }}</option> }
              </select>
            </label>
            <label class="field">Gate
              <input [(ngModel)]="gatePick" name="gatePick" [attr.list]="listId" maxlength="80" autocomplete="off" placeholder="Main Gate" [class.invalid]="staffTouched() && !gatePick.trim()" />
              <datalist [id]="listId">@for (g of gateSuggestions(); track g) { <option [value]="g"></option> }</datalist>
            </label>
            <button type="submit" class="a-btn primary" [disabled]="busy() || !availableStaff().length">{{ busy() ? 'Assigning…' : 'Assign to gate' }}</button>
          </form>
          <button type="button" class="link" (click)="drawer.set('staff')">+ Add new staff to your team</button>
        }

        <h3>Event managers</h3>
        @if (eventTeam()?.managers?.length) {
          <div class="list">
            @for (m of eventTeam()!.managers; track m.userId) {
              <div class="li"><span><b>{{ m.name }}</b><small>{{ m.email }}</small></span>
                <i class="chip" [class.off]="!m.active">{{ m.active ? 'Active' : 'Deactivated' }}</i>
                <button type="button" class="a-btn sm danger" (click)="pending.set({ kind: 'manager', email: m.email, name: m.name })">Remove</button></div>
            }
          </div>
        } @else { <div class="none">No manager is assigned to this event yet.</div> }

        @if (!pool()?.managers?.length) {
          <div class="empty-cta"><b>No managers on this team yet.</b><button type="button" class="a-btn primary" (click)="drawer.set('managers')">Add your first manager</button></div>
        } @else {
          <form class="row" (ngSubmit)="assignManager()" novalidate>
            <label class="field">Manager
              <select [(ngModel)]="managerPick" name="managerPick" [class.invalid]="managerTouched() && !managerPick">
                <option value="">{{ availableManagers().length ? 'Choose a manager…' : 'Everyone is already assigned' }}</option>
                @for (m of availableManagers(); track m.id) { <option [value]="m.email">{{ m.name }} · {{ m.email }}</option> }
              </select>
            </label>
            <button type="submit" class="a-btn primary" [disabled]="busy() || !availableManagers().length">{{ busy() ? 'Assigning…' : 'Assign manager' }}</button>
          </form>
          <button type="button" class="link" (click)="drawer.set('managers')">+ Add new manager to your team</button>
        }
      }
    </section>

    <lk-confirm-dialog [open]="!!pending()" [busy]="busy()" confirmLabel="Remove"
      [title]="pending()?.kind === 'staff' ? 'Remove from gate?' : 'Remove manager?'"
      [message]="pending() ? (pending()!.name + ' will no longer be able to ' + (pending()!.kind === 'staff' ? 'scan at this event.' : 'see or scan this event.') + ' Their account stays on your team.') : ''"
      (confirmed)="confirmRemove()" (cancelled)="pending.set(null)" />

    <lk-member-drawer [open]="!!drawer()" [kind]="drawer() || 'staff'" [slug]="organizerSlug()" [organizerName]="pool()?.name || ''"
      [inviteEnabled]="pool()?.inviteEmailEnabled || false" (closed)="drawer.set(null)" (created)="onCreated()" />
  `,
  styles: [ADMIN_UI_STYLES, `
    h3{font-size:13px;text-transform:uppercase;letter-spacing:.1em;color:#8a8190;margin:22px 0 10px;font-weight:800}
    .none{font-size:13px;color:#8a8190;padding:6px 0 12px}
    .tbl{border:1px solid #eee8e0;border-radius:14px;overflow:hidden;margin-bottom:14px}
    .tr{display:grid;grid-template-columns:minmax(120px,1fr) minmax(0,2fr) 110px auto;gap:12px;align-items:center;padding:12px 14px;border-top:1px solid #f1ece5}
    .tr:first-child{border-top:0}.th{background:#faf8f5;font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:#8a8190;font-weight:800}
    .tr span{min-width:0;display:grid;gap:2px}.tr small,.li small{color:#8a8190;font-size:12px;overflow-wrap:anywhere}
    .acts{display:flex!important;gap:8px;flex-wrap:wrap;justify-content:flex-end}
    .chip{font-style:normal;font-size:11px;font-weight:800;border-radius:999px;padding:4px 9px;background:#e4f2e7;color:#2f6a3d;justify-self:start}.chip.off{background:#fde8e8;color:#8c2f2f}
    .row{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(0,1fr) auto;gap:12px;align-items:end;margin:6px 0 4px}
    .row .a-btn{min-height:46px}
    .list .li{display:flex;align-items:center;gap:12px;padding:10px 0;border-bottom:1px solid #f1ece5}.li span{display:grid;gap:2px;flex:1;min-width:0}
    .link{background:none;border:0;color:#7a5a12;font-weight:700;font-size:13px;cursor:pointer;padding:10px 0;min-height:44px}
    .empty-cta{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;background:#faf8f5;border:1px dashed #d9d2c8;border-radius:14px;padding:14px 16px;margin:8px 0}
    @media(max-width:700px){
      .th{display:none}
      .tr{grid-template-columns:1fr;gap:6px;padding:14px}
      .tr [data-label]::before{content:attr(data-label);font-size:10px;text-transform:uppercase;letter-spacing:.08em;color:#a79fa9;font-weight:800}
      .acts{justify-content:flex-start}.acts .a-btn{flex:1}
      .row{grid-template-columns:1fr}
      .li{flex-wrap:wrap}
    }
  `]
})
export class EventTeamPanelComponent {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  readonly eventId = input.required<string>();
  readonly organizerSlug = input.required<string>();
  /** Optional shared team so /admin/team does not fetch it twice. When omitted the panel loads it itself. */
  readonly team = input<TeamView | undefined>(undefined);
  readonly changed = output<void>();

  readonly eventTeam = signal<EventTeam | undefined>(undefined);
  private readonly ownTeam = signal<TeamView | undefined>(undefined);
  readonly pool = computed(() => this.team() ?? this.ownTeam());
  readonly loading = signal(true);
  readonly loadError = signal('');
  readonly busy = signal(false);
  readonly editing = signal('');
  readonly pending = signal<Pending>(null);
  readonly drawer = signal<TeamKind | null>(null);
  readonly staffTouched = signal(false);
  readonly managerTouched = signal(false);
  readonly listId = 'gates-' + Math.random().toString(36).slice(2, 8);
  staffPick = ''; gatePick = 'Main Gate'; managerPick = ''; editGate = '';

  readonly availableStaff = computed<TeamMember[]>(() => {
    const taken = new Set((this.eventTeam()?.staff ?? []).map(s => s.email.toLowerCase()));
    return (this.pool()?.staff ?? []).filter(m => m.active && !taken.has(m.email.toLowerCase()));
  });
  readonly availableManagers = computed<TeamMember[]>(() => {
    const taken = new Set((this.eventTeam()?.managers ?? []).map(s => s.email.toLowerCase()));
    return (this.pool()?.managers ?? []).filter(m => m.active && !taken.has(m.email.toLowerCase()));
  });
  readonly gateSuggestions = computed<string[]>(() => {
    const names = new Map<string, string>();
    const add = (g?: string | null) => { const t = (g || '').trim(); if (t) names.set(t.toLowerCase(), t); };
    add('Main Gate');
    (this.eventTeam()?.staff ?? []).forEach(s => add(s.gate));
    (this.pool()?.staff ?? []).forEach(m => m.assignments.forEach(a => add(a.gate)));
    return [...names.values()].sort((a, b) => a.localeCompare(b));
  });

  constructor() {
    effect(() => {
      const id = this.eventId(); const slug = this.organizerSlug();
      untracked(() => { this.staffPick = ''; this.managerPick = ''; this.editing.set(''); this.load(id, slug, true); });
    });
  }

  reload(): void { this.load(this.eventId(), this.organizerSlug(), true); }

  private load(id: string, slug: string, withSkeleton: boolean): void {
    if (!id) return;
    if (withSkeleton) this.loading.set(true);
    this.loadError.set('');
    this.api.eventTeam(id).subscribe({
      next: t => { this.eventTeam.set(t); if (!this.team() && slug) this.loadPool(slug); else this.loading.set(false); },
      error: e => { this.loading.set(false); this.loadError.set(e?.error?.message || 'The event team could not be loaded.'); }
    });
  }

  private loadPool(slug: string): void {
    this.api.orgTeam(slug).subscribe({
      next: t => { this.ownTeam.set(t); this.loading.set(false); },
      error: e => { this.loading.set(false); this.loadError.set(e?.error?.message || 'Your team list could not be loaded.'); }
    });
  }

  private refresh(): void {
    this.load(this.eventId(), this.organizerSlug(), false);
    this.changed.emit();
  }

  startEdit(r: EventTeamStaffRow): void { this.editGate = r.gate || ''; this.editing.set(r.userId); }

  assignStaff(): void {
    this.staffTouched.set(true);
    const gate = this.gatePick.trim();
    if (!this.staffPick || !gate) return;
    this.busy.set(true);
    this.api.assignStaff(this.eventId(), { email: this.staffPick, gate }).subscribe({
      next: () => { this.busy.set(false); this.toast.success(`Assigned to ${gate}.`); this.staffPick = ''; this.staffTouched.set(false); this.refresh(); },
      error: e => { this.busy.set(false); this.toast.error(e?.error?.message || 'The staff assignment failed.'); }
    });
  }

  saveGate(r: EventTeamStaffRow): void {
    const gate = this.editGate.trim();
    if (!gate) { this.toast.error('Enter a gate name.'); return; }
    if (gate === (r.gate || '')) { this.editing.set(''); return; }
    this.busy.set(true);
    this.api.changeStaffGate(this.eventId(), { email: r.email, gate }).subscribe({
      next: () => { this.busy.set(false); this.editing.set(''); this.toast.success(`${r.name} moved to ${gate}.`); this.refresh(); },
      error: e => { this.busy.set(false); this.toast.error(e?.error?.message || 'The gate could not be changed.'); }
    });
  }

  assignManager(): void {
    this.managerTouched.set(true);
    if (!this.managerPick) return;
    this.busy.set(true);
    this.api.assignManager(this.eventId(), { email: this.managerPick }).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Manager assigned to this event.'); this.managerPick = ''; this.managerTouched.set(false); this.refresh(); },
      error: e => { this.busy.set(false); this.toast.error(e?.error?.message || 'The manager assignment failed.'); }
    });
  }

  confirmRemove(): void {
    const p = this.pending(); if (!p) return;
    this.busy.set(true);
    const call = p.kind === 'staff' ? this.api.removeStaff(this.eventId(), p.email) : this.api.unassignManager(this.eventId(), p.email);
    call.subscribe({
      next: () => { this.busy.set(false); this.pending.set(null); this.toast.success(`${p.name} removed from this event.`); this.refresh(); },
      error: e => { this.busy.set(false); this.pending.set(null); this.toast.error(e?.error?.message || 'The removal failed.'); }
    });
  }

  onCreated(): void {
    this.drawer.set(null);
    if (!this.team()) this.loadPool(this.organizerSlug());
    this.changed.emit();
  }
}
