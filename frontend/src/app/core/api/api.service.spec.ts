import 'zone.js';
import 'zone.js/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { API_BASE_URL } from './api.tokens';

describe('ApiService optional query parameters', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' }
      ]
    });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('omits undefined admin event filters instead of sending the literal string undefined', () => {
    api.adminEventsCursor({ q: undefined, status: undefined, cursor: undefined, size: 50 }).subscribe();
    const req = http.expectOne('/api/v1/admin/events/cursor?size=50');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    expect(req.request.params.has('cursor')).toBe(false);
    req.flush({ items: [], nextCursor: null, hasNext: false, size: 50, total: 0 });
  });

  it('accepts the typed EventQuery model and preserves false/zero values', () => {
    api.events({ q: undefined, featured: false, page: 0, size: 20 }).subscribe();
    const req = http.expectOne('/api/v1/public/events?featured=false&page=0&size=20');
    expect(req.request.params.has('q')).toBe(false);
    expect(req.request.params.get('featured')).toBe('false');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    req.flush({ items: [], page: 0, size: 20, totalPages: 0, totalElements: 0 });
  });


  it('requests attendee CSV through the authenticated API path as a blob', () => {
    api.attendeesCsv('event-123').subscribe();
    const req = http.expectOne('/api/v1/admin/events/event-123/attendees.csv');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['name,email\nGuest,guest@example.com\n'], { type: 'text/csv' }));
  });

  it('keeps real optional filter values', () => {
    api.allIssuedTicketsCursor({ q: 'bhawesh', status: 'ACTIVE', source: 'PUBLIC', eventId: undefined, size: 25 }).subscribe();
    const req = http.expectOne(r => r.url === '/api/v1/admin/tickets/cursor' && r.params.get('q') === 'bhawesh');
    expect(req.request.params.get('status')).toBe('ACTIVE');
    expect(req.request.params.get('source')).toBe('PUBLIC');
    expect(req.request.params.get('size')).toBe('25');
    expect(req.request.params.has('eventId')).toBe(false);
    req.flush({ items: [], nextCursor: null, hasNext: false, size: 25, total: 0 });
  });
});
