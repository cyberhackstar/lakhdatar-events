import { CommonModule } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { AdminOrganizer } from '../../core/api/api.models';
import { safeImage } from '../../core/format';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * Organizers (event companies). The organizer's name and logo are entered here when the organizer is created;
 * the logo is uploaded to Cloudinary by the backend and only the returned HTTPS URL is stored.
 * Nothing about an organizer comes from environment variables or files bundled with the application.
 */
@Component({
  selector: 'lk-organizers',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="org-page">
      <a routerLink="/admin" class="back">← Back to operations console</a>
      <div class="eyebrow">Neelastack Events · Organizers</div>
      <h1>Organizers</h1>
      <p class="intro">Each organizer is an event company whose events are sold on this platform. Add the organizer's name and logo here; the logo is stored securely on Cloudinary.</p>

      <div class="notice" *ngIf="loaded && !mediaStorageConfigured" role="status">
        Image storage is not configured on the server (Cloudinary credentials are missing), so logos cannot be uploaded yet.
        You can still create the organizer now and add its logo later.
      </div>
      <div class="error" *ngIf="loadError" role="alert">{{ loadError }} <button type="button" (click)="load()">Retry</button></div>

      <section class="list" *ngIf="loaded">
        <article *ngFor="let o of organizers">
          <div class="logo-slot">
            <img *ngIf="logo(o) as l; else initial" [src]="l" [alt]="o.name + ' logo'" />
            <ng-template #initial><span aria-hidden="true">{{ o.name.charAt(0).toUpperCase() }}</span></ng-template>
          </div>
          <div class="meta">
            <strong>{{ o.name }}</strong>
            <small>{{ o.slug }}<ng-container *ngIf="!logo(o)"> · no logo yet</ng-container></small>
          </div>
          <button type="button" class="small" *ngIf="canCreate && mediaStorageConfigured" [disabled]="replacingSlug === o.slug" (click)="chooseReplacement(o)">
            {{ replacingSlug === o.slug ? 'Uploading…' : (logo(o) ? 'Replace logo' : 'Add logo') }}
          </button>
        </article>
        <div class="empty" *ngIf="!organizers.length">No organizers yet.</div>
      </section>
      <input #replaceInput type="file" accept="image/png,image/jpeg" hidden (change)="replacementSelected($event)" />
      <div class="success" *ngIf="message" role="status">{{ message }}</div>

      <section class="create" *ngIf="loaded && canCreate">
        <h2>Add an organizer</h2>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
          <label>Organizer name
            <input formControlName="name" autocomplete="off" placeholder="Your event company" (input)="onNameInput()" />
          </label>
          <label>URL slug
            <input formControlName="slug" autocomplete="off" autocapitalize="none" placeholder="your-event-company" (input)="slugTouched = true" />
            <small>Lowercase letters, numbers and hyphens. Used in links such as /events?organizer=your-event-company.</small>
          </label>
          <label>About (optional)
            <textarea formControlName="description" rows="3" placeholder="A short description shown on the organizer's event pages"></textarea>
          </label>
          <label>Website (optional)
            <input formControlName="website" type="url" autocomplete="off" placeholder="https://" />
          </label>
          <div class="logo-pick">
            <div class="logo-slot big">
              <img *ngIf="previewUrl" [src]="previewUrl" alt="Selected logo preview" />
              <span *ngIf="!previewUrl" aria-hidden="true">Logo</span>
            </div>
            <div>
              <label class="file-btn" [class.disabled]="!mediaStorageConfigured">
                {{ logoFile ? 'Choose a different image' : 'Choose logo (PNG or JPEG)' }}
                <input type="file" accept="image/png,image/jpeg" hidden [disabled]="!mediaStorageConfigured" (change)="logoSelected($event)" />
              </label>
              <small>Up to 5 MB. A square or wide PNG with a transparent background works best.</small>
              <button type="button" class="link" *ngIf="logoFile" (click)="clearLogo()">Remove selected logo</button>
            </div>
          </div>
          <div class="error" *ngIf="error" role="alert">{{ error }}</div>
          <button type="submit" class="primary" [disabled]="busy || form.invalid">{{ busy ? 'Creating…' : 'Create organizer' }}</button>
        </form>
      </section>
      <p class="muted" *ngIf="loaded && !canCreate">Only platform administrators can create organizers.</p>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh;background:#f4f1ec;color:#1a151b}
    .org-page{max-width:860px;margin:0 auto;padding:28px 20px 80px}
    .back{display:inline-block;margin-bottom:22px;color:#6b6270;text-decoration:none;font-size:13px}
    .eyebrow{text-transform:uppercase;letter-spacing:.16em;font-size:10px;font-weight:800;color:#9a7424}
    h1{font-size:clamp(34px,6vw,52px);letter-spacing:-.04em;margin:8px 0 10px}
    h2{font-size:22px;margin:0 0 16px}
    .intro,.muted{color:#6b6270;line-height:1.6;font-size:14px;max-width:640px}
    .notice,.error,.success{padding:12px 14px;border-radius:12px;margin:16px 0;font-size:13px;line-height:1.5}
    .notice{background:#fff7e0;border:1px solid #ecd59a;color:#6d5311}
    .error{background:#fdeeee;border:1px solid #efb9b9;color:#9b2c2c}
    .success{background:#eaf6ee;border:1px solid #b8dcc3;color:#23623a}
    .error button{margin-left:8px}
    .list{display:grid;gap:12px;margin:22px 0}
    .list article{display:flex;align-items:center;gap:14px;background:#fff;border:1px solid #e6e0d8;border-radius:16px;padding:14px 16px}
    .meta{flex:1;min-width:0}.meta strong{display:block;font-size:16px}.meta small{color:#8a8190;font-size:12px}
    .logo-slot{width:56px;height:56px;border-radius:14px;background:#1a151b;color:#f0cf8c;display:grid;place-items:center;overflow:hidden;font-weight:800;flex:none}
    .logo-slot img{width:100%;height:100%;object-fit:contain;padding:6px;box-sizing:border-box}
    .logo-slot.big{width:96px;height:96px;font-size:13px;letter-spacing:.1em;text-transform:uppercase;color:#8a8190;background:#ece7df;border:1px dashed #cfc7bb}
    .empty{padding:18px;color:#8a8190;text-align:center}
    .create{background:#fff;border:1px solid #e6e0d8;border-radius:20px;padding:24px;margin-top:28px}
    form{display:grid;gap:16px}
    label{display:grid;gap:6px;font-size:12px;font-weight:700;color:#3b333f}
    label small,.logo-pick small{font-weight:400;color:#8a8190;font-size:12px;line-height:1.4}
    input:not([type=file]),textarea{width:100%;box-sizing:border-box;min-height:46px;border:1px solid #d9d2c8;border-radius:12px;padding:10px 14px;font:inherit;font-size:16px;background:#fdfcfa;color:#1a151b}
    textarea{resize:vertical}
    .logo-pick{display:flex;gap:18px;align-items:center;flex-wrap:wrap}
    .file-btn{display:inline-block;padding:11px 18px;border-radius:999px;border:1px solid #1a151b;font-size:13px;font-weight:700;cursor:pointer;margin-bottom:6px}
    .file-btn.disabled{opacity:.4;cursor:not-allowed}
    .small{border:1px solid #1a151b;background:transparent;border-radius:999px;padding:9px 16px;font-size:12px;font-weight:700;cursor:pointer}
    .small:disabled{opacity:.5;cursor:wait}
    .link{display:block;background:none;border:0;color:#9b2c2c;font-size:12px;cursor:pointer;padding:4px 0}
    .primary{min-height:50px;border:0;border-radius:999px;background:#1a151b;color:#f0cf8c;font-weight:800;font-size:15px;cursor:pointer}
    .primary:disabled{opacity:.45;cursor:not-allowed}
  `]
})
export class OrganizersComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly fb = inject(FormBuilder);

  organizers: AdminOrganizer[] = [];
  loaded = false; loadError = ''; mediaStorageConfigured = false;
  busy = false; error = ''; message = '';
  slugTouched = false;
  logoFile: File | null = null; previewUrl = '';
  replacingSlug = ''; private replaceTarget?: AdminOrganizer;

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(255)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+){0,80}$/)]],
    description: ['', Validators.maxLength(5000)],
    website: ['', [Validators.maxLength(255), Validators.pattern(/^(https:\/\/\S+)?$/)]]
  });

  get canCreate(): boolean { return this.auth.role() === 'ADMIN'; }

  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void { this.revokePreview(); }

  logo(o: AdminOrganizer): string | null { return safeImage(o.logoUrl); }

  load(): void {
    this.loadError = '';
    this.api.listOrganizers().subscribe({
      next: r => { this.organizers = r.organizers; this.mediaStorageConfigured = r.mediaStorageConfigured; this.loaded = true; },
      error: e => { this.loaded = true; this.loadError = e?.error?.message || 'Organizers could not be loaded.'; }
    });
  }

  onNameInput(): void {
    if (this.slugTouched) return;
    const slug = this.form.controls.name.value.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80);
    this.form.controls.slug.setValue(slug);
  }

  logoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const problem = this.validateImage(file);
    if (problem) { this.error = problem; this.message = ''; return; }
    this.error = ''; this.message = '';
    this.revokePreview();
    this.logoFile = file;
    this.previewUrl = URL.createObjectURL(file);
  }

  clearLogo(): void { this.revokePreview(); this.logoFile = null; this.previewUrl = ''; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.busy = true; this.error = ''; this.message = '';
    const v = this.form.getRawValue();
    this.api.createOrganizer({ name: v.name.trim(), slug: v.slug.trim(), description: v.description.trim(), website: v.website.trim() }, this.logoFile).subscribe({
      next: o => {
        this.busy = false;
        this.message = `Organizer “${o.name}” created${o.logoUrl ? ' with its logo' : ''}. You can now choose it when creating an event.`;
        this.form.reset({ name: '', slug: '', description: '', website: '' });
        this.slugTouched = false; this.clearLogo(); this.load();
      },
      error: e => { this.busy = false; this.error = e?.error?.message || 'The organizer could not be created.'; }
    });
  }

  @ViewChild('replaceInput') private replaceInput?: ElementRef<HTMLInputElement>;

  chooseReplacement(o: AdminOrganizer): void {
    this.replaceTarget = o;
    this.replaceInput?.nativeElement.click();
  }

  replacementSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const target = this.replaceTarget;
    if (!file || !target) return;
    const problem = this.validateImage(file);
    if (problem) { this.error = problem; return; }
    this.error = ''; this.message = ''; this.replacingSlug = target.slug;
    this.api.uploadAdminAsset(file, 'ORGANIZER_LOGO', undefined, target.slug).subscribe({
      next: () => { this.replacingSlug = ''; this.message = `Logo updated for “${target.name}”.`; this.load(); },
      error: e => { this.replacingSlug = ''; this.error = e?.error?.message || 'Logo upload failed.'; }
    });
  }

  private validateImage(file: File): string {
    if (!['image/png', 'image/jpeg'].includes(file.type)) return 'Only PNG and JPEG images are supported.';
    if (file.size > MAX_IMAGE_BYTES) return 'Image must be 5 MB or smaller.';
    return '';
  }

  private revokePreview(): void { if (this.previewUrl) URL.revokeObjectURL(this.previewUrl); }
}
