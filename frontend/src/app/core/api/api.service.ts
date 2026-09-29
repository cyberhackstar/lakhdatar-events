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
  ManagerTicketIssueResponse
} from './api.models';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);

  events(query: EventQuery = {}) {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(query)) if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
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

  verifyPayment(body: { razorpayOrderId: string; razorpayPaymentId: string; razorpaySignature: string }) {
    return this.http.post<VerifyResponse>(`${this.base}/public/checkout/verify`, body);
  }

  recover(body: { orderNumber: string; email: string }) {
    return this.http.post<RecoveryResponse>(`${this.base}/public/orders/recover`, body);
  }

  ticket(ticketId: string, token: string) {
    return this.http.get<TicketView>(`${this.base}/public/tickets/${encodeURIComponent(ticketId)}`, { headers: { 'X-Ticket-Token': token } });
  }

  createEvent(body: Record<string, unknown>) {
    return this.http.post<{ id: string; slug: string; name: string; status: string }>(`${this.base}/admin/events`, body);
  }

  dashboard() { return this.http.get<Dashboard>(`${this.base}/admin/dashboard`); }
  adminEvents() { return this.http.get<Dashboard['events']>(`${this.base}/admin/events`); }
  adminEvent(eventId: string) { return this.http.get<AdminEventView>(`${this.base}/admin/events/${eventId}`); }
  publishEvent(eventId: string) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/publish`, {}); }
  adminTransition(eventId: string, action: 'unpublish' | 'cancel' | 'complete' | 'archive') { return this.http.post<void>(`${this.base}/admin/events/${eventId}/${action}`, {}); }
  updateEvent(eventId: string, body: Record<string, unknown>) { return this.http.put<void>(`${this.base}/admin/events/${eventId}`, body); }
  addTicketType(eventId: string, body: Record<string, unknown>) { return this.http.post<{ id: string; name: string }>(`${this.base}/admin/events/${eventId}/ticket-types`, body); }
  updateTicketType(ticketTypeId: string, body: Record<string, unknown>) { return this.http.put<void>(`${this.base}/admin/ticket-types/${ticketTypeId}`, body); }
  createStaff(body: { email: string; name: string; password: string }) { return this.http.post<void>(`${this.base}/admin/staff`, body); }
  assignStaff(eventId: string, body: { email: string; gate: string }) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/staff`, body); }
  attendeesCsv(eventId: string) { return this.http.get(`${this.base}/admin/events/${eventId}/attendees.csv`, { responseType: 'blob' }); }
  adminManagers(eventId: string) { return this.http.get<EventManagerView[]>(`${this.base}/admin/events/${eventId}/managers`); }
  adminTicketTypes(eventId: string) { return this.http.get<ManagerTicketType[]>(`${this.base}/admin/events/${eventId}/ticket-types`); }
  createManager(body: { email: string; name: string; password: string }) { return this.http.post<void>(`${this.base}/admin/managers`, body); }
  assignManager(eventId: string, body: { email: string }) { return this.http.post<void>(`${this.base}/admin/events/${eventId}/managers`, body); }
  unassignManager(eventId: string, email: string) { return this.http.delete<void>(`${this.base}/admin/events/${eventId}/managers`, { params: { email } }); }
  issueComplimentaryTicket(body: { eventId: string; ticketTypeId: string; quantity: number; attendeeName: string; attendeeEmail: string; attendeePhone?: string; idempotencyKey: string }) {
    return this.http.post<ManagerTicketIssueResponse>(`${this.base}/admin/manager-tickets/complimentary`, body);
  }
  staffEvents() { return this.http.get<StaffEvent[]>(`${this.base}/staff/events`); }

  scan(eventId: string, gate: string, qrToken: string) {
    return this.http.post<ScanResponse>(`${this.base}/checkin/scan`, { eventId, gate, qrToken });
  }
}
