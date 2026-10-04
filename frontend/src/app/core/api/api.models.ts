import { BrandConfig } from '../branding/branding.model';

export interface TicketTypeView {
  id: string;
  name: string;
  description?: string;
  priceMinorUnits: number;
  currency: string;
  availableQuantity: number;
  minPerOrder: number;
  maxPerOrder: number;
  status: string;
  saleStartsAt?: string;
  saleEndsAt?: string;
}

export type SalesState = 'AVAILABLE' | 'SELLING_FAST' | 'SOLD_OUT' | 'BOOKING_NOT_STARTED' | 'BOOKING_CLOSED' | 'CANCELLED' | 'COMPLETED';

export interface OrganizerView {
  slug: string;
  name: string;
  logoUrl?: string;
  description?: string;
  website?: string;
  contactEmail?: string;
  contactPhone?: string;
  address?: string;
  supportHours?: string;
  instagramUrl?: string;
  facebookUrl?: string;
}

export interface EventView {
  id: string;
  slug: string;
  name: string;
  description?: string;
  startsAt: string;
  endsAt?: string;
  capacity?: number;
  currency: string;
  venueName?: string;
  venueAddress?: string;
  brand: BrandConfig;
  ticketTypes: TicketTypeView[];
  shortDescription?: string;
  category?: string;
  timezone?: string;
  coverImageUrl?: string;
  gallery?: string[];
  highlights?: string[];
  bookingStartsAt?: string;
  bookingEndsAt?: string;
  terms?: string;
  refundPolicy?: string;
  ageRestriction?: string;
  city?: string;
  state?: string;
  country?: string;
  mapUrl?: string;
  featured?: boolean;
  status?: string;
  salesState?: SalesState;
  startingPriceMinor?: number | null;
  organizer?: OrganizerView | null;
  paymentProvider?: 'RAZORPAY' | 'CASHFREE';
}

export interface EventCard {
  id: string;
  slug: string;
  name: string;
  shortDescription?: string;
  category?: string;
  coverImageUrl?: string;
  startsAt: string;
  endsAt?: string;
  timezone?: string;
  venueName?: string;
  city?: string;
  state?: string;
  organizerName: string;
  organizerSlug: string;
  featured: boolean;
  status: string;
  salesState: SalesState;
  startingPriceMinor?: number | null;
  currency: string;
  availableQuantity: number;
}

export interface PageView<T> { items: T[]; page: number; size: number; total: number; totalPages: number; }
export interface Facets { categories: string[]; cities: string[]; }

export interface EventQuery {
  q?: string; category?: string; city?: string; organizer?: string; featured?: boolean;
  from?: string; to?: string; minPrice?: number; maxPrice?: number; page?: number; size?: number;
}

export interface CheckoutResponse {
  orderPublicId: string;
  orderNumber: string;
  provider: 'RAZORPAY' | 'CASHFREE';
  providerOrderId: string;
  providerPublicKey?: string;
  providerSessionId?: string;
  razorpayOrderId?: string;
  razorpayKeyId?: string;
  amountMinorUnits: number;
  currency: string;
  reservationExpiresAt: string;
}

export interface VerifyResponse {
  orderPublicId: string;
  orderNumber: string;
  status: string;
  tickets: Array<{ ticketId: string; ticketNumber: string; accessToken: string }>;
}

export interface TicketView {
  ticketId: string;
  ticketNumber: string;
  status: string;
  attendeeName?: string;
  eventName: string;
  startsAt: string;
  endsAt?: string;
  venueName?: string;
  venueAddress?: string;
  ticketType: string;
  amountMinorUnits: number;
  currency: string;
  checkedInAt?: string;
  source?: string;
  issuedByName?: string;
  qrDataUri: string;
  brand: BrandConfig;
}

export interface RecoveryResponse {
  orderNumber: string;
  status: string;
  eventName: string;
  tickets: Array<{ ticketId: string; ticketNumber: string; accessToken: string }>;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  role: string;
  fullName: string;
}


