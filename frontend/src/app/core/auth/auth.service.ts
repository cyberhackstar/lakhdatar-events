import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';
import { API_BASE_URL } from '../api/api.tokens';
import { AuthResponse, MfaEnrollment } from '../api/api.models';

const ROLE = 'lk_role';
const NAME = 'lk_full_name';
const LOGGED_OUT = 'lk_logged_out';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(API_BASE_URL);
  private access?: string;
  private refreshInFlight?: Observable<AuthResponse>;
  private sessionProbe?: Observable<boolean>;

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/login`, { email, password }, { withCredentials: true }).pipe(tap(r => this.store(r)));
  }

  /** Completes an emailed invite: sets the password, consumes the one-time token and signs the user in. */
  acceptInvite(token: string, password: string): Observable<AuthResponse> {
    this.sessionProbe = undefined;
    return this.http.post<AuthResponse>(`${this.base}/auth/accept-invite`, { token, password }, { withCredentials: true }).pipe(tap(r => this.store(r)));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/change-password`, { currentPassword, newPassword }, { withCredentials: true }).pipe(tap(r => this.store(r)));
  }

  mfaEnroll(challengeToken: string): Observable<MfaEnrollment> {
    return this.http.post<MfaEnrollment>(`${this.base}/auth/mfa/enroll`, { challengeToken });
  }

  mfaConfirm(challengeToken: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/mfa/confirm`, { challengeToken, code }, { withCredentials: true }).pipe(tap(r => this.store(r)));
  }

  mfaVerify(challengeToken: string, code: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/mfa/verify`, { challengeToken, code }, { withCredentials: true }).pipe(tap(r => this.store(r)));
  }

  requestPasswordReset(email: string): Observable<{ accepted: boolean }> {
    return this.http.post<{ accepted: boolean }>(`${this.base}/auth/password-reset/request`, { email });
  }

  completePasswordReset(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/auth/password-reset/complete`, { token, newPassword });
  }

  /** True while an account created with an initial password has not yet chosen its own. */
  mustChangePassword(): Observable<boolean> {
    return this.http.get<{ mustChangePassword: boolean }>(`${this.base}/auth/password-status`, { withCredentials: true }).pipe(map(r => !!r.mustChangePassword));
  }

  refresh(): Observable<AuthResponse> {
    if (this.refreshInFlight) return this.refreshInFlight;
    this.refreshInFlight = this.http.post<AuthResponse>(`${this.base}/auth/refresh`, {}, { withCredentials: true }).pipe(
      tap(r => this.store(r)),
      finalize(() => { this.refreshInFlight = undefined; }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    return this.refreshInFlight;
  }

  ensureSession(): Observable<boolean> {
    if (this.wasExplicitlyLoggedOut()) return of(false);
    if (this.access && this.role()) return of(true);
    if (this.sessionProbe) return this.sessionProbe;
    this.sessionProbe = this.refresh().pipe(
      map(() => true),
      catchError(() => { this.clearLocalSession(); return of(false); }),
      shareReplay({ bufferSize: 1, refCount: false }),
      finalize(() => { this.sessionProbe = undefined; })
    );
    return this.sessionProbe.pipe();
  }

  logout(): Observable<void> {
    this.markExplicitLogout();
    return this.http.post<void>(`${this.base}/auth/logout`, {}, { withCredentials: true }).pipe(
      tap(() => this.clearLocalSession()),
      catchError(error => { this.clearLocalSession(); return throwError(() => error); })
    );
  }

  store(r: AuthResponse): void {
    // A successful credential check may intentionally return no access token while MFA is pending.
    // Clear any stale browser session so a previous identity cannot survive a privileged re-auth flow.
    if (!r.accessToken) {
      this.clearLocalSession();
      return;
    }
    this.access = r.accessToken;
    try { localStorage.removeItem(LOGGED_OUT); } catch { /* optional storage */ }
    try { sessionStorage.setItem(ROLE, r.role); sessionStorage.setItem(NAME, r.fullName); } catch { /* access token remains memory-only */ }
  }

  clearLocalSession(): void {
    this.access = undefined;
    try { sessionStorage.removeItem(ROLE); sessionStorage.removeItem(NAME); } catch { /* optional storage */ }
  }

  accessToken(): string | null { return this.access ?? null; }
  role(): string | null { try { return sessionStorage.getItem(ROLE); } catch { return null; } }
  fullName(): string { try { return sessionStorage.getItem(NAME) || 'Operator'; } catch { return 'Operator'; } }
  isLoggedIn(): boolean { return !this.wasExplicitlyLoggedOut() && !!this.access; }
  private markExplicitLogout(): void { try { localStorage.setItem(LOGGED_OUT, '1'); } catch { /* optional storage */ } }
  private wasExplicitlyLoggedOut(): boolean { try { return localStorage.getItem(LOGGED_OUT) === '1'; } catch { return false; } }
}
