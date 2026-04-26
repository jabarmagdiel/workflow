import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface User {
  id?: string;
  email: string;
  password?: string;
  firstName: string;
  lastName: string;
  phone?: string;
  department?: string;
  position?: string;
  roles: string[];
  enabled?: boolean;
  createdAt?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserService {
  private http = inject(HttpClient);
  private apiUrl = '/api/users';
  private authUrl = '/api/auth';

  getUsers(): Observable<User[]> {
    return this.http.get<User[]>(this.apiUrl);
  }

  getUser(id: string): Observable<User> {
    return this.http.get<User>(`${this.apiUrl}/${id}`);
  }

  createUser(user: any): Observable<any> {
    return this.http.post(`${this.authUrl}/register`, user);
  }

  updateUser(id: string, user: Partial<User>): Observable<User> {
    return this.http.put<User>(`${this.apiUrl}/${id}`, user);
  }

  updateUserRoles(id: string, roles: string[]): Observable<User> {
    return this.http.patch<User>(`${this.apiUrl}/${id}/roles`, { roles });
  }

  toggleUserStatus(id: string): Observable<{setEnabled: boolean}> {
    return this.http.patch<{setEnabled: boolean}>(`${this.apiUrl}/${id}/toggle-status`, {});
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
