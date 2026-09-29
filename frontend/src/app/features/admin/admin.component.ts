import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api/api.service';
import { AuthService } from '../../core/auth/auth.service';
import { Dashboard, EventManagerView, ManagerTicketType, ManagerTicketIssueResponse } from '../../core/api/api.models';
import { EventEditorComponent } from './event-editor.component';

@Component({
  selector: 'lk-admin',
  standalone: true,
  imports: [CommonModule, DatePipe, DecimalPipe, RouterLink, ReactiveFormsModule, EventEditorComponent],
  template: `
    <div class="admin-shell">
      <aside>
        <div class="brand"><img src="/assets/neelastack-logo.svg" alt="Neelastack"/><span>OPERATIONS<br><i>CONSOLE</i></span></div>
        <div class="role">{{canAdministerEvents()?"CONTROL CENTER":"ASSIGNED EVENTS ONLY"}}</div>
        <nav>
          <a class="active" href="#overview">Overview</a>
          <a href="#events">Events</a>
          <a href="#event-day">Event day</a>
          <a href="#neelastack">Technology</a>
          <a routerLink="/staff">Scanner console</a>
        </nav>
        <div class="aside-bottom">
          <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Powered by Neelastack ↗</a>
          <button type="button" (click)="logout()">Sign out</button>
        </div>
      </aside>

      <main id="overview">
        <header>
          <div>
            <div class="eyebrow">Operations control</div>
            <h1>Good evening, {{ firstName }}.</h1>
            <p class="header-copy">{{canAdministerEvents()?"One workspace for event inventory, revenue, staff access and event-day entry.":"Operate only the events explicitly assigned to your manager account."}}</p>
          </div>
          <div class="header-actions">
            <span class="header-pill">LIVE SYSTEM <i></i></span>
            <button type="button" class="refresh" (click)="load()" [disabled]="loading">↻ Refresh</button>
          </div>
        </header>

        <div class="dashboard-error" *ngIf="loadError" role="alert"><span>{{loadError}}</span><button type="button" (click)="load()">Retry</button></div>
        <div class="dashboard-loading" *ngIf="loading">Loading operations data…</div>

        <ng-container *ngIf="!loading && !loadError">
          <section class="metrics">
            <article><span>Gross revenue</span><strong>₹{{(dash?.totalRevenueMinor||0)/100 | number:'1.0-0'}}</strong><small>Successful payments</small></article>
            <article><span>Tickets sold</span><strong>{{dash?.totalSold || 0}}</strong><small>Across managed events</small></article>
            <article><span>Checked in</span><strong>{{dash?.totalCheckedIn || 0}}</strong><small>Verified entries</small></article>
            <article><span>Events</span><strong>{{dash?.events?.length || 0}}</strong><small>In your workspace</small></article>
          </section>

          <section class="panel" id="events">
            <div class="panel-head">
              <div><div class="eyebrow">Event portfolio</div><h2>Recent events</h2></div>
              <button class="panel-action" *ngIf="canAdministerEvents()" type="button" (click)="showCreate=!showCreate">{{showCreate?'Close':'Create event'}} <span>{{showCreate?'×':'+'}}</span></button>
            </div>
            <div class="event-table desktop-events" *ngIf="dash?.events?.length" aria-label="Event portfolio desktop table">
              <div class="table-row head"><span>Event</span><span>Status</span><span>Sold</span><span>Check-ins</span><span>Revenue</span><span>Tools</span></div>
              <div class="table-row" *ngFor="let e of dash?.events">
                <span><strong>{{e.name}}</strong><small>{{e.startsAt | date:'d MMM yyyy · h:mm a'}}</small></span>
                <span><b class="status" [class.published]="e.status==='PUBLISHED'">{{e.status}}</b><button class="publish-mini" *ngIf="canAdministerEvents() && e.status==='DRAFT' && publishingId!==e.id" type="button" (click)="publish(e.id)">Publish</button><span class="publishing" *ngIf="publishingId===e.id">Publishing…</span></span>
                <span>{{e.ticketsSold}}</span><span>{{e.ticketsCheckedIn}}</span><span>₹{{e.revenueMinor/100 | number:'1.0-0'}}</span>
                <span class="tools"><a [routerLink]="['/events',e.slug]" target="_blank" rel="noopener">Open</a><button type="button" *ngIf="canAdministerEvents()" (click)="editEvent(e.id)">Edit</button><button type="button" (click)="downloadCsv(e)">CSV</button></span>
              </div>
            </div>

            <div class="event-mobile-list" *ngIf="dash?.events?.length" aria-label="Event portfolio mobile cards">
              <article class="event-mobile-card" *ngFor="let e of dash?.events">
                <div class="event-mobile-top">
                  <div class="event-mobile-title">
                    <strong>{{e.name}}</strong>
                    <small>{{e.startsAt | date:'d MMM yyyy · h:mm a'}}</small>
                  </div>
                  <b class="status" [class.published]="e.status==='PUBLISHED'">{{e.status}}</b>
                </div>
                <div class="event-mobile-metrics">
                  <div><span>Sold</span><strong>{{e.ticketsSold}}</strong></div>
                  <div><span>Check-ins</span><strong>{{e.ticketsCheckedIn}}</strong></div>
                  <div><span>Revenue</span><strong>₹{{e.revenueMinor/100 | number:'1.0-0'}}</strong></div>
                </div>
                <div class="event-mobile-actions">
                  <a [routerLink]="['/events',e.slug]" target="_blank" rel="noopener">Open</a>
                  <button type="button" *ngIf="canAdministerEvents()" (click)="editEvent(e.id)">Edit</button>
                  <button type="button" (click)="downloadCsv(e)">CSV</button>
                  <button type="button" class="publish-mobile" *ngIf="canAdministerEvents() && e.status==='DRAFT'" (click)="publish(e.id)" [disabled]="publishingId===e.id">{{publishingId===e.id?'Publishing…':'Publish'}}</button>
                </div>
              </article>
            </div>
            <div class="empty" *ngIf="!dash?.events?.length">No events yet. Create your first event to start selling tickets.</div>

            <form class="create-form" *ngIf="showCreate" [formGroup]="createForm" (ngSubmit)="createEvent()">
              <div class="form-title"><div class="eyebrow">New event</div><h3>Create an event</h3><p>Build the event shell first; publishing it makes it public.</p></div>
              <div class="form-grid">
                <label>Name<input formControlName="name" placeholder="Dandiya Night 2026" autocomplete="off"/></label>
                <label>URL slug<input formControlName="slug" placeholder="dandiya-night-2026" autocomplete="off" autocapitalize="none"/></label>
                <label>Starts<input type="datetime-local" formControlName="startsAt"/></label>
                <label>Ends (optional)<input type="datetime-local" formControlName="endsAt"/></label>
                <label>Venue<input formControlName="venueName" placeholder="Venue name"/></label>
                <label>City<input formControlName="city" placeholder="Jaipur"/></label>
                <label class="wide">Venue address<input formControlName="venueAddress" placeholder="Full venue address"/></label>
                <label class="wide">Description<textarea formControlName="description" placeholder="What guests should know about the event"></textarea></label>
              </div>
              <div class="ticket-heading"><span>Ticket inventory</span><small>Prices are in INR</small></div>
              <div formArrayName="ticketTypes" class="ticket-form-list">
                <div class="ticket-form-row" *ngFor="let group of ticketForms.controls; let i=index" [formGroupName]="i">
                  <input formControlName="name" placeholder="Ticket type" aria-label="Ticket type"/>
                  <input formControlName="priceRupees" type="number" min="1" step="1" placeholder="₹ Price" aria-label="Ticket price in rupees"/>
                  <input formControlName="totalQuantity" type="number" min="1" placeholder="Qty" aria-label="Ticket quantity"/>
                  <input formControlName="minPerOrder" type="number" min="1" max="20" placeholder="Min" aria-label="Minimum per order"/>
                  <input formControlName="maxPerOrder" type="number" min="1" max="20" placeholder="Max" aria-label="Maximum per order"/>
                  <button type="button" (click)="removeTicket(i)" *ngIf="ticketForms.length>1" aria-label="Remove ticket type">×</button>
                </div>
              </div>
              <button type="button" class="secondary-action" (click)="addTicket()">+ Add ticket type</button>
              <div class="create-error" *ngIf="createError" role="alert">{{createError}}</div>
              <button class="create-submit" type="submit" [disabled]="createForm.invalid || creating">{{creating?'Creating…':'Create draft event'}} <span>→</span></button>
            </form>

            <lk-event-editor *ngIf="editingEventId" [eventId]="editingEventId" (saved)="onEventEditorSaved()" (closed)="closeEventEditor()"></lk-event-editor>
          </section>

          <section class="operations-grid" id="event-day">
            <article class="op-card" *ngIf="canCreateStaff()">
              <div class="eyebrow">Event-day access</div><h2>Provision staff.</h2>
              <p>Create a dedicated check-in account, then assign it to an event and gate.</p>
              <form [formGroup]="staffForm" (ngSubmit)="createStaff()">
                <label>Email<input formControlName="email" type="email" placeholder="gate@example.com" autocomplete="off"/></label>
                <label>Name<input formControlName="name" placeholder="Gate operator" autocomplete="name"/></label>
                <label>Password<input formControlName="password" type="password" placeholder="12+ character password" autocomplete="new-password"/></label>
                <button type="submit" [disabled]="staffForm.invalid || staffBusy">{{staffBusy?'Creating…':'Create staff account'}} <span>→</span></button>
              </form>
              <div class="op-message success" *ngIf="staffMessage">{{staffMessage}}</div>
              <div class="op-message error" *ngIf="staffError">{{staffError}}</div>
            </article>
            <article class="op-card" *ngIf="canAssignStaff()">
              <div class="eyebrow">Gate assignment</div><h2>Attach a staff member.</h2>
              <p>Only an account with STAFF role can be assigned to an event gate.</p>
              <form [formGroup]="assignmentForm" (ngSubmit)="assignStaff()">
                <label>Event<select formControlName="eventId"><option value="">Choose an event</option><option *ngFor="let e of dash?.events" [value]="e.id">{{e.name}}</option></select></label>
                <label>Staff email<input formControlName="email" type="email" placeholder="gate@example.com" autocomplete="off"/></label>
                <label>Gate<input formControlName="gate" placeholder="Main Gate" autocomplete="off"/></label>
                <button type="submit" [disabled]="assignmentForm.invalid || assignmentBusy">{{assignmentBusy?'Assigning…':'Assign gate'}} <span>→</span></button>
              </form>
              <div class="op-message success" *ngIf="assignmentMessage">{{assignmentMessage}}</div>
              <div class="op-message error" *ngIf="assignmentError">{{assignmentError}}</div>
            </article>

            <article class="op-card manager-issue" *ngIf="isEventManager()">
              <div class="eyebrow">Complimentary access</div><h2>Issue manager tickets.</h2>
              <p>Free tickets consume real inventory and are permanently marked with the issuing manager. You can issue only tickets for events assigned to you.</p>
              <form [formGroup]="managerTicketForm" (ngSubmit)="issueManagerTickets()">
                <label>Event<select formControlName="eventId" (change)="onManagerEventChange($any($event.target).value)"><option value="">Choose your event</option><option *ngFor="let e of dash?.events" [value]="e.id">{{e.name}}</option></select></label>
                <label>Ticket type<select formControlName="ticketTypeId"><option value="">Choose a ticket type</option><option *ngFor="let t of managerTicketTypes" [value]="t.id">{{t.name}} · {{t.availableQuantity}} left</option></select></label>
                <label>Quantity<input formControlName="quantity" type="number" min="1" max="100" inputmode="numeric"/></label>
                <label>Attendee name<input formControlName="attendeeName" placeholder="Guest full name" autocomplete="name"/></label>
                <label>Email<input formControlName="attendeeEmail" type="email" placeholder="guest@example.com" autocomplete="email"/></label>
                <label>Phone (optional)<input formControlName="attendeePhone" type="tel" placeholder="+91 98765 43210" autocomplete="tel" inputmode="tel"/></label>
                <button type="submit" [disabled]="managerTicketForm.invalid || managerTicketBusy">{{managerTicketBusy?'Issuing…':'Issue free ticket'}} <span>↗</span></button>
              </form>
              <div class="op-message success" *ngIf="managerTicketMessage">{{managerTicketMessage}}</div>
              <div class="op-message manager-result" *ngIf="managerTicketResult">Issued {{managerTicketResult.quantity}} × {{managerTicketResult.ticketType}} · ₹0 · <strong>{{managerTicketResult.issuedByName}}</strong><div class="manager-ticket-list"><span *ngFor="let t of managerTicketResult.tickets"><b>{{t.ticketNumber}}</b><a [href]="ticketUrl(t)" target="_blank" rel="noopener">Open QR ↗</a></span></div></div>
              <div class="op-message error" *ngIf="managerTicketError">{{managerTicketError}}</div>
            </article>

            <article class="op-card" *ngIf="canAssignManagers()">
              <div class="eyebrow">Event manager access</div><h2>Assign an event manager.</h2>
              <p>Managers are scoped to explicit events. They can scan only their assigned events and issue complimentary tickets only there.</p>
              <form [formGroup]="managerAssignmentForm" (ngSubmit)="assignManager()">
                <label>Event<select formControlName="eventId" (change)="onManagerAssignmentEventChange($any($event.target).value)"><option value="">Choose an event</option><option *ngFor="let e of dash?.events" [value]="e.id">{{e.name}}</option></select></label>
                <label>Manager email<input formControlName="email" type="email" placeholder="manager@example.com" autocomplete="off"/></label>
                <button type="submit" [disabled]="managerAssignmentForm.invalid || managerAssignmentBusy">{{managerAssignmentBusy?'Assigning…':'Assign manager'}} <span>→</span></button>
              </form>
              <div class="op-message success" *ngIf="managerAssignmentMessage">{{managerAssignmentMessage}}</div>
              <div class="op-message error" *ngIf="managerAssignmentError">{{managerAssignmentError}}</div>
              <div class="manager-members" *ngIf="managerAssignmentEventId">
                <small>Assigned managers</small>
                <div class="manager-member" *ngFor="let m of assignedManagers"><span><b>{{m.fullName}}</b><small>{{m.email}}</small></span><button type="button" (click)="unassignManager(m.email)">Remove</button></div>
                <div class="manager-empty" *ngIf="!assignedManagers.length">No manager is assigned to this event yet.</div>
              </div>
            </article>

            <article class="op-card" *ngIf="canCreateManager()">
              <div class="eyebrow">Platform administration</div><h2>Provision a manager.</h2>
              <p>Create a dedicated EVENT_MANAGER account. It cannot access events until it is explicitly assigned.</p>
              <form [formGroup]="managerCreateForm" (ngSubmit)="createManager()">
                <label>Email<input formControlName="email" type="email" placeholder="manager@example.com" autocomplete="off"/></label>
                <label>Name<input formControlName="name" placeholder="Event manager" autocomplete="name"/></label>
                <label>Password<input formControlName="password" type="password" placeholder="12+ character password" autocomplete="new-password"/></label>
                <button type="submit" [disabled]="managerCreateForm.invalid || managerCreateBusy">{{managerCreateBusy?'Creating…':'Create manager account'}} <span>→</span></button>
              </form>
              <div class="op-message success" *ngIf="managerCreateMessage">{{managerCreateMessage}}</div>
              <div class="op-message error" *ngIf="managerCreateError">{{managerCreateError}}</div>
            </article>
            <article class="quick-card">
              <div><div class="eyebrow">Scanner console</div><h3>Turn any phone into the gate scanner.</h3><p>No dedicated hardware. Staff sign in, choose their gate and scan QR tickets from the phone camera.</p></div>
              <a routerLink="/staff">Open staff console →</a>
            </article>
            <article class="quick-card dark" id="neelastack">
              <div><div class="eyebrow">Technology partner</div><h3>Digital experiences by Neelastack.</h3><p>Custom web applications, business portals, dashboards and automation for modern companies.</p></div>
              <a href="https://neelastack.com" target="_blank" rel="noopener noreferrer">Explore Neelastack ↗</a>
            </article>
          </section>
        </ng-container>
      </main>
    </div>
  `,
  styles: [
    `.admin-shell{min-height:100vh;background:#f4f1ec;color:#1a151b;display:grid;grid-template-columns:250px 1fr}aside{background:#0b090e;color:#fff;padding:26px 20px;display:flex;flex-direction:column;min-height:100vh;position:sticky;top:0;height:100vh}.brand{display:flex;align-items:center;gap:11px}.brand img{height:38px;max-width:82px;background:#f7f3ec;padding:5px;border-radius:10px;object-fit:contain}.brand span{font-size:11px;font-weight:900;line-height:1.1;letter-spacing:.09em}.brand i{font-style:normal;color:#817885;font-weight:600}.role{font-size:8px;letter-spacing:.17em;color:#635c68;margin:45px 10px 10px}nav{display:grid;gap:5px}nav a{padding:12px;border-radius:11px;color:#827987;text-decoration:none;font-size:11px}nav a.active,nav a:hover{background:rgba(255,255,255,.07);color:#fff}.aside-bottom{margin-top:auto;display:grid;gap:14px}.aside-bottom a,.aside-bottom button{font-size:9px;color:#786f7b;text-decoration:none;background:transparent;border:0;text-align:left;padding:0;cursor:pointer}.admin-shell main{padding:44px clamp(20px,4vw,58px);min-width:0}header{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.eyebrow{color:#958b96;text-transform:uppercase;letter-spacing:.15em;font-size:9px;font-weight:800}.admin-shell h1{font-size:clamp(42px,5vw,62px);letter-spacing:-.055em;margin:11px 0 8px;line-height:.95}.header-copy{margin:0;color:#918894;font-size:12px;max-width:580px;line-height:1.6}.header-actions{display:flex;gap:10px;align-items:center}.header-pill{background:#e8f2e9;color:#477353;border-radius:999px;padding:9px 12px;font-size:8px;letter-spacing:.12em;font-weight:800;white-space:nowrap}.header-pill i{display:inline-block;width:6px;height:6px;border-radius:50%;background:#64a678;margin-left:5px}.refresh{border:1px solid #ded6ce;background:#fff;border-radius:999px;padding:9px 12px;color:#514953;font-size:9px;font-weight:800;cursor:pointer}.refresh:disabled{opacity:.45}.dashboard-loading,.dashboard-error{margin-top:20px;padding:14px 16px;border-radius:14px;background:#fff;border:1px solid #e5dfd7;color:#837a84;font-size:10px}.dashboard-error{color:#974f52;display:flex;justify-content:space-between;align-items:center;gap:12px}.dashboard-error button{border:1px solid #d7cec6;background:#17121a;color:#fff;border-radius:999px;padding:8px 12px;font-size:9px;font-weight:800;cursor:pointer}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:42px}.metrics article{background:#fff;border:1px solid #e8e1d9;border-radius:18px;padding:18px}.metrics span,.metrics small{display:block;color:#918992;font-size:10px}.metrics strong{display:block;font-size:28px;letter-spacing:-.04em;margin:15px 0 5px}.metrics small{font-size:9px}.panel{background:#fff;border:1px solid #e5dfd7;border-radius:22px;margin-top:18px;overflow:hidden}.panel-head{display:flex;justify-content:space-between;align-items:end;padding:24px}.panel-head h2{font-size:28px;letter-spacing:-.03em;margin:8px 0 0}.panel-action{border:1px solid #e1d9d1;background:#f9f7f4;border-radius:999px;padding:10px 13px;font-size:10px;font-weight:800;cursor:pointer}.event-table{overflow-x:auto}.event-mobile-list{display:none}.table-row{display:grid;grid-template-columns:minmax(220px,2fr) minmax(155px,1fr) .55fr .7fr .8fr .7fr;gap:15px;padding:16px 24px;border-top:1px solid #eee9e3;align-items:center;font-size:11px;min-width:900px}.table-row.head{color:#9e949d;text-transform:uppercase;letter-spacing:.1em;font-size:8px}.table-row strong{font-size:12px}.table-row small{display:block;color:#9b939c;font-size:9px;margin-top:4px}.status{font-size:8px;letter-spacing:.08em;color:#8a818b;background:#f1edef;border-radius:999px;padding:6px 8px}.status.published{background:#e8f2e9;color:#46714f}.publish-mini,.tools button,.tools a{margin-left:6px;border:1px solid #d5c9b8;background:#fff;border-radius:999px;padding:5px 7px;font-size:8px;font-weight:800;cursor:pointer;text-decoration:none;color:#544b55}.publishing{margin-left:6px;color:#a097a0;font-size:8px}.empty{text-align:center;color:#938b95;padding:42px 24px;border-top:1px solid #eee9e3}.tools{display:flex;gap:4px;align-items:center}.create-form{padding:26px 24px 28px;border-top:1px solid #eee9e3;background:#faf8f5}.form-title h3{font-size:28px;margin:7px 0 4px}.form-title p{color:#918992;font-size:11px;margin:0 0 22px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px}.form-grid label,.op-card label{display:grid;gap:7px;text-transform:uppercase;letter-spacing:.09em;font-size:8px;font-weight:800;color:#5f5661}.form-grid label.wide{grid-column:1/-1}.form-grid input,.form-grid textarea,.op-card input,.op-card select{border:1px solid #ddd6ce;background:#fff;border-radius:11px;padding:11px;font:inherit;text-transform:none;letter-spacing:0;outline:none;color:#1d1820}.form-grid input:focus,.form-grid textarea:focus,.op-card input:focus,.op-card select:focus{border-color:#9e8150;box-shadow:0 0 0 3px rgba(158,129,80,.09)}.form-grid textarea{min-height:100px;resize:vertical}.ticket-heading{display:flex;justify-content:space-between;align-items:end;margin-top:20px;padding-bottom:8px;border-bottom:1px solid #e6dfd7;font-size:9px;text-transform:uppercase;letter-spacing:.1em;font-weight:800}.ticket-heading small{font-size:9px;color:#99919a;text-transform:none;letter-spacing:0;font-weight:500}.ticket-form-list{display:grid;gap:8px;margin-top:10px}.ticket-form-row{display:grid;grid-template-columns:1.4fr .8fr .7fr .6fr .6fr 34px;gap:8px}.ticket-form-row input{border:1px solid #ddd6ce;background:#fff;border-radius:10px;padding:10px}.ticket-form-row button{border:1px solid #ddd6ce;background:#fff;border-radius:10px;cursor:pointer}.secondary-action{margin-top:10px;border:1px dashed #c8c0b7;background:transparent;padding:9px 11px;border-radius:10px;font-size:10px;cursor:pointer}.create-submit,.op-card form button{display:flex;justify-content:space-between;align-items:center;margin-top:16px;width:100%;border:0;background:#17121a;color:#fff;border-radius:12px;padding:14px;font-weight:800;cursor:pointer}.create-submit:disabled,.op-card button:disabled{opacity:.45}.create-error{color:#a24b4b;font-size:10px;margin-top:10px}.operations-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:18px}.op-card,.quick-card{background:#fff;border:1px solid #e5dfd7;border-radius:22px;padding:24px;min-height:290px}.op-card h2,.quick-card h3{font-size:26px;letter-spacing:-.04em;margin:10px 0 6px}.op-card>p,.quick-card p{color:#827985;font-size:11px;line-height:1.55;max-width:440px}.op-card form{display:grid;gap:11px;margin-top:18px}.op-card input,.op-card select{height:44px}.op-card form button{margin-top:0;height:44px}.op-message{font-size:10px;padding:10px 12px;border-radius:10px;margin-top:11px}.op-message.success{background:#edf6ee;color:#426b4b}.op-message.error{background:#fff0f0;color:#8c4141}.quick-card{display:flex;flex-direction:column;justify-content:space-between;background:#ece5db;border:0}.quick-card.dark{background:#151019;color:#fff;border:0}.quick-card a{color:#1a151b;text-decoration:none;font-size:11px;font-weight:800}.quick-card.dark a{color:#fff}@media(max-width:1100px){.metrics{grid-template-columns:1fr 1fr}.operations-grid{grid-template-columns:1fr}.header-actions{flex-direction:column;align-items:flex-end}}.manager-issue{background:linear-gradient(145deg,#fff,#fbf6ed)}.manager-result{background:#f2efe8;color:#5c4e30;line-height:1.55}.form-grid input,.form-grid textarea,.op-card input,.op-card select,.ticket-form-row input{font-size:16px;line-height:1.35} .form-grid textarea{font-size:16px}.op-card input,.op-card select{font-size:16px}.ticket-form-row input{font-size:16px}.form-grid input::placeholder,.op-card input::placeholder,.ticket-form-row input::placeholder{font-size:16px}@media(max-width:760px){.admin-shell{display:block}aside{position:static;height:auto;min-height:0;padding:18px}.brand span{font-size:10px}nav,.role{display:none}.aside-bottom{display:flex;justify-content:space-between;margin-top:20px;gap:12px}.admin-shell main{padding:28px 14px 40px}.admin-shell h1{font-size:42px}.header-actions{display:none}.form-grid{grid-template-columns:1fr}.form-grid label.wide{grid-column:auto}.ticket-form-list{overflow:visible;padding-bottom:0}.ticket-form-row{grid-template-columns:1fr 1fr;min-width:0}.ticket-form-row input{min-width:0}.ticket-form-row button{grid-column:1/-1;min-height:44px}.desktop-events{display:none!important}.event-mobile-list{display:grid;gap:10px;padding:0 12px 12px}.event-mobile-card{background:#fbfaf8;border:1px solid #e8e1d9;border-radius:16px;padding:16px}.event-mobile-top{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.event-mobile-title{min-width:0;display:grid;gap:4px}.event-mobile-title strong{font-size:14px;line-height:1.25}.event-mobile-title small{font-size:10px;color:#9a929b}.event-mobile-top .status{flex:0 0 auto}.event-mobile-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:14px}.event-mobile-metrics div{background:#fff;border:1px solid #ece6df;border-radius:11px;padding:10px;min-width:0}.event-mobile-metrics span{display:block;color:#9c949d;font-size:8px;text-transform:uppercase;letter-spacing:.08em}.event-mobile-metrics strong{display:block;margin-top:5px;font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.event-mobile-actions{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin-top:12px}.event-mobile-actions a,.event-mobile-actions button{min-height:42px;border:1px solid #dcd4cb;background:#fff;border-radius:10px;display:flex;align-items:center;justify-content:center;padding:8px 6px;color:#4d454f;text-decoration:none;font-size:9px;font-weight:800}.event-mobile-actions .publish-mobile{grid-column:1/-1;background:#17121a;color:#fff;border-color:#17121a}.event-mobile-actions button:disabled{opacity:.5}.panel{overflow:hidden}.metrics{grid-template-columns:1fr 1fr}.operations-grid{grid-template-columns:1fr}.quick-card,.op-card{min-height:240px}.op-card input,.op-card select,.form-grid input,.form-grid textarea{font-size:16px}.panel-head{padding:20px 16px}.panel-head h2{font-size:24px}.panel-action{min-height:44px}.event-table{overflow:visible}}

.manager-ticket-list{display:grid;gap:8px;margin-top:10px}.manager-ticket-list span{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 10px;border:1px solid rgba(255,255,255,.08);border-radius:10px}.manager-ticket-list a{color:#e3c16e;text-decoration:none;font-weight:800}.manager-ticket-list a:hover{text-decoration:underline}
.manager-members{margin-top:14px;padding-top:12px;border-top:1px solid rgba(255,255,255,.07)}.manager-members>small{display:block;color:#7e7582;font-size:9px;text-transform:uppercase;letter-spacing:.1em;margin-bottom:8px}.manager-member{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:8px 0}.manager-member span{display:grid;gap:2px}.manager-member b{font-size:11px}.manager-member small{font-size:9px;color:#8c8390}.manager-member button{min-height:34px!important;padding:7px 10px!important;border:1px solid rgba(255,255,255,.1)!important;background:transparent!important;color:#c7bdc9!important;border-radius:9px!important}.manager-empty{font-size:10px;color:#77707b;padding:6px 0}
  `]
})
export class AdminComponent implements OnInit {
  readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly fb = inject(FormBuilder);

