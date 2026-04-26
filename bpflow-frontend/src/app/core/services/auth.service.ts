import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap, catchError } from 'rxjs/operators';
import { of, BehaviorSubject } from 'rxjs';

export interface User {
    id: string;
    email: string;
    firstName: string;
    lastName: string;
    roles: string[];
    department?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
    private http = inject(HttpClient);
    private router = inject(Router);
    private apiUrl = '/api/auth';

    // Signals for reactive state
    currentUser = signal<User | null>(null);
    isAuthenticated = signal<boolean>(false);
    token = signal<string | null>(null);

    constructor() {
        this.loadSession();
    }

    login(credentials: any) {
        return this.http.post<any>(`${this.apiUrl}/login`, credentials).pipe(
            tap(res => this.saveSession(res)),
            catchError(err => {
                console.error('Login failed', err);
                throw err;
            })
        );
    }

    logout() {
        this.currentUser.set(null);
        this.isAuthenticated.set(false);
        this.token.set(null);
        localStorage.removeItem('bpflow_token');
        localStorage.removeItem('bpflow_user');
        this.router.navigate(['/login']);
    }

    private saveSession(res: any) {
        localStorage.setItem('bpflow_token', res.accessToken);
        localStorage.setItem('bpflow_user', JSON.stringify(res.user));
        this.currentUser.set(res.user);
        this.token.set(res.accessToken);
        this.isAuthenticated.set(true);
    }

    private loadSession() {
        const token = localStorage.getItem('bpflow_token');
        const userJson = localStorage.getItem('bpflow_user');
        if (token && userJson) {
            this.currentUser.set(JSON.parse(userJson));
            this.token.set(token);
            this.isAuthenticated.set(true);
        }
    }

    hasRole(role: string): boolean {
        return this.currentUser()?.roles.includes(role) || false;
    }
}
