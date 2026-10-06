import 'zone.js';
import 'zone.js/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService } from './auth.service';
import { API_BASE_URL } from '../api/api.tokens';

const sessionResponse = {
  accessToken: 'access-123',
  refreshToken: '',
  tokenType: 'Bearer',
  role: 'ADMIN',
  fullName: 'Operator',
  mfaRequired: false,
  mfaSetupRequired: false,
  mfaChallengeToken: null
};

describe('AuthService enterprise auth lifecycle', () => {
  let auth: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_BASE_URL, useValue: '/api/v1' }
      ]
    });
    auth = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    localStorage.clear();
    sessionStorage.clear();
  });

  it('never stores blank access tokens returned for a pending MFA challenge', () => {
    auth.login('admin@example.com', 'correct-password').subscribe(result => {
      expect(result.mfaRequired).toBeTrue();
      expect(result.accessToken).toBe('');
    });
    const req = http.expectOne('/api/v1/auth/login');
    req.flush({
      accessToken: '', refreshToken: '', tokenType: 'Bearer', role: 'ADMIN', fullName: 'Admin',
      mfaRequired: true, mfaSetupRequired: false, mfaChallengeToken: 'challenge-123'
    });
    expect(auth.accessToken()).toBeNull();
    expect(auth.role()).toBeNull();
  });

  it('clears any stale session when login pauses for MFA', () => {
    auth.mfaVerify('old-challenge', '123456').subscribe();
    const oldReq = http.expectOne('/api/v1/auth/mfa/verify');
    oldReq.flush(sessionResponse);
    expect(auth.accessToken()).toBe('access-123');

    auth.login('admin@example.com', 'correct-password').subscribe();
    const loginReq = http.expectOne('/api/v1/auth/login');
    loginReq.flush({
      accessToken: '', refreshToken: '', tokenType: 'Bearer', role: 'ADMIN', fullName: 'Admin',
      mfaRequired: true, mfaSetupRequired: false, mfaChallengeToken: 'challenge-new'
    });
    expect(auth.accessToken()).toBeNull();
    expect(auth.role()).toBeNull();
  });

  it('stores the session only after MFA verification issues real tokens', () => {
    auth.mfaVerify('challenge-123', '123456').subscribe();
    const req = http.expectOne('/api/v1/auth/mfa/verify');
    expect(req.request.body).toEqual({ challengeToken: 'challenge-123', code: '123456' });
    req.flush(sessionResponse);
    expect(auth.accessToken()).toBe('access-123');
    expect(auth.role()).toBe('ADMIN');
    expect(auth.fullName()).toBe('Operator');
  });

  it('uses a fragment-safe password recovery API contract', () => {
    auth.requestPasswordReset('user@example.com').subscribe(result => expect(result.accepted).toBeTrue());
    const request = http.expectOne('/api/v1/auth/password-reset/request');
    expect(request.request.body).toEqual({ email: 'user@example.com' });
    request.flush({ accepted: true });

    auth.completePasswordReset('url-safe-token', 'a-new-long-password').subscribe();
    const complete = http.expectOne('/api/v1/auth/password-reset/complete');
    expect(complete.request.body).toEqual({ token: 'url-safe-token', newPassword: 'a-new-long-password' });
    complete.flush(null);
  });
});
