import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { catchError } from 'rxjs/operators';
import { throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    const token = localStorage.getItem('bpflow_token');

    const authReq = token
        ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
        : req;

    return next(authReq).pipe(
        catchError((err: { status: number; url?: string }) => {
            if (err.status === 401 && !authReq.url.includes('/auth/login')) {
                localStorage.removeItem('bpflow_token');
                localStorage.removeItem('bpflow_user');
                window.location.href = '/login';
            }
            return throwError(() => err);
        })
    );
};
