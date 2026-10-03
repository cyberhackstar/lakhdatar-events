import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../../core/api/api.service';
import { AdminOrganizer, Dashboard } from '../../core/api/api.models';
import { AuthService } from '../../core/auth/auth.service';

/** One shared copy of the dashboard + organizer list so every admin page sees the same data and refreshes it together. */
@Injectable({ providedIn: 'root' })
export class AdminStore {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);

  readonly dash = signal<Dashboard | undefined>(undefined);
  readonly loading = signal(false);
  readonly error = signal('');

  readonly organizers = signal<AdminOrganizer[]>([]);
  readonly mediaStorageConfigured = signal(false);
  readonly organizersLoaded = signal(false);
  readonly organizersError = signal('');

  get role(): string { return this.auth.role() || ''; }
  get canAdministerEvents(): boolean { return this.role === 'ADMIN' || this.role === 'ORGANIZER'; }

  /** Loads the dashboard once; pass force=true to refresh. */
  load(force = false): void {
    if (this.loading() || (!force && this.dash())) return;
    this.loading.set(true); this.error.set('');
    this.api.dashboard().subscribe({
      next: d => { this.dash.set(d); this.loading.set(false); },
      error: e => { this.loading.set(false); this.error.set(e?.error?.message || 'Operations data could not be loaded. Please retry.'); }
    });
    if (this.canAdministerEvents) this.loadOrganizers(force);
  }

  loadOrganizers(force = false): void {
    if (!force && this.organizersLoaded()) return;
    this.organizersError.set('');
    this.api.listOrganizers().subscribe({
      next: r => { this.organizers.set(r.organizers); this.mediaStorageConfigured.set(r.mediaStorageConfigured); this.organizersLoaded.set(true); },
      error: e => { this.organizersLoaded.set(true); this.organizersError.set(e?.error?.message || 'Organizers could not be loaded.'); }
    });
  }

  /** Clears cached data (used on sign-out so the next user never sees stale rows). */
  reset(): void {
    this.dash.set(undefined); this.error.set(''); this.loading.set(false);
    this.organizers.set([]); this.organizersLoaded.set(false); this.organizersError.set('');
  }
}
