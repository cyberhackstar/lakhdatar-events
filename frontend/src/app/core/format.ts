/** Locale/timezone-explicit formatting. Explicit timeZone keeps server render and browser hydration identical. */
const LOCALE = 'en-IN';
const DEFAULT_TZ = 'Asia/Kolkata';

function fmt(iso: string | undefined | null, tz: string | undefined, opts: Intl.DateTimeFormatOptions): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  try { return new Intl.DateTimeFormat(LOCALE, { timeZone: tz || DEFAULT_TZ, ...opts }).format(d); }
  catch { return new Intl.DateTimeFormat(LOCALE, { timeZone: DEFAULT_TZ, ...opts }).format(d); }
}

export const eventDay = (iso?: string, tz?: string) => fmt(iso, tz, { day: 'numeric' });
export const eventMonth = (iso?: string, tz?: string) => fmt(iso, tz, { month: 'short' }).toUpperCase();
export const eventWeekday = (iso?: string, tz?: string) => fmt(iso, tz, { weekday: 'short' });
export const eventDate = (iso?: string, tz?: string) => fmt(iso, tz, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
export const eventLongDate = (iso?: string, tz?: string) => fmt(iso, tz, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
export const eventTime = (iso?: string, tz?: string) => fmt(iso, tz, { hour: 'numeric', minute: '2-digit', hour12: true }).toUpperCase();
export const eventDateTime = (iso?: string, tz?: string) => fmt(iso, tz, { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit', hour12: true });

export function rupees(minor: number | null | undefined, currency = 'INR'): string {
  if (minor === null || minor === undefined) return '';
  const major = minor / 100;
  const whole = Number.isInteger(major);
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency, minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 }).format(major);
}

export function toAbsoluteUrl(siteUrl: string, url?: string | null): string | undefined {
  if (!url) return undefined;
  if (/^https:\/\//i.test(url)) return url;
  if (url.startsWith('/') && !url.startsWith('//')) return siteUrl.replace(/\/$/, '') + url;
  return undefined;
}

/** Only same-origin paths and https URLs may be used as image sources; anything else is dropped. */
export function safeImage(url?: string | null): string | null {
  if (!url) return null;
  if (url.startsWith('/') && !url.startsWith('//')) return url;
  return /^https:\/\//i.test(url) ? url : null;
}

export const SALES_LABEL: Record<string, string> = {
  AVAILABLE: 'Available', SELLING_FAST: 'Selling fast', SOLD_OUT: 'Sold out',
  BOOKING_NOT_STARTED: 'Booking opens soon', BOOKING_CLOSED: 'Booking closed', CANCELLED: 'Cancelled', COMPLETED: 'Completed'
};
export const canBook = (state?: string) => state === 'AVAILABLE' || state === 'SELLING_FAST';