export interface AdminTicketView {
  id: string; name: string; description?: string; priceMinorUnits: number; currency: string;
  totalQuantity: number; soldQuantity: number; reservedQuantity: number; availableQuantity: number;
  minPerOrder: number; maxPerOrder: number; status: string; saleStartsAt?: string; saleEndsAt?: string;
}

export interface AdminEventView {
  id: string; slug: string; name: string; shortDescription?: string; description?: string; category: string;
  startsAt: string; endsAt?: string; capacity?: number; timezone: string; currency: string;
  venueName?: string; venueAddress?: string; city?: string; state?: string; mapUrl?: string; coverImageUrl?: string;
  gallery: string[]; highlights: string[]; bookingStartsAt?: string; bookingEndsAt?: string; terms?: string;
  refundPolicy?: string; ageRestriction?: string; featured: boolean; displayOrder: number; status: string;
  organizerName: string; organizerSlug?: string; paymentProvider: 'RAZORPAY' | 'CASHFREE'; brandingMode: 'TEXT_ONLY' | 'LOGO_ONLY' | 'BOTH'; organizerLogoUrl?: string; eventLogoUrl?: string; eventBannerUrl?: string; ticketTypes: AdminTicketView[];
}

export interface Dashboard {
  events: Array<{
    id: string;
    slug: string;
    name: string;
    status: string;
    startsAt: string;
    ticketsSold: number;
    ticketsCheckedIn: number;
    revenueMinor: number;
  }>;
  totalSold: number;
  totalCheckedIn: number;
  totalRevenueMinor: number;
  totalEvents: number;
  publishedEvents: number;
  draftEvents: number;
}

export interface StaffEvent {
  id: string;
  name: string;
  slug: string;
  gate: string;
  status: string;
  startsAt: string;
  organizerName: string;
}

export type ScanResultCode = 'ACCEPTED' | 'ALREADY_USED' | 'INVALID' | 'CANCELLED' | 'REFUNDED' | 'WRONG_EVENT' | 'STAFF_NOT_ASSIGNED' | 'EVENT_CLOSED' | 'RATE_LIMITED';

export interface EventManagerView { userId: string; email: string; fullName: string; role: string; eventName: string; }

export interface ManagerTicketType { id: string; name: string; priceMinorUnits: number; availableQuantity: number; status: string; }

export interface ManagerTicketIssueResponse {
  orderPublicId: string; orderNumber: string; eventName: string; ticketType: string; quantity: number;
  amountMinorUnits: number; source: string; issuedByName: string;
  tickets: Array<{ ticketId: string; ticketNumber: string; accessToken: string }>;
  /** SENT | NOT_CONFIGURED | FAILED | NO_TICKETS - the real outcome of the ticket email. */
  emailStatus?: 'SENT' | 'NOT_CONFIGURED' | 'FAILED' | 'NO_TICKETS' | null;
}

export interface ScanResponse {
  result: ScanResultCode;
  message: string;
  ticketNumber: string | null;
  attendeeName: string | null;
  ticketType: string | null;
  checkedInAt: string | null;
  ticketSource: string | null;
  issuedByName: string | null;
}

export interface InitialAdminSetupStatus { enabled: boolean; completed: boolean; }
export interface InitialAdminSetupResult { email: string; organizerSlug: string; }
export interface AssetUploadResult { secureUrl: string; publicId: string; purpose: 'ORGANIZER_LOGO' | 'EVENT_LOGO' | 'EVENT_BANNER' | 'EVENT_COVER'; }

export interface AdminOrganizer { id: string; slug: string; name: string; logoUrl?: string | null; description?: string | null; website?: string | null; }
export interface AdminOrganizerList { mediaStorageConfigured: boolean; organizers: AdminOrganizer[]; }

