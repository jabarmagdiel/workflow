import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

// Sincronizado con FraudAlert.java del backend
interface FraudAlert {
  id: string;
  instanceId: string;
  workflowId: string;
  taskId: string;
  userId: string;
  alertType: 'ANOMALOUS_TIMING' | 'SUSPICIOUS_USER' | 'REPETITIVE_APPROVAL' | 'FLOW_SKIP' | 'UNUSUAL_REASSIGNMENT' | 'OFF_HOURS_ACTIVITY' | 'HIGH_REJECTION_RATE' | 'BULK_APPROVAL';
  description: string;
  riskScore: number;  // 0.0 - 1.0
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  status: 'OPEN' | 'UNDER_REVIEW' | 'RESOLVED' | 'FALSE_POSITIVE';
  indicators: string[];
  reviewedBy?: string;
  reviewedAt?: string;
  reviewNotes?: string;
  detectedAt: string;
}

@Component({
  selector: 'app-fraud-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './fraud-dashboard.component.html',
  styleUrl: './fraud-dashboard.component.css'
})
export class FraudDashboardComponent implements OnInit {
  private http = inject(HttpClient);

  alerts = signal<FraudAlert[]>([]);
  loading = signal(true);
  activeFilter = signal('ALL');

  statusTabs = [
    { label: 'Todas', key: 'ALL' },
    { label: 'Abiertas', key: 'OPEN' },
    { label: 'En Revisión', key: 'UNDER_REVIEW' },
    { label: 'Resueltas', key: 'RESOLVED' }
  ];

  filteredAlerts() {
    const f = this.activeFilter();
    return f === 'ALL' ? this.alerts() : this.alerts().filter((a: FraudAlert) => a.status === f);
  }

  ngOnInit() { this.loadAlerts(); }

  loadAlerts() {
    this.loading.set(true);
    this.http.get<FraudAlert[]>('/api/fraud/alerts').subscribe({
      next: (data: FraudAlert[]) => { this.alerts.set(data); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  updateAlertStatus(id: string, status: string) {
    this.http.patch<FraudAlert>(`/api/fraud/alerts/${id}/status`, { status }).subscribe({
      next: (updated: FraudAlert) => {
        this.alerts.update((list: FraudAlert[]) => list.map((a: FraudAlert) => a.id === updated.id ? updated : a));
      },
      error: (err: { status: number }) => console.error('Error actualizando alerta', err)
    });
  }

  countByStatus(status: string): number {
    return this.alerts().filter((a: FraudAlert) => a.status === status).length;
  }

  countBySeverity(severity: string): number {
    return this.alerts().filter((a: FraudAlert) => a.severity === severity).length;
  }

  severityBorderClass(s: string): string {
    const m: Record<string, string> = {
      CRITICAL: 'border-red-700/70',
      HIGH: 'border-orange-700/70',
      MEDIUM: 'border-amber-800/60',
      LOW: 'border-slate-700'
    };
    return m[s] ?? m['LOW'];
  }

  severityBadge(s: string): string {
    const m: Record<string, string> = {
      CRITICAL: 'bg-red-950 text-red-400',
      HIGH: 'bg-orange-950 text-orange-400',
      MEDIUM: 'bg-amber-950 text-amber-400',
      LOW: 'bg-slate-800 text-slate-400'
    };
    return m[s] ?? m['LOW'];
  }

  alertStatusClass(s: string): string {
    const m: Record<string, string> = {
      OPEN: 'bg-red-950 text-red-400',
      UNDER_REVIEW: 'bg-blue-950 text-blue-400',
      RESOLVED: 'bg-emerald-950 text-emerald-400',
      FALSE_POSITIVE: 'bg-slate-800 text-slate-400'
    };
    return m[s] ?? m['OPEN'];
  }
}
