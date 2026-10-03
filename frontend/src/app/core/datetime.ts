/** Date helpers shared by the admin pages. All admin date inputs are interpreted in the event's own timezone. */
export const DEFAULT_TZ = 'Asia/Kolkata';

export const TIMEZONES = [
  'Asia/Kolkata', 'Asia/Dubai', 'Asia/Singapore', 'Europe/London', 'Europe/Paris',
  'America/New_York', 'America/Los_Angeles', 'Australia/Sydney'
];

function offsetMinutes(date: Date, timezone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: timezone, timeZoneName: 'longOffset', hour: '2-digit', minute: '2-digit', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(date);
  const raw = parts.find(p => p.type === 'timeZoneName')?.value || 'GMT';
  const match = raw.match(/GMT([+-])(\d{2}):(\d{2})/);
  if (!match) return 0;
  const mins = Number(match[2]) * 60 + Number(match[3]);
  return match[1] === '-' ? -mins : mins;
}

/** "2026-10-17T19:30" (a datetime-local value) in the given timezone -> UTC ISO instant. */
export function toIsoInZone(local: string | null | undefined, timezone: string = DEFAULT_TZ): string | undefined {
  if (!local) return undefined;
  const m = local.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  if (!m) return undefined;
  const [year, month, day, hour, minute] = m.slice(1).map(Number);
  const naiveMs = Date.UTC(year, month - 1, day, hour, minute);
  const offset1 = offsetMinutes(new Date(naiveMs), timezone);
  const candidate = new Date(naiveMs - offset1 * 60000);
  const offset2 = offsetMinutes(candidate, timezone);
  return new Date(naiveMs - offset2 * 60000).toISOString();
}

/** UTC ISO instant -> datetime-local value in the given timezone. */
export function toLocalInput(iso: string | undefined | null, timezone: string = DEFAULT_TZ): string {
  if (!iso) return '';
  try {
    const parts = new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(iso));
    const get = (type: string) => parts.find(p => p.type === type)?.value || '';
    return `${get('year')}-${get('month')}-${get('day')}T${get('hour')}:${get('minute')}`;
  } catch { return ''; }
}

export function slugify(value: string): string {
  return value.toLowerCase().normalize('NFKD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 80).replace(/-+$/g, '');
}