/* ---- Organizer-owned team ---- */
export interface TeamAssignment { eventId: string; eventName: string; gate?: string | null; }
export interface TeamMember {
  id: string; name: string; email: string; phone?: string | null; active: boolean; invitePending: boolean;
  createdAt: string; assignments: TeamAssignment[];
}
export interface TeamEventRef { id: string; name: string; status: string; startsAt: string; }
export interface TeamView {
  organizerId: string; slug: string; name: string; inviteEmailEnabled: boolean;
  events: TeamEventRef[]; owners: TeamMember[]; staff: TeamMember[]; managers: TeamMember[];
}
export type TeamKind = 'staff' | 'managers' | 'owners';
export interface CreateTeamMemberBody { name: string; email: string; phone?: string | null; password?: string | null; }
export interface CreatedTeamMember { member: TeamMember; delivery: 'INVITE_SENT' | 'INVITE_FAILED' | 'PASSWORD_SET'; }
export interface EventTeamStaffRow { userId: string; name: string; email: string; gate: string | null; active: boolean; }
export interface EventTeamManagerRow { userId: string; name: string; email: string; active: boolean; }
export interface EventTeam { eventId: string; eventName: string; staff: EventTeamStaffRow[]; managers: EventTeamManagerRow[]; }

export interface AdminIssuedTicket {
  ticketId: string; ticketNumber: string; attendeeName?: string; email?: string; phone?: string;
  ticketType: string; amountMinorUnits: number; currency: string; status: string; source: string;
  orderNumber: string; issuedByName?: string; issuedAt: string; checkedInAt?: string | null;
}
export interface AdminScopedTicket extends AdminIssuedTicket { eventId: string; eventName: string; eventSlug: string; }
export interface AdminOrder {
  orderId: string; orderNumber: string; customerName: string; customerEmail: string; customerPhone?: string | null;
  totalMinorUnits: number; currency: string; status: string; paymentStatus?: string; ticketCount: number; createdAt: string;
}
export interface AdminPage<T> { items: T[]; page: number; size: number; total: number; totalPages: number; }
export interface AdminOperationsPage<T> extends AdminPage<T> { eventId: string; eventName: string; }
export interface CursorPage<T> { items: T[]; nextCursor?: string | null; hasNext: boolean; size: number; }

export interface FinanceOverview { grossCapturedMinor: number; refundedMinor: number; netMinor: number; pendingPaymentCount: number; pendingRefundCount: number; recoveryPendingCount: number; failedMailCount: number; heldReservationCount: number; expiredReservationCount: number; webhookBacklogCount: number; webhookStuckCount: number; stalePaymentCount: number; oldestPendingPaymentAt?: string | null; }
export interface FinanceRefund { refundId: string; paymentId: string; orderNumber: string; customerName?: string | null; eventName: string; amountMinor: number; currency: string; status: string; providerStatus?: string | null; createdAt: string; }
export interface FinanceLedgerRow { entryId: string; entryType: 'SALE' | 'REFUND'; paymentId?: string | null; refundId?: string | null; orderNumber?: string | null; eventName?: string | null; organizerName?: string | null; amountMinor: number; currency: string; createdAt: string; }
export interface FinancePage<T> { items: T[]; page: number; size: number; total: number; totalPages: number; }

export interface OperationsHealth { application: string; version: string; checkedAt: string; database: { status: string; latencyMs: number; detail: string }; redis: { status: string; latencyMs: number; detail: string }; queues: { pendingPayments: number; stalePayments: number; providerOrderRecoveryPending: number; pendingRefunds: number; webhookBacklog: number; webhookStuck: number; heldReservations: number; expiredReservations: number; mailPending: number; mailFailed: number }; publishedEvents: number; organizers: number; workerEnabled: boolean; }

export interface AdminEventCursorPage { items: Dashboard['events']; nextCursor?: string | null; hasNext: boolean; size: number; total: number; }
