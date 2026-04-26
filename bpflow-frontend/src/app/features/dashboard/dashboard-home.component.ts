import { Component, inject, signal, OnInit, AfterViewInit, ViewChild, ElementRef, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import Chart from 'chart.js/auto';
import { NotificationService } from '../../core/services/notification.service';
import { Subscription } from 'rxjs';

interface DashboardData {
  workflows: number;
  instances: { running: number; completed: number; cancelled: number };
  tasks: { new: number; inProgress: number; completed: number };
  totalUsers: number;
}

@Component({
  selector: 'app-dashboard-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard-home.component.html',
  styleUrls: ['./dashboard-home.component.css']
})
export class DashboardHomeComponent implements OnInit, OnDestroy {
  private http = inject(HttpClient);
  private notifService = inject(NotificationService);
  
  @ViewChild('tasksChart') tasksChartCanvas!: ElementRef;
  @ViewChild('instancesChart') instancesChartCanvas!: ElementRef;
  
  data = signal<DashboardData | null>(null);
  loading = signal(true);
  error = signal(false);
  
  private charts: Chart[] = [];
  private eventSubscription?: Subscription;

  ngOnInit() {
    this.loadData();
    
    // Listen for WebSocket events to global refresh
    this.eventSubscription = this.notifService.events$.subscribe(event => {
      if (event.event === 'DASHBOARD_UPDATE') {
        console.log('🔄 Real-time Dashboard Update triggered');
        this.loadData();
      }
    });
  }

  ngOnDestroy() {
    this.charts.forEach(c => c.destroy());
    this.eventSubscription?.unsubscribe();
  }

  loadData() {
    this.http.get<DashboardData>('/api/analytics/dashboard').subscribe({
      next: (d: DashboardData) => { 
        this.data.set(d); 
        this.loading.set(false);
        setTimeout(() => this.initCharts(), 0);
      },
      error: () => { 
        this.loading.set(false); 
        this.error.set(true); 
      }
    });
  }

  initCharts() {
    const d = this.data();
    if (!d) return;

    this.charts.forEach(c => c.destroy());
    this.charts = [];

    // Tasks Doughnut Chart
    if (this.tasksChartCanvas) {
      const tasksChart = new Chart(this.tasksChartCanvas.nativeElement, {
        type: 'doughnut',
        data: {
          labels: ['Nuevas', 'En Progreso', 'Completadas'],
          datasets: [{
            data: [d.tasks.new, d.tasks.inProgress, d.tasks.completed],
            backgroundColor: ['#06b6d4', '#f59e0b', '#10b981'],
            borderColor: '#0f172a',
            borderWidth: 2,
            hoverOffset: 15
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          cutout: '70%',
          plugins: {
            legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 10 } } }
          }
        }
      });
      this.charts.push(tasksChart);
    }

    // Instances Polar Area Chart
    if (this.instancesChartCanvas) {
      const instancesChart = new Chart(this.instancesChartCanvas.nativeElement, {
        type: 'polarArea',
        data: {
          labels: ['Corriendo', 'Completados', 'Cancelados'],
          datasets: [{
            data: [d.instances.running, d.instances.completed, d.instances.cancelled],
            backgroundColor: [
              'rgba(59, 130, 246, 0.6)',
              'rgba(16, 185, 129, 0.6)',
              'rgba(239, 68, 68, 0.6)'
            ],
            borderColor: '#1e293b'
          }]
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          scales: {
            r: {
              ticks: { display: false },
              grid: { color: 'rgba(255, 255, 255, 0.05)' }
            }
          },
          plugins: {
            legend: { position: 'bottom', labels: { color: '#94a3b8', font: { size: 10 } } }
          }
        }
      });
      this.charts.push(instancesChart);
    }
  }

  taskPercent(key: 'new' | 'inProgress' | 'completed'): number {
    const d = this.data();
    if (!d) return 0;
    const total = (d.tasks.new || 0) + (d.tasks.inProgress || 0) + (d.tasks.completed || 0);
    if (total === 0) return 0;
    const val = key === 'new' ? d.tasks.new : key === 'inProgress' ? d.tasks.inProgress : d.tasks.completed;
    return Math.round((val / total) * 100);
  }

  successRate(): number {
    const d = this.data();
    if (!d) return 0;
    const total = (d.instances.running || 0) + (d.instances.completed || 0) + (d.instances.cancelled || 0);
    if (total === 0) return 0;
    return Math.round((d.instances.completed / total) * 100);
  }
}
