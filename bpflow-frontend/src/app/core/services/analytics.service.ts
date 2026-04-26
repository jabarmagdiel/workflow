import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BottleneckInfo {
  node: string;
  avg_delay_hours: number;
  impact: 'High' | 'Medium' | 'Low';
}

export interface BottleneckResponse {
  workflow_id: string;
  bottlenecks: BottleneckInfo[];
  recommendation: string;
}

@Injectable({
  providedIn: 'root'
})
export class AnalyticsService {
  private http = inject(HttpClient);

  // Calls the Python AI Microservice for bottlenecks
  getBottlenecks(workflowId: string): Observable<BottleneckResponse> {
    return this.http.get<BottleneckResponse>(`/ai/analytics/bottlenecks`, {
      params: { workflow_id: workflowId }
    });
  }

  // Get active workflows to select from
  getWorkflows(): Observable<any[]> {
    return this.http.get<any[]>('/api/workflows');
  }
}
