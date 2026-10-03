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
