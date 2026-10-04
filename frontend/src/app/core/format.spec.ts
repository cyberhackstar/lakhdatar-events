import 'zone.js';
import 'zone.js/testing';
import { canBook, rupees, safeImage, toAbsoluteUrl } from './format';

describe('format utilities', () => {
  it('recognizes bookable sales states', () => {
    expect(canBook('AVAILABLE')).toBeTrue();
    expect(canBook('SELLING_FAST')).toBeTrue();
    expect(canBook('SOLD_OUT')).toBeFalse();
  });

  it('formats INR minor units', () => {
    expect(rupees(12345)).toContain('123.45');
    expect(rupees(50000)).toContain('500');
  });

  it('allows only safe image URL forms', () => {
    expect(safeImage('/assets/poster.png')).toBe('/assets/poster.png');
    expect(safeImage('https://cdn.example.com/poster.png')).toBe('https://cdn.example.com/poster.png');
    expect(safeImage('javascript:alert(1)')).toBeNull();
    expect(safeImage('//evil.example/poster.png')).toBeNull();
  });

  it('resolves same-origin and HTTPS absolute URLs', () => {
    expect(toAbsoluteUrl('https://events.neelastack.com', '/events/demo')).toBe('https://events.neelastack.com/events/demo');
    expect(toAbsoluteUrl('https://events.neelastack.com', 'https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png');
    expect(toAbsoluteUrl('https://events.neelastack.com', '//evil.example/a.png')).toBeUndefined();
  });
});