  dash?: Dashboard;
  showCreate = false;
  creating = false;
  publishingId = '';
  createError = '';
  loading = true;
  loadError = '';
  staffBusy = false;
  assignmentBusy = false;
  staffMessage = '';
  staffError = '';
  assignmentMessage = '';
  assignmentError = '';
  managerTicketBusy = false;
  managerTicketMessage = '';
  managerTicketError = '';
  managerTicketTypes: ManagerTicketType[] = [];
  managerTicketResult?: ManagerTicketIssueResponse;
  managerAssignmentBusy = false;
  managerAssignmentMessage = '';
  managerAssignmentError = '';
  managerAssignmentEventId = '';
  assignedManagers: EventManagerView[] = [];
  managerCreateBusy = false;
  managerCreateMessage = '';
  managerCreateError = '';
  editingEventId = '';

  createForm = this.fb.group({
    name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(180)]],
    slug: ['', [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/), Validators.maxLength(180)]],
    startsAt: ['', Validators.required], endsAt: [''], capacity: [5000, [Validators.min(1), Validators.max(1000000)]],
    venueName: ['', [Validators.required, Validators.maxLength(255)]], city: ['Jaipur', [Validators.maxLength(120)]], venueAddress: ['', Validators.maxLength(500)],
    description: ['', Validators.maxLength(5000)],
    ticketTypes: this.fb.array([this.ticketGroup('General Entry', 599, 1000)])
  });

  staffForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]], name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]], password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]] });
  assignmentForm = this.fb.nonNullable.group({ eventId: ['', Validators.required], email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]], gate: ['Main Gate', [Validators.required, Validators.maxLength(80)]] });
  managerTicketForm = this.fb.nonNullable.group({
    eventId: ['', Validators.required], ticketTypeId: ['', Validators.required], quantity: [1, [Validators.required, Validators.min(1), Validators.max(100)]],
    attendeeName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    attendeeEmail: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    attendeePhone: ['', Validators.maxLength(40)], idempotencyKey: [this.issueKey(), [Validators.required, Validators.minLength(16), Validators.maxLength(100)]]
  });
  managerAssignmentForm = this.fb.nonNullable.group({ eventId: ['', Validators.required], email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]] });
  managerCreateForm = this.fb.nonNullable.group({ email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]], name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]], password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]] });

  get firstName(): string { return this.auth.fullName().split(/\s+/)[0] || 'Operator'; }
  get ticketForms() { return this.createForm.controls.ticketTypes; }

  ngOnInit(): void { this.load(); }
  canAdministerEvents(): boolean { const r = this.auth.role(); return r === 'ADMIN' || r === 'ORGANIZER'; }
  canOperateAssignedEvents(): boolean { const r = this.auth.role(); return r === 'ADMIN' || r === 'ORGANIZER' || r === 'EVENT_MANAGER'; }
  canAssignStaff(): boolean { const r = this.auth.role(); return r === 'ADMIN' || r === 'ORGANIZER'; }
  canCreateStaff(): boolean { return this.auth.role() === 'ADMIN'; }
  isEventManager(): boolean { return this.auth.role() === 'EVENT_MANAGER'; }
  canAssignManagers(): boolean { const r = this.auth.role(); return r === 'ADMIN' || r === 'ORGANIZER'; }
  canCreateManager(): boolean { return this.auth.role() === 'ADMIN'; }

  private issueKey(): string { return `MGR-${globalThis.crypto.randomUUID()}`; }
  ticketUrl(t: { ticketId: string; accessToken: string }): string { return `/ticket/${encodeURIComponent(t.ticketId)}#access=${encodeURIComponent(t.accessToken)}`; }

  onManagerEventChange(eventId: string): void {
    this.managerTicketTypes = [];
    this.managerTicketForm.controls.ticketTypeId.setValue('');
    if (!eventId) return;
    this.api.adminTicketTypes(eventId).subscribe({
      next: types => this.managerTicketTypes = types.filter(t => t.status !== 'CLOSED' && t.availableQuantity > 0),
      error: e => this.managerTicketError = e?.error?.message || 'Could not load ticket types for this event.'
    });
  }

  issueManagerTickets(): void {
    if (this.managerTicketForm.invalid) { this.managerTicketForm.markAllAsTouched(); return; }
    this.managerTicketBusy = true; this.managerTicketMessage = ''; this.managerTicketError = ''; this.managerTicketResult = undefined;
    const v = this.managerTicketForm.getRawValue();
    this.api.issueComplimentaryTicket(v).subscribe({
      next: result => {
        this.managerTicketBusy = false; this.managerTicketResult = result; this.managerTicketMessage = `Complimentary ticket(s) issued for ${result.eventName}.`;
        this.managerTicketForm.patchValue({ attendeeName: '', attendeeEmail: '', attendeePhone: '', quantity: 1, idempotencyKey: this.issueKey() });
        this.onManagerEventChange(v.eventId); this.load();
      },
      error: e => { this.managerTicketBusy = false; this.managerTicketError = e?.error?.message || 'Could not issue complimentary tickets.'; }
    });
  }

  onManagerAssignmentEventChange(eventId: string): void {
    this.managerAssignmentEventId = eventId || '';
    this.assignedManagers = [];
    if (!eventId) return;
    this.api.adminManagers(eventId).subscribe({
      next: managers => this.assignedManagers = managers,
      error: e => this.managerAssignmentError = e?.error?.message || 'Could not load assigned managers.'
    });
  }

  unassignManager(email: string): void {
    const eventId = this.managerAssignmentEventId;
    if (!eventId) return;
    this.api.unassignManager(eventId, email).subscribe({
      next: () => { this.assignedManagers = this.assignedManagers.filter(m => m.email.toLowerCase() !== email.toLowerCase()); this.managerAssignmentMessage = `Manager ${email} removed from this event.`; },
      error: e => this.managerAssignmentError = e?.error?.message || 'Manager removal failed.'
    });
  }

  assignManager(): void {
    if (this.managerAssignmentForm.invalid) { this.managerAssignmentForm.markAllAsTouched(); return; }
    this.managerAssignmentBusy = true; this.managerAssignmentMessage = ''; this.managerAssignmentError = '';
    const v = this.managerAssignmentForm.getRawValue();
    this.api.assignManager(v.eventId, { email: v.email.trim().toLowerCase() }).subscribe({
      next: () => { this.managerAssignmentBusy = false; this.managerAssignmentEventId = v.eventId; this.managerAssignmentMessage = `Manager ${v.email.trim().toLowerCase()} is now assigned to this event.`; this.onManagerAssignmentEventChange(v.eventId); },
      error: e => { this.managerAssignmentBusy = false; this.managerAssignmentError = e?.error?.message || 'Manager assignment failed.'; }
    });
  }

  createManager(): void {
    if (this.managerCreateForm.invalid) { this.managerCreateForm.markAllAsTouched(); return; }
    this.managerCreateBusy = true; this.managerCreateMessage = ''; this.managerCreateError = '';
    this.api.createManager(this.managerCreateForm.getRawValue()).subscribe({
      next: () => { const email = this.managerCreateForm.controls.email.value; this.managerCreateBusy = false; this.managerCreateMessage = `Manager ${email} created. Assign this account to one or more events before it can operate them.`; this.managerCreateForm.reset({ email:'', name:'', password:'' }); },
      error: e => { this.managerCreateBusy = false; this.managerCreateError = e?.error?.message || 'Manager account could not be created.'; }
    });
  }

  private ticketGroup(name: string, priceRupees: number, quantity: number) {
    return this.fb.group({
      name: [name, [Validators.required, Validators.minLength(2), Validators.maxLength(120)]], description: [''],
      priceRupees: [priceRupees, [Validators.required, Validators.min(1), Validators.max(10000000)]],
      totalQuantity: [quantity, [Validators.required, Validators.min(1), Validators.max(1000000)]],
      minPerOrder: [1, [Validators.required, Validators.min(1), Validators.max(20)]],
      maxPerOrder: [10, [Validators.required, Validators.min(1), Validators.max(20)]]
    });
  }

  addTicket(): void { this.ticketForms.push(this.ticketGroup('New ticket', 999, 100)); }
  removeTicket(i: number): void { this.ticketForms.removeAt(i); }

  editEvent(eventId: string): void {
    this.editingEventId = eventId;
    queueMicrotask(() => document.getElementById('event-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }

  closeEventEditor(): void { this.editingEventId = ''; }
  onEventEditorSaved(): void { this.load(); }

  load(): void {
    this.loading = true; this.loadError = '';
    this.api.dashboard().subscribe({ next: d => { this.dash = d; this.loading = false; }, error: e => { this.loading = false; this.loadError = e?.error?.message || 'Operations data could not be loaded. Please retry.'; } });
  }

  publish(eventId: string): void {
    this.publishingId = eventId;
    this.api.publishEvent(eventId).subscribe({ next: () => { this.publishingId = ''; this.load(); }, error: e => { this.publishingId = ''; this.createError = e?.error?.message || 'Could not publish event.'; } });
  }

  createEvent(): void {
    if (this.createForm.invalid) { this.createForm.markAllAsTouched(); return; }
    this.creating = true; this.createError = '';
    const v = this.createForm.getRawValue();
    const tickets = v.ticketTypes.map(t => ({
      name: t.name!, description: t.description || '', priceMinorUnits: Math.round(Number(t.priceRupees) * 100), totalQuantity: Number(t.totalQuantity), minPerOrder: Number(t.minPerOrder), maxPerOrder: Number(t.maxPerOrder), saleStartsAt: undefined, saleEndsAt: undefined
    }));
    const totalTicketCapacity = tickets.reduce((sum, t) => sum + Math.max(0, t.totalQuantity), 0);
    if (v.capacity && totalTicketCapacity > Number(v.capacity)) { this.creating = false; this.createError = 'Ticket quantities cannot exceed the event capacity.'; return; }
    const startsAt = this.toIso(v.startsAt!); const endsAt = v.endsAt ? this.toIso(v.endsAt) : undefined;
    if (!startsAt || (v.endsAt && !endsAt)) { this.creating = false; this.createError = 'Enter valid event start/end times.'; return; }
    if (endsAt && new Date(endsAt).getTime() <= new Date(startsAt).getTime()) { this.creating = false; this.createError = 'Event end time must be after the start time.'; return; }
    for (const t of tickets) if (t.maxPerOrder < t.minPerOrder) { this.creating = false; this.createError = 'Maximum tickets per order must be at least the minimum.'; return; }
    const body = { slug: v.slug!, name: v.name!, description: v.description || '', startsAt, endsAt, capacity: v.capacity || undefined, venueName: v.venueName!, venueAddress: v.venueAddress || '', city: v.city || '', ticketTypes: tickets };
    this.api.createEvent(body).subscribe({ next: created => {
      this.creating = false; this.showCreate = false; this.createForm.reset({ name: '', slug: '', description: '', startsAt: '', endsAt: '', capacity: 5000, venueName: '', city: 'Jaipur', venueAddress: '' });
      this.ticketForms.clear(); this.addTicket(); this.load();
      this.assignmentForm.controls.eventId.setValue(created.id);
      this.editingEventId = created.id;
    }, error: e => { this.creating = false; this.createError = e?.error?.message || 'Could not create event.'; } });
  }

  createStaff(): void {
    if (this.staffForm.invalid) { this.staffForm.markAllAsTouched(); return; }
    this.staffBusy = true; this.staffMessage = ''; this.staffError = '';
    this.api.createStaff(this.staffForm.getRawValue()).subscribe({ next: () => { const email = this.staffForm.controls.email.value; this.staffBusy = false; this.staffMessage = `Staff account ${email} created. Assign it to an event gate below.`; this.staffForm.reset({ email: '', name: '', password: '' }); }, error: e => { this.staffBusy = false; this.staffError = e?.error?.message || 'Staff account could not be created.'; } });
  }

  assignStaff(): void {
    if (this.assignmentForm.invalid) { this.assignmentForm.markAllAsTouched(); return; }
    this.assignmentBusy = true; this.assignmentMessage = ''; this.assignmentError = '';
    const v = this.assignmentForm.getRawValue();
    this.api.assignStaff(v.eventId, { email: v.email.trim().toLowerCase(), gate: v.gate.trim() }).subscribe({ next: () => { this.assignmentBusy = false; this.assignmentMessage = `Assigned ${v.email.trim().toLowerCase()} to ${v.gate.trim()}.`; }, error: e => { this.assignmentBusy = false; this.assignmentError = e?.error?.message || 'Staff assignment failed.'; } });
  }

  downloadCsv(event: Dashboard['events'][number]): void {
    this.api.attendeesCsv(event.id).subscribe({ next: blob => {
      const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = `${event.slug}-attendees.csv`; a.style.display = 'none'; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, error: e => { this.loadError = e?.error?.message || 'Attendee export failed.'; } });
  }

  logout(): void { this.auth.logout().subscribe({ complete: () => location.href = '/login', error: () => location.href = '/login' }); }
  private toIso(value: string): string | undefined { const d = new Date(value); return Number.isNaN(d.getTime()) ? undefined : d.toISOString(); }
}
