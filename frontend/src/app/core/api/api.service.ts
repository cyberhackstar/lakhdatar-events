import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { API_BASE_URL } from './api.tokens';
import {
  AuthResponse,
  EventCard,
  EventQuery,
  Facets,
  PageView,
  CheckoutResponse,
  Dashboard,
  EventView,
  AdminEventView,
  RecoveryResponse,
  ScanResponse,
  StaffEvent,
  TicketView,
  VerifyResponse,
  EventManagerView,
  ManagerTicketType,
  ManagerTicketIssueResponse,
  AdminOrganizer,
  AdminOrganizerList,
  TeamView,
  TeamKind,
  TeamMember,
  CreateTeamMemberBody,
  CreatedTeamMember,
  EventTeam,
  AdminPage, AdminOperationsPage, AdminIssuedTicket, AdminOrder, AdminScopedTicket, CursorPage
} from './api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  /** Build HTTP query params without ever serializing undefined/null/blank optional filters. */
  private queryParams<T extends object>(values: T): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(values as Record<string, unknown>)) {
      if (value === undefined || value === null || value === '') continue;
      params = params.set(key, String(value));
    }
    return params;
  }

  events(query: EventQuery = {}) {
    const params = this.queryParams(query);
    return this.http.get<PageView<EventCard>>(`${this.base}/public/events`, { params });
  }
  featuredEvents(limit = 6) { return this.http.get<PageView<EventCard>>(`${this.base}/public/events/featured`, { params: { limit } }); }
  upcomingEvents(page = 0, size = 12) { return this.http.get<PageView<EventCard>>(`${this.base}/public/events/upcoming`, { params: { page, size } }); }
  eventFacets() { return this.http.get<Facets>(`${this.base}/public/events/facets`); }

  event(slug: string) { return this.http.get<EventView>(`${this.base}/public/events/${encodeURIComponent(slug)}`); }

  checkout(body: {
    eventId: string;
    customerName: string;
    customerEmail: string;
    customerPhone?: string;
    idempotencyKey: string;
    items: Array<{ ticketTypeId: string; quantity: number }>;
  }) { return this.http.post<CheckoutResponse>(`${this.base}/public/checkout`, body); }

  verifyPayment(body: { providerOrderId: string; providerPaymentId?: string; providerSignature?: string }) {
    return this.http.post<VerifyResponse>(`${this.base}/public/checkout/verify`, body, { withCredentials: true });
  }

  recover(body: { orderNumber: string; email: string }) {
    return this.http.post<RecoveryResponse>(`${this.base}/public/orders/recover`, body);
  }

  ticket(ticketId: string, token: string) {
    return this.http.get<TicketView>(`${this.base}/public/tickets/${encodeURIComponent(ticketId)}`, { headers: { 'X-Ticket-Token': token } });
  }

  ticketPdf(ticketId: string, token: string) {
    return this.http.get(`${this.base}/public/tickets/${encodeURIComponent(ticketId)}/pdf`, { headers: { 'X-Ticket-Token': token }, responseType: 'blob' });
  }


  uploadAdminAsset(file: File, purpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER', eventId?: string, organizerSlug?: string) {
    const form = new FormData(); form.append('file', file);
    let params: Record<string, string> = { purpose }; if (eventId) params.eventId = eventId; if (organizerSlug) params.organizerSlug = organizerSlug;
    return this.http.post<import('./api.models').AssetUploadResult>(`${this.base}/admin/assets/images`, form, { params });
  }

  listOrganizers() { return this.http.get<AdminOrganizerList>(`${this.base}/admin/organizers`); }
  /** Organizer name + optional logo. The logo is uploaded to Cloudinary by the backend; only its URL is stored. */
  createOrganizer(body: { name: string; slug: string; description?: string; website?: string }, logo?: File | null) {
    const form = new FormData();
    form.append('name', body.name); form.append('slug', body.slug);
    if (body.description) form.append('description', body.description);
    if (body.website) form.append('website', body.website);
    if (logo) form.append('logo', logo);
    return this.http.post<AdminOrganizer>(`${this.base}/admin/organizers`, form);
  }

  initialAdminSetupStatus() { return this.http.get<import('./api.models').InitialAdminSetupStatus>(`${this.base}/setup/initial-admin/status`, { headers: { 'Cache-Control': 'no-store' } }); }
  createInitialAdmin(body: { email: string; password: string; name: string; organizerName: string; organizerSlug: string }, token: string) {
    return this.http.post<import('./api.models').InitialAdminSetupResult>(`${this.base}/setup/initial-admin`, body, { headers: { 'X-Initial-Setup-Token': token, 'Cache-Control': 'no-store' } });
  }

  createEvent(body: Record<string, unknown>) {
    return this.http.post<{ id: string; slug: string; name: string; status: string }>(`${this.base}/admin/events`, body);
  }

  dashboard() { return this.http.get<Dashboard>(`${this.base}/admin/dashboard`); }
  adminEvents() { return this.http.get<Dashboard['events']>(`${this.base}/admin/events`); }
  adminEventsCursor(params: { q?: string; status?: string; cursor?: string; size?: number } = {}) { return this.http.get<import('./api.models').AdminEventCursorPage>(`${this.base}/admin/events/cursor`, { params: this.queryParams(params) }); }
  adminEvent(eventId: string) { return this.http.get<AdminEventView>(`${this.base}/admin/events/${eventId}`); }
  eventOperationsSummary(eventId: string) { return this.http.get<{eventId:string;eventName:string;ticketsSold:number;ticketsCheckedIn:number;revenueMinor:number;orderCount:number}>(`${this.base}/admin/events/${eventId}/operations/summary`); }
  publishEvent(eventId: string) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/publish`, {}); }
  adminTransition(eventId: string, action: 'unpublish' | 'cancel' | 'complete' | 'archive') { return this.http.post<void>(`${this.base}/admin/events/${eventId}/${action}`, {}); }
  updateEvent(eventId: string, body: Record<string, unknown>) { return this.http.put<void>(`${this.base}/admin/events/${eventId}`, body); }
  addTicketType(eventId: string, body: Record<string, unknown>) { return this.http.post<{ id: string; name: string }>(`${this.base}/admin/events/${eventId}/ticket-types`, body); }
  updateTicketType(ticketTypeId: string, body: Record<string, unknown>) { return this.http.put<void>(`${this.base}/admin/ticket-types/${ticketTypeId}`, body); }
  assignStaff(eventId: string, body: { email: string; gate: string }) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/staff`, body); }
  attendeesCsv(eventId: string) { return this.http.get(`${this.base}/admin/events/${eventId}/attendees.csv`, { responseType: 'blob' }); }
  adminManagers(eventId: string) { return this.http.get<EventManagerView[]>(`${this.base}/admin/events/${eventId}/managers`); }
  adminTicketTypes(eventId: string) { return this.http.get<ManagerTicketType[]>(`${this.base}/admin/events/${eventId}/ticket-types`); }
  assignManager(eventId: string, body: { email: string }) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/managers`, body); }
  // ---- organizer-owned team ----
  orgTeam(slug: string) { return this.http.get<TeamView>(`${this.base}/admin/organizers/${encodeURIComponent(slug)}/team`); }
  createTeamMember(slug: string, kind: TeamKind, body: CreateTeamMemberBody) {
    return this.http.post<CreatedTeamMember>(`${this.base}/admin/organizers/${encodeURIComponent(slug)}/team/${kind}`, body);
  }
  patchTeamMember(slug: string, userId: string, body: { name?: string; active?: boolean }) {
    return this.http.patch<TeamMember>(`${this.base}/admin/organizers/${encodeURIComponent(slug)}/team/${userId}`, body);
  }
  resendTeamInvite(slug: string, userId: string) {
    return this.http.post<CreatedTeamMember>(`${this.base}/admin/organizers/${encodeURIComponent(slug)}/team/${userId}/invite`, {});
  }
  eventTeam(eventId: string) { return this.http.get<EventTeam>(`${this.base}/admin/events/${eventId}/team`); }
  changeStaffGate(eventId: string, body: { email: string; gate: string }) { return this.http.put<void>(`${this.base}/admin/events/${eventId}/staff`, body); }
  removeStaff(eventId: string, email: string) { return this.http.delete<void>(`${this.base}/admin/events/${eventId}/staff`, { params: { email } }); }
  unassignManager(eventId: string, email: string) { return this.http.delete<void>(`${this.base}/admin/events/${eventId}/managers`, { params: { email } }); }
  issueComplimentaryTicket(body: { eventId: string; ticketTypeId: string; quantity: number; attendeeName: string; attendeeEmail: string; attendeePhone?: string; idempotencyKey: string }) {
    return this.http.post<ManagerTicketIssueResponse>(`${this.base}/admin/manager-tickets/complimentary`, body);
  }
  resendTicketEmail(orderPublicId: string) { return this.http.post<{ emailStatus: string }>(`${this.base}/admin/manager-tickets/orders/${orderPublicId}/email`, {}); }
  issuedTicketsCursor(eventId: string, params: { q?: string; status?: string; source?: string; cursor?: string; size?: number } = {}) { return this.http.get<import('./api.models').CursorPage<import('./api.models').AdminIssuedTicket>>(`${this.base}/admin/events/${eventId}/tickets/cursor`, { params: this.queryParams(params) }); }
  issuedTickets(eventId: string, params: { q?: string; status?: string; source?: string; page?: number; size?: number } = {}) {
    return this.http.get<AdminOperationsPage<AdminIssuedTicket>>(`${this.base}/admin/events/${eventId}/tickets`, { params: this.queryParams(params) });
  }
  allIssuedTickets(params: { eventId?: string; q?: string; status?: string; source?: string; page?: number; size?: number } = {}) {
    return this.http.get<AdminPage<AdminScopedTicket>>(`${this.base}/admin/tickets`, { params: this.queryParams(params) });
  }
  allIssuedTicketsCursor(params: { eventId?: string; q?: string; status?: string; source?: string; cursor?: string; size?: number } = {}) {
    return this.http.get<CursorPage<AdminScopedTicket>>(`${this.base}/admin/tickets/cursor`, { params: this.queryParams(params) });
  }
  eventOrdersCursor(eventId: string, params: { q?: string; status?: string; cursor?: string; size?: number } = {}) { return this.http.get<import('./api.models').CursorPage<import('./api.models').AdminOrder>>(`${this.base}/admin/events/${eventId}/orders/cursor`, { params: this.queryParams(params) }); }
  eventOrders(eventId: string, params: { q?: string; status?: string; page?: number; size?: number } = {}) {
    return this.http.get<AdminOperationsPage<AdminOrder>>(`${this.base}/admin/events/${eventId}/orders`, { params: this.queryParams(params) });
  }
  staffEvents() { return this.http.get<StaffEvent[]>(`${this.base}/staff/events`); }

  operationsHealth() { return this.http.get<import('./api.models').OperationsHealth>(`${this.base}/admin/ops/health`); }
  financeRefundsCursor(params: { q?: string; status?: string; cursor?: string; size?: number } = {}) { return this.http.get<CursorPage<import('./api.models').FinanceRefund>>(`${this.base}/finance/refunds/cursor`, { params: this.queryParams(params) }); }
  financeLedgerCursor(params: { q?: string; entryType?: string; cursor?: string; size?: number } = {}) { return this.http.get<CursorPage<import('./api.models').FinanceLedgerRow>>(`${this.base}/finance/ledger/cursor`, { params: this.queryParams(params) }); }
  financeOverview() { return this.http.get<import('./api.models').FinanceOverview>(`${this.base}/finance/overview`); }
  financeRefunds(params: { q?: string; status?: string; page?: number; size?: number } = {}) { return this.http.get<import('./api.models').FinancePage<import('./api.models').FinanceRefund>>(`${this.base}/finance/refunds`, { params: this.queryParams(params) }); }
  financeLedger(params: { q?: string; entryType?: string; page?: number; size?: number } = {}) { return this.http.get<import('./api.models').FinancePage<import('./api.models').FinanceLedgerRow>>(`${this.base}/finance/ledger`, { params: this.queryParams(params) }); }

  scan(eventId: string, gate: string, qrToken: string) {
    return this.http.post<ScanResponse>(`${this.base}/checkin/scan`, { eventId, gate, qrToken });
  }
}
