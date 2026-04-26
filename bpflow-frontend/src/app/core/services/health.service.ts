import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of, timer } from 'rxjs';
import { catchError, map, switchMap, repeat } from 'rxjs/operators';

export interface ServiceStatus {
  name: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  details?: any;
  latency?: number;
}

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private http = inject(HttpClient);
  
  systemStatus = signal<ServiceStatus[]>([]);

  constructor() {
    this.startMonitoring();
  }

  private startMonitoring() {
    timer(0, 10000).pipe(
      switchMap(() => {
        const startTime = Date.now();
        return forkJoin({
          backend: this.http.get<any>('/api/actuator/health').pipe(
            map(res => ({ name: 'Backend Services', status: res.status, details: res.components, latency: Date.now() - startTime })),
            catchError(() => of({ name: 'Backend Services', status: 'DOWN', latency: -1 }))
          ),
          ai: this.http.get<any>('/ai/health').pipe(
            map(res => ({ name: 'AI Microservice', status: res.status === 'healthy' ? 'UP' : 'DOWN', details: res, latency: Date.now() - startTime })),
            catchError(() => of({ name: 'AI Microservice', status: 'DOWN', latency: -1 }))
          )
        });
      })
    ).subscribe(results => {
      this.systemStatus.set([results.backend as ServiceStatus, results.ai as ServiceStatus]);
    });
  }
}
