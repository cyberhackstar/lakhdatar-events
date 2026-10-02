import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';

@Component({
  selector: 'lk-initial-admin',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="setup-shell">
      <section class="setup-card" *ngIf="!loading && !completed; else state">
        <div class="eyebrow">Neelastack Events · Secure setup</div>
        <h1>Create the first administrator.</h1>
        <p class="muted">This is a one-time production provisioning flow. It creates the platform administrator and the first organizer owner. No sample event is created.</p>
        <div class="notice" *ngIf="!enabled">Initial administrator setup is currently disabled by the server.</div>
        <form [formGroup]="form" (ngSubmit)="submit()" *ngIf="enabled" novalidate>
          <label>Setup token<input formControlName="token" type="password" autocomplete="off" /></label>
          <label>Administrator name<input formControlName="name" autocomplete="name" /></label>
          <label>Email<input formControlName="email" type="email" autocomplete="email" /></label>
          <label>Password<input formControlName="password" type="password" autocomplete="new-password" /></label>
          <label>Organizer name<input formControlName="organizerName" autocomplete="organization" /></label>
          <label>Organizer slug<input formControlName="organizerSlug" autocomplete="off" placeholder="lakhdatar-events" /></label>
          <div class="error" *ngIf="error" role="alert">{{ error }}</div>
          <button type="submit" [disabled]="busy || form.invalid">{{ busy ? 'Creating securely…' : 'Create administrator' }}</button>
        </form>
        <a routerLink="/login" class="back">Back to login</a>
      </section>
      <ng-template #state>
        <section class="setup-card">
          <div class="eyebrow">Neelastack Events</div>
          <h1>{{ completed ? 'Setup is already complete.' : 'Checking secure setup…' }}</h1>
          <p class="muted" *ngIf="completed">The initial administrator flow is permanently closed for this installation.</p>
          <a *ngIf="completed" routerLink="/login" class="primary">Continue to login</a>
        </section>
      </ng-template>
    </main>
  `,
  styles: [`
    :host{display:block;min-height:100vh}.setup-shell{min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(circle at 50% 20%,rgba(212,166,78,.10),transparent 38%),#08070b}.setup-card{width:min(560px,100%);padding:34px;border:1px solid rgba(255,255,255,.10);border-radius:26px;background:rgba(18,15,23,.96);box-shadow:0 40px 120px rgba(0,0,0,.45)}.eyebrow{text-transform:uppercase;letter-spacing:.18em;font-size:10px;color:#d4a64e;font-weight:800}.setup-card h1{font-size:clamp(38px,7vw,60px);line-height:.95;letter-spacing:-.05em;margin:14px 0}.muted{color:#9d95a1;line-height:1.6;font-size:14px}.notice,.error{padding:13px 15px;border-radius:12px;margin:16px 0;font-size:12px;line-height:1.5}.notice{background:rgba(212,166,78,.08);border:1px solid rgba(212,166,78,.2);color:#d9bf82}.error{background:rgba(239,107,107,.08);border:1px solid rgba(239,107,107,.2);color:#f1a1a1}form{display:grid;gap:14px;margin-top:24px}label{display:grid;gap:7px;color:#cfc8d2;font-size:12px;font-weight:700}input{width:100%;min-height:48px;border:1px solid rgba(255,255,255,.11);border-radius:12px;background:#0d0b11;color:#fff;padding:0 14px;font-size:16px}button,.primary{min-height:50px;border:0;border-radius:999px;background:#f0cf8c;color:#17100d;font-weight:800;cursor:pointer;text-align:center;padding:14px 20px;text-decoration:none}button:disabled{opacity:.45;cursor:not-allowed}.back{display:block;margin-top:20px;color:#8f8794;text-decoration:none;font-size:12px}.primary{display:inline-block;margin-top:18px}
  `]
})
export class InitialAdminComponent {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  form = this.fb.nonNullable.group({ token:['', Validators.required], name:['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]], email:['', [Validators.required, Validators.email]], password:['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]], organizerName:['Lakhdatar Events', [Validators.required, Validators.minLength(2), Validators.maxLength(255)]], organizerSlug:['lakhdatar-events', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+){0,80}$/)]] });
  loading = true; enabled = false; completed = false; busy = false; error = '';

  constructor(){
    this.api.initialAdminSetupStatus().subscribe({next:s=>{this.enabled=s.enabled;this.completed=s.completed;this.loading=false;},error:()=>{this.error='Secure setup status could not be loaded.';this.loading=false;}});
  }
  submit():void{
    if(this.form.invalid){this.form.markAllAsTouched();return;}
    this.busy=true;this.error=''; const v=this.form.getRawValue();
    this.api.createInitialAdmin({email:v.email,name:v.name,password:v.password,organizerName:v.organizerName,organizerSlug:v.organizerSlug},v.token).subscribe({next:()=>this.router.navigateByUrl('/login?setup=complete',{replaceUrl:true}),error:e=>{this.busy=false;this.error=e?.error?.message||'Administrator setup could not be completed.';}});
  }
}
