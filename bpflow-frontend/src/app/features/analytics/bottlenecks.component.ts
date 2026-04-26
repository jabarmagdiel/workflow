import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AnalyticsService, BottleneckResponse } from '../../core/services/analytics.service';
import Chart from 'chart.js/auto';

@Component({
  selector: 'app-bottlenecks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bottlenecks.component.html',
  styleUrls: ['./bottlenecks.component.css']
})
export class BottlenecksComponent implements OnInit {
  private analyticsService = inject(AnalyticsService);

  workflows = signal<any[]>([]);
  selectedWorkflow = signal<string | null>(null);
  
  loading = signal<boolean>(false);
  error = signal<string | null>(null);
  
  aiResult = signal<BottleneckResponse | null>(null);
  private chartInstance: Chart | null = null;

  ngOnInit(): void {
    this.analyticsService.getWorkflows().subscribe({
      next: (data) => {
        this.workflows.set(data);
        if (data.length > 0) {
          this.selectedWorkflow.set(data[0].id);
          this.analyzeBottlenecks();
        }
      },
      error: (err) => this.error.set('Could not load workflows.')
    });
  }

  analyzeBottlenecks() {
    const wfId = this.selectedWorkflow();
    if (!wfId) return;

    this.loading.set(true);
    this.error.set(null);
    this.aiResult.set(null);

    this.analyticsService.getBottlenecks(wfId).subscribe({
      next: (res) => {
        this.aiResult.set(res);
        this.loading.set(false);
        setTimeout(() => this.renderChart(res), 50);
      },
      error: (err) => {
        this.error.set('AI Analysis failed or service is down.');
        this.loading.set(false);
      }
    });
  }

  onWorkflowChange(event: Event) {
    const target = event.target as HTMLSelectElement;
    this.selectedWorkflow.set(target.value);
    this.analyzeBottlenecks();
  }

  renderChart(res: BottleneckResponse) {
    const canvas = document.getElementById('bottleneckChart') as HTMLCanvasElement;
    if (!canvas) return;

    if (this.chartInstance) {
      this.chartInstance.destroy();
    }

    const labels = res.bottlenecks.map(b => b.node);
    const data = res.bottlenecks.map(b => b.avg_delay_hours);
    const bgColors = res.bottlenecks.map(b => 
      b.impact === 'High' ? 'rgba(239, 68, 68, 0.7)' : 
      b.impact === 'Medium' ? 'rgba(245, 158, 11, 0.7)' : 
      'rgba(16, 185, 129, 0.7)'
    );

    this.chartInstance = new Chart(canvas, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [{
          label: 'Avg Delay (Hours)',
          data: data,
          backgroundColor: bgColors,
          borderColor: bgColors.map(c => c.replace('0.7', '1')),
          borderWidth: 1,
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: 'rgba(15, 23, 42, 0.9)',
            titleColor: '#fff',
            bodyColor: '#94a3b8',
            borderColor: 'rgba(255,255,255,0.1)',
            borderWidth: 1
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            grid: { color: 'rgba(255, 255, 255, 0.05)' },
            ticks: { color: '#94a3b8' }
          },
          x: {
            grid: { display: false },
            ticks: { color: '#94a3b8' }
          }
        }
      }
    });
  }
}
