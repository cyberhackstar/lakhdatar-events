import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { HttpContextToken } from '@angular/common/http';

const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isAuthRoute = req.url.includes('/auth/');
  const isPublicApiRoute = /\/api\/v1\/public(?:\/|$)/.test(req.url);
  // These two auth endpoints are authenticated, unlike login/refresh/accept-invite.
  const needsToken = /\/auth\/(change-password|password-status)(\?|$)/.test(req.url);
  const token = auth.accessToken();
  const request = token && !isPublicApiRoute && (!isAuthRoute || needsToken)
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` }, withCredentials: true })
    : req.clone({ withCredentials: true });

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 403 && error?.error?.code === 'PASSWORD_CHANGE_REQUIRED' && !router.url.startsWith('/change-password')) {
        router.navigate(['/change-password'], { queryParams: { required: 1, returnUrl: router.url } });
        return throwError(() => error);
      }
      if (error.status !== 401 || isAuthRoute || isPublicApiRoute || request.context.get(AUTH_RETRIED)) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        switchMap(result => next(req.clone({
          setHeaders: { Authorization: `Bearer ${result.accessToken}` },
          withCredentials: true,
          context: req.context.set(AUTH_RETRIED, true)
        }))),
        catchError(refreshError => {
          auth.clearLocalSession();
          return throwError(() => refreshError);
        })
      );
    })
  );
};
