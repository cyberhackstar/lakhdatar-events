import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { HttpContextToken } from '@angular/common/http';

const AUTH_RETRIED = new HttpContextToken<boolean>(() => false);

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isAuthRoute = req.url.includes('/auth/');
  const token = auth.accessToken();
  const request = token && !isAuthRoute
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` }, withCredentials: true })
    : req.clone({ withCredentials: true });

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status !== 401 || isAuthRoute || request.context.get(AUTH_RETRIED)) {
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
