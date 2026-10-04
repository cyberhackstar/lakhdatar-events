import { Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { TeamKind, TeamMember, TeamView } from '../../core/api/api.models';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { ToastService } from '../../shared/toast.service';
import { ADMIN_UI_STYLES } from './admin.styles';
import { AdminStore } from './admin-store.service';
import { EventTeamPanelComponent } from './event-team-panel.component';
import { MemberDrawerComponent } from './member-drawer.component';

type Tab = 'staff' | 'managers';

/**
 * Organizer-centric team page. The organizer owns its people: ORGANIZER users manage their own organizer,
 * ADMIN (platform super-admin) can pick any organizer for support and oversight.
 */
@Component({
  selector: 'lk-team',
  standalone: true,
  imports: [RouterLink, ConfirmDialogComponent, MemberDrawerComponent, EventTeamPanelComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="eyebrow">{{ isAdmin ? 'Support & oversight' : 'Your organization' }}</div>
        <h1 class="title">Team</h1>
        <p class="sub">Neelastack platform admins create organizers and their owners. Organizer owners then add event managers and gate staff, and assign them only to the events and gates they should operate. People only ever see what you assign them.</p>
      </div>
      @if (showSwitcher()) {
        <label class="field switcher">Organizer
          <select [value]="slug()" (change)="pickOrganizer($any($event.target).value)" aria-label="Choose organizer">
            @for (o of store.organizers(); track o.id) { <option [value]="o.slug">{{ o.name }}</option> }
          </select>
        </label>
      } @else if (team()) { <div class="org-chip">{{ team()!.name }}</div> }
    </div>

    @if (!store.organizersLoaded()) {
      <div class="card"><div class="skeleton-line" style="width:40%"></div><div class="skeleton-line"></div><div class="skeleton-line" style="width:70%"></div></div>
    } @else if (store.organizersError()) {
      <div class="alert error" role="alert">{{ store.organizersError() }} <button type="button" class="a-btn sm" (click)="store.loadOrganizers(true)">Retry</button></div>
    } @else if (!store.organizers().length) {
      <div class="card empty">
        @if (isAdmin) { There are no organizers yet. <a routerLink="/admin/organizers">Create an organizer</a> first, then add its team here. }
        @else { Your account is not linked to an organizer yet. Ask a platform administrator to add you as an organizer owner. }
      </div>
    } @else {
      @if (loading()) {
        <div class="card"><div class="skeleton-line" style="width:35%"></div><div class="skeleton-line"></div><div class="skeleton-line"></div><div class="skeleton-line" style="width:75%"></div></div>
      } @else if (error()) {
        <div class="alert error" role="alert">{{ error() }} <button type="button" class="a-btn sm" (click)="loadTeam()">Retry</button></div>
      } @else if (team(); as t) {

        @if (!t.owners.length && isAdmin) {
          <div class="alert info">This organizer has no owner login yet, so nobody can manage its team except you.
            <button type="button" class="a-btn sm" (click)="drawer.set('owners')">Add organizer owner</button></div>
        }

        @if (!t.staff.length && !t.managers.length) {
          <div class="card first">
            <h2>Build your event-day team</h2>
            <p>Add gate staff to scan tickets and managers to run an event on the day. You can invite them by email or set a starting password.</p>
            <button type="button" class="a-btn primary" (click)="drawer.set('staff')">Add your first staff member</button>
            <button type="button" class="a-btn" (click)="drawer.set('managers')">Add an event manager</button>
          </div>
        } @else {
          <section class="card">
            <div class="tabs" role="tablist">
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'staff'" [class.on]="tab() === 'staff'" (click)="setTab('staff')">Gate staff <i>{{ t.staff.length }}</i></button>
              <button type="button" role="tab" [attr.aria-selected]="tab() === 'managers'" [class.on]="tab() === 'managers'" (click)="setTab('managers')">Event managers <i>{{ t.managers.length }}</i></button>
              <span class="grow"></span>
              <button type="button" class="a-btn primary add" (click)="drawer.set(tab())">+ {{ tab() === 'staff' ? 'Add staff' : 'Add manager' }}</button>
            </div>
            <input class="cell-input search" type="search" placeholder="Search by name or email" aria-label="Search team" [value]="query()" (input)="query.set($any($event.target).value)" />

            <div class="members">
              @for (m of visible(); track m.id) {
                <article class="member" [class.off]="!m.active">
                  <div class="avatar" aria-hidden="true">{{ initials(m.name) }}</div>
                  <div class="who">
                    @if (renaming() === m.id) {
                      <input class="cell-input" [value]="renameValue" maxlength="120" aria-label="Name" (input)="renameValue = $any($event.target).value" (keydown.enter)="saveRename(m)" />
                    } @else { <b>{{ m.name }}</b> }
                    <small>{{ m.email }}@if (m.phone) { · {{ m.phone }} }</small>
                    <div class="meta">
                      <i class="chip" [class.off]="!m.active" [class.pending]="m.active && m.invitePending">{{ !m.active ? 'Deactivated' : m.invitePending ? 'Invite pending' : 'Active' }}</i>
                      @for (a of m.assignments; track a.eventId) { <i class="tag">{{ a.eventName }}@if (a.gate) { · {{ a.gate }} }</i> }
                      @if (!m.assignments.length && m.active) { <i class="tag none">Not assigned to an event</i> }
                    </div>
                  </div>
                  <div class="acts">
                    @if (renaming() === m.id) {
                      <button type="button" class="a-btn sm primary" [disabled]="busyId() === m.id" (click)="saveRename(m)">Save</button>
                      <button type="button" class="a-btn sm" (click)="renaming.set('')">Cancel</button>
                    } @else {
                      <button type="button" class="a-btn sm" (click)="startRename(m)">Rename</button>
                      @if (m.invitePending && t.inviteEmailEnabled && m.active) { <button type="button" class="a-btn sm" [disabled]="busyId() === m.id" (click)="resend(m)">Resend invite</button> }
                      <button type="button" class="a-btn sm" [class.danger]="m.active" (click)="askToggle(m)">{{ m.active ? 'Deactivate' : 'Reactivate' }}</button>
                    }
                  </div>
                </article>
              } @empty {
                <div class="empty">{{ query() ? 'No one matches your search.' : (tab() === 'staff' ? 'No gate staff yet.' : 'No event managers yet.') }}</div>
              }
            </div>
          </section>

          @if (isAdmin && t.owners.length) {
            <section class="card owners">
              <div class="eyebrow">Organizer owners</div>
              @for (o of t.owners; track o.id) { <div class="own"><b>{{ o.name }}</b><small>{{ o.email }}</small><i class="chip" [class.off]="!o.active">{{ o.active ? 'Active' : 'Deactivated' }}</i></div> }
              <button type="button" class="a-btn sm" (click)="drawer.set('owners')">Add organizer owner</button>
            </section>
          }

          <section class="card pick">
            <div class="eyebrow">Assignments</div>
            <h2>Assign people to an event</h2>
            @if (!t.events.length) {
              <p>This organizer has no events yet. @if (!isAdmin) { <a routerLink="/admin/events/new">Create an event</a> } to assign staff and managers.</p>
            } @else {
              <label class="field">Event
                <select [value]="eventId()" (change)="eventId.set($any($event.target).value)" aria-label="Choose an event">
                  <option value="">Choose an event…</option>
                  @for (e of t.events; track e.id) { <option [value]="e.id">{{ e.name }}</option> }
                </select>
              </label>
            }
          </section>

          @if (eventId()) {
            <lk-event-team-panel [eventId]="eventId()" [organizerSlug]="slug()" [team]="team()" (changed)="refreshQuiet()" />
          }
        }
      }
    }

    <lk-member-drawer [open]="!!drawer()" [kind]="drawer() || 'staff'" [slug]="slug()" [organizerName]="team()?.name || ''"
      [inviteEnabled]="team()?.inviteEmailEnabled || false" (closed)="drawer.set(null)" (created)="onCreated()" />

    <lk-confirm-dialog [open]="!!toggling()" [busy]="!!busyId()" [danger]="!!toggling()?.active"
      [title]="toggling()?.active ? 'Deactivate this account?' : 'Reactivate this account?'"
      [confirmLabel]="toggling()?.active ? 'Deactivate' : 'Reactivate'"
      [message]="toggling() ? (toggling()!.active ? toggling()!.name + ' will be signed out and can no longer sign in or scan. Their event assignments are kept, so you can reactivate them later.' : toggling()!.name + ' will be able to sign in again with their existing assignments.') : ''"
      (confirmed)="confirmToggle()" (cancelled)="toggling.set(null)" />
  `,
  styles: [ADMIN_UI_STYLES, `
    .switcher{min-width:240px}.org-chip{font-weight:700;font-size:14px;background:#fff;border:1px solid var(--line);border-radius:999px;padding:10px 16px}
    .card+.card,.alert+.card{margin-top:16px}
    .first{text-align:left}.first .a-btn{margin:4px 10px 0 0}
    .tabs{display:flex;gap:6px;align-items:center;flex-wrap:wrap;border-bottom:1px solid #eee8e0;margin:-6px 0 14px;padding-bottom:12px}
    .tabs button[role=tab]{background:none;border:0;font:inherit;font-weight:700;font-size:14px;color:#8a8190;padding:10px 14px;border-radius:10px;cursor:pointer;min-height:44px}
    .tabs button[role=tab].on{background:#17121a;color:#fff}.tabs i{font-style:normal;font-size:11px;opacity:.7;margin-left:4px}
    .grow{flex:1}.search{margin-bottom:6px}
    .member{display:flex;gap:14px;align-items:flex-start;padding:14px 0;border-bottom:1px solid #f1ece5}.member.off .who b{color:#8a8190}
    .avatar{flex:none;width:42px;height:42px;border-radius:50%;background:#f1ece5;color:#5d4a1f;display:grid;place-items:center;font-weight:800;font-size:14px}
    .who{display:grid;gap:3px;min-width:0;flex:1}.who b{font-size:15px}.who small{color:#8a8190;font-size:12px;overflow-wrap:anywhere}
    .meta{display:flex;gap:6px;flex-wrap:wrap;margin-top:4px}
    .chip{font-style:normal;font-size:11px;font-weight:800;border-radius:999px;padding:4px 9px;background:#e4f2e7;color:#2f6a3d}.chip.off{background:#fde8e8;color:#8c2f2f}.chip.pending{background:#fff3d6;color:#7a5a12}
    .tag{font-style:normal;font-size:12px;background:#f4f1ec;color:#4a414d;border-radius:8px;padding:4px 8px}.tag.none{color:#a79fa9;background:transparent;padding-left:0}
    .acts{display:flex;gap:8px;flex-wrap:wrap;justify-content:flex-end}
    .owners .own{display:flex;gap:12px;align-items:center;flex-wrap:wrap;padding:8px 0}.owners small{color:#8a8190}
    .owners .a-btn{margin-top:8px}
    .pick .field{max-width:480px}
    @media(max-width:700px){
      .switcher{width:100%}.member{flex-wrap:wrap}.acts{width:100%;justify-content:flex-start}.acts .a-btn{flex:1}
      .tabs .add{width:100%}
    }
  `]
})
export class TeamComponent implements OnInit {
  readonly store = inject(AdminStore);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  get isAdmin(): boolean { return this.auth.role() === 'ADMIN'; }

  readonly slug = signal('');
  readonly team = signal<TeamView | undefined>(undefined);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly tab = signal<Tab>('staff');
  readonly query = signal('');
  readonly eventId = signal('');
  readonly drawer = signal<TeamKind | null>(null);
  readonly renaming = signal('');
  renameValue = '';
  readonly busyId = signal('');
  readonly toggling = signal<TeamMember | null>(null);

  /** ORGANIZER users with a single organizer never see a switcher; ADMIN sees every organizer. */
  readonly showSwitcher = computed(() => this.store.organizers().length > 1);

  readonly visible = computed(() => {
    const t = this.team(); if (!t) return [];
    const q = this.query().trim().toLowerCase();
    const list = this.tab() === 'staff' ? t.staff : t.managers;
    return q ? list.filter(m => m.name.toLowerCase().includes(q) || m.email.toLowerCase().includes(q)) : list;
  });

  constructor() {
    effect(() => {
      const orgs = this.store.organizers();
      if (!this.store.organizersLoaded() || !orgs.length || this.slug()) return;
      const wanted = this.route.snapshot.queryParamMap.get('org');
      const pick = orgs.find(o => o.slug === wanted) ?? orgs[0];
      untracked(() => { this.slug.set(pick.slug); this.loadTeam(); });
    });
  }

  ngOnInit(): void { this.store.load(); this.store.loadOrganizers(); }

  pickOrganizer(slug: string): void {
    if (!slug || slug === this.slug()) return;
    this.slug.set(slug); this.eventId.set(''); this.query.set(''); this.team.set(undefined);
    this.router.navigate([], { relativeTo: this.route, queryParams: { org: slug }, queryParamsHandling: 'merge', replaceUrl: true });
    this.loadTeam();
  }

  loadTeam(quiet = false): void {
    const slug = this.slug(); if (!slug) return;
    if (!quiet) this.loading.set(true);
    this.error.set('');
    this.api.orgTeam(slug).subscribe({
      next: t => { if (this.slug() === slug) { this.team.set(t); this.loading.set(false); } },
      error: e => { this.loading.set(false); this.error.set(e?.error?.message || 'The team could not be loaded.'); }
    });
  }
  refreshQuiet(): void { this.loadTeam(true); }

  setTab(t: Tab): void { this.tab.set(t); this.query.set(''); }
  initials(name: string): string { return name.split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]!.toUpperCase()).join('') || '?'; }

  onCreated(): void {
    const kind = this.drawer();
    this.drawer.set(null);
    if (kind === 'staff') this.tab.set('staff'); else if (kind === 'managers') this.tab.set('managers');
    this.loadTeam(true);
  }

  startRename(m: TeamMember): void { this.renameValue = m.name; this.renaming.set(m.id); }
  saveRename(m: TeamMember): void {
    const name = this.renameValue.trim();
    if (name.length < 2 || name.length > 120) { this.toast.error('Enter a name between 2 and 120 characters.'); return; }
    if (name === m.name) { this.renaming.set(''); return; }
    this.busyId.set(m.id);
    this.api.patchTeamMember(this.slug(), m.id, { name }).subscribe({
      next: () => { this.busyId.set(''); this.renaming.set(''); this.toast.success('Name updated.'); this.loadTeam(true); },
      error: e => { this.busyId.set(''); this.toast.error(e?.error?.message || 'The name could not be updated.'); }
    });
  }

  resend(m: TeamMember): void {
    this.busyId.set(m.id);
    this.api.resendTeamInvite(this.slug(), m.id).subscribe({
      next: r => {
        this.busyId.set('');
        if (r.delivery === 'INVITE_SENT') this.toast.success(`A new invite was sent to ${m.email}. Earlier links no longer work.`);
        else this.toast.error('The invite email could not be sent. Please try again shortly.');
        this.loadTeam(true);
      },
      error: e => { this.busyId.set(''); this.toast.error(e?.error?.message || 'The invite could not be sent.'); }
    });
  }

  askToggle(m: TeamMember): void { this.toggling.set(m); }
  confirmToggle(): void {
    const m = this.toggling(); if (!m) return;
    this.busyId.set(m.id);
    this.api.patchTeamMember(this.slug(), m.id, { active: !m.active }).subscribe({
      next: () => {
        this.busyId.set(''); this.toggling.set(null);
        this.toast.success(m.active ? `${m.name} was deactivated and signed out.` : `${m.name} was reactivated.`);
        this.loadTeam(true);
      },
      error: e => { this.busyId.set(''); this.toggling.set(null); this.toast.error(e?.error?.message || 'The change could not be saved.'); }
    });
  }
}
