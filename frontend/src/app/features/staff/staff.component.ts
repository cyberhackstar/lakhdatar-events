import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { StaffEvent } from '../../core/api/api.models';
import { BrandMarkComponent } from '../../shared/brand-mark.component';

@Component({selector:'lk-staff',standalone:true,imports:[CommonModule,DatePipe,RouterLink,BrandMarkComponent],template:`
<div class="staff-page"><header><a routerLink="/" class="brand" aria-label="Neelastack Events home"><lk-brand-mark label="Events" [height]="30"></lk-brand-mark></a><div class="staff-user"><span>{{auth.fullName()}}</span><button type="button" (click)="logout()">Sign out</button></div></header><main><div class="eyebrow">Event-day operations</div><h1>Choose your gate.</h1><p class="intro">Use your phone camera to validate tickets instantly. Keep this screen ready before the doors open.</p><div class="loading-state" *ngIf="loading">Loading your assigned gates…</div><div class="error-state" *ngIf="error" role="alert">{{error}}<button type="button" (click)="retry()">Retry</button></div><div class="event-list" *ngIf="!loading && !error"><article *ngFor="let e of events"><div><div class="date-chip">{{e.startsAt | date:'d MMM'}}</div><div><h2>{{e.name}}</h2><p>{{e.organizerName}} · {{e.gate}} · {{e.startsAt | date:'h:mm a'}}</p></div></div><a [routerLink]="['/staff/events',e.id,'scanner']" [queryParams]="{gate:e.gate,eventName:e.name}">Open scanner <span>→</span></a></article><div class="empty" *ngIf="!events.length">No event gates are assigned to this account yet.</div></div><div class="tip"><span>✓</span><div><strong>Scanner rule</strong><p>Entry is accepted only after the server confirms the ticket. A connection failure is never treated as a valid scan.</p></div></div></main><footer>Powered by <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Neelastack ↗</a></footer></div>
`,styles:[`.staff-page{min-height:100vh;background:#09080c;color:#fff}.staff-page header{height:76px;border-bottom:1px solid rgba(255,255,255,.07);display:flex;align-items:center;justify-content:space-between;padding:0 22px}.brand{color:#fff;text-decoration:none;display:flex;align-items:center;gap:11px}.brand lk-brand-mark{font-size:17px}.brand span{color:#77707c;font-size:8px;letter-spacing:.15em;font-weight:800}.staff-user{display:flex;gap:17px;align-items:center}.staff-user span{font-size:11px;color:#918893}.staff-user button{border:1px solid rgba(255,255,255,.08);background:transparent;color:#b1a8b5;font-size:10px;cursor:pointer;border-radius:999px;padding:8px 11px}.loading-state,.error-state,.empty{margin-top:36px;padding:22px;border:1px solid rgba(255,255,255,.08);border-radius:18px;background:#141118;color:#857c89;font-size:11px}.error-state{color:#d8a1a1;border-color:rgba(216,161,161,.15)}.error-state button{display:block;margin:12px auto 0;border:1px solid rgba(255,255,255,.1);background:#fff;color:#17121a;border-radius:999px;padding:9px 13px;font-size:10px;font-weight:800;cursor:pointer}.staff-page main{max-width:900px;margin:0 auto;padding:75px 20px}.eyebrow{text-transform:uppercase;letter-spacing:.15em;color:#8c8491;font-size:10px;font-weight:800}.staff-page h1{font-size:65px;letter-spacing:-.06em;line-height:.95;margin:15px 0}.intro{color:#817985;max-width:570px;font-size:14px;line-height:1.65}.event-list{display:grid;gap:12px;margin-top:45px}.event-list article{display:flex;justify-content:space-between;gap:25px;align-items:center;padding:21px 22px;background:#141118;border:1px solid rgba(255,255,255,.08);border-radius:19px}.event-list article>div{display:flex;align-items:center;gap:17px}.date-chip{display:grid;place-items:center;width:52px;height:52px;border-radius:14px;background:#211a13;color:#d5a84e;font-size:11px;font-weight:800}.event-list h2{font-size:17px;margin:0 0 4px}.event-list p{font-size:10px;color:#756c78;margin:0}.event-list a{background:#fff;color:#19141b;text-decoration:none;border-radius:999px;padding:12px 14px;font-size:10px;font-weight:800;white-space:nowrap}.event-list a span{margin-left:7px;font-size:15px}.tip{display:flex;gap:12px;margin-top:18px;border-top:1px solid rgba(255,255,255,.07);padding-top:22px}.tip>span{width:30px;height:30px;border-radius:9px;background:#17301f;color:#8fd4a4;display:grid;place-items:center;font-size:14px}.tip strong{font-size:11px}.tip p{margin:4px 0 0;color:#716977;font-size:10px;line-height:1.5}.staff-page footer{text-align:center;color:#625b66;font-size:9px;padding:18px}.staff-page footer a{color:#8b7f91;text-decoration:none}@media(max-width:600px){.staff-page h1{font-size:48px}.event-list article{align-items:flex-start;flex-direction:column}.event-list a{width:100%;text-align:center}.staff-user span{display:none}}
`]
})
export class StaffComponent implements OnInit {
  readonly auth=inject(AuthService);
  private readonly api=inject(ApiService);
  events:StaffEvent[]=[];
  loading=true;
  error='';

  ngOnInit():void {
    this.loading=true;
    this.error='';
    this.api.staffEvents().subscribe({
      next:events=>{this.events=events;this.loading=false;},
      error:error=>{
        this.loading=false;
        this.error=error?.error?.message || 'We could not load your assigned gates. Please retry.';
      }
    });
  }

  retry():void { this.ngOnInit(); }

  logout():void {
    this.auth.logout().subscribe({complete:()=>location.href='/login',error:()=>location.href='/login'});
  }
}
