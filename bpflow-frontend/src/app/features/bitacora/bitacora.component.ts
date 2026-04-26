import { Component, inject, signal, OnInit, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface AuditLog {
  id: string;
  userId: string;
  userEmail: string;
  userRole: string;
  action: string;
  resourceType: string;
  resourceId: string;
  ipAddress: string;
  success: boolean;
  detail: string;
  timestamp: string;
}

interface LogPage {
  content: AuditLog[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

interface Stats {
  total: number;
  success: number;
  failures: number;
  byAction: Record<string, number>;
  byResource: Record<string, number>;
}

interface BackupStats {
  workflows: number;
  instances: number;
  tasks: number;
  auditLogs: number;
  generatedAt: string;
}

@Component({
  selector: 'app-bitacora',
  standalone: true,
  imports: [CommonModule, FormsModule, DatePipe],
  templateUrl: './bitacora.component.html',
  styleUrl: './bitacora.component.css'
})
export class BitacoraComponent implements OnInit {
  private http = inject(HttpClient);

  /* ── State ───────────────────────────────── */
  logs         = signal<AuditLog[]>([]);
  stats        = signal<Stats | null>(null);
  backupStats  = signal<BackupStats | null>(null);
  totalPages   = signal(1);
  totalItems   = signal(0);
  loading      = signal(true);
  exporting    = signal(false);
  importing    = signal(false);

  /* ── Tabs ────────────────────────────────── */
  activeTab = signal<'logs' | 'backup'>('logs');

  /* ── Filters ─────────────────────────────── */
  page         = signal(0);
  pageSize     = 50;
  filterUserId = signal('');
  filterAction = signal('');
  filterType   = signal('');
  filterFrom   = signal('');
  filterTo     = signal('');

  /* ── Dropdown options ─────────────────────── */
  readonly ACTION_OPTIONS = [
    'CREATE_WORKFLOW','PUBLISH_WORKFLOW','DELETE_WORKFLOW',
    'TASK_STARTED','TASK_COMPLETED',
    'START_INSTANCE','COMPLETE_INSTANCE',
    'LOGIN','LOGOUT'
  ];
  readonly TYPE_OPTIONS = ['WORKFLOW','TASK','INSTANCE','USER'];

  ngOnInit() {
    this.loadLogs();
    this.loadStats();
    this.loadBackupStats();
  }

  /* ── Data loading ─────────────────────────── */
  loadLogs() {
    this.loading.set(true);
    const params: Record<string, string> = {
      page: this.page().toString(),
      size: this.pageSize.toString()
    };
    if (this.filterUserId()) params['userId']       = this.filterUserId();
    if (this.filterAction()) params['action']        = this.filterAction();
    if (this.filterType())   params['resourceType']  = this.filterType();
    if (this.filterFrom())   params['from']           = this.filterFrom() + ':00';
    if (this.filterTo())     params['to']             = this.filterTo()   + ':00';

    this.http.get<LogPage>('/api/audit/logs', { params }).subscribe({
      next: p => {
        this.logs.set(p.content);
        this.totalPages.set(p.totalPages);
        this.totalItems.set(p.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  loadStats() {
    this.http.get<Stats>('/api/audit/logs/stats').subscribe({
      next: s => this.stats.set(s)
    });
  }

  loadBackupStats() {
    this.http.get<BackupStats>('/api/audit/backup/stats').subscribe({
      next: s => this.backupStats.set(s)
    });
  }

  applyFilters() {
    this.page.set(0);
    this.loadLogs();
  }

  clearFilters() {
    this.filterUserId.set('');
    this.filterAction.set('');
    this.filterType.set('');
    this.filterFrom.set('');
    this.filterTo.set('');
    this.page.set(0);
    this.loadLogs();
  }

  gotoPage(p: number) {
    if (p < 0 || p >= this.totalPages()) return;
    this.page.set(p);
    this.loadLogs();
  }

  pagesArray(): number[] {
    const tp = this.totalPages();
    const cur = this.page();
    const range: number[] = [];
    const delta = 2;
    for (let i = Math.max(0, cur - delta); i <= Math.min(tp - 1, cur + delta); i++) {
      range.push(i);
    }
    return range;
  }

  /* ── Backup export ────────────────────────── */
  exportBackup() {
    this.exporting.set(true);
    this.http.get('/api/audit/backup/export', { responseType: 'blob' }).subscribe({
      next: blob => {
        const url  = URL.createObjectURL(blob);
        const a    = document.createElement('a');
        a.href     = url;
        a.download = `bpflow-backup-${new Date().toISOString().slice(0,10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
        this.loadBackupStats();
      },
      error: () => this.exporting.set(false)
    });
  }

  /* ── Backup import ────────────────────────── */
  triggerImport() {
    document.getElementById('backup-file-input')?.click();
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    if (!file.name.endsWith('.json')) {
      alert('Solo se aceptan archivos .json generados por BPFlow.');
      return;
    }
    if (!confirm(`¿Restaurar backup desde "${file.name}"? Solo se importarán entidades que no existan.`)) {
      return;
    }
    this.importing.set(true);
    const form = new FormData();
    form.append('file', file);
    this.http.post<any>('/api/audit/backup/import', form).subscribe({
      next: res => {
        alert(`✅ ${res.message}\nWorkflows restaurados: ${res.workflowsRestored}`);
        this.importing.set(false);
        this.loadBackupStats();
        this.loadLogs();
      },
      error: () => {
        alert('❌ Error al importar el backup.');
        this.importing.set(false);
      }
    });
    input.value = '';
  }

  /* ── Helpers ──────────────────────────────── */
  actionIcon(action: string): string {
    const m: Record<string, string> = {
      CREATE_WORKFLOW: '📐', PUBLISH_WORKFLOW: '🚀', DELETE_WORKFLOW: '🗑',
      TASK_STARTED:   '▶',  TASK_COMPLETED:   '✅', TASK_REJECTED:   '✕',
      START_INSTANCE: '🔄', COMPLETE_INSTANCE: '🏁',
      LOGIN: '🔑', LOGOUT: '🚪', BACKUP: '💾', RESTORE: '📥'
    };
    return m[action] ?? '📋';
  }

  actionClass(action: string): string {
    if (action?.includes('DELETE') || action?.includes('REJECT')) return 'badge-red';
    if (action?.includes('PUBLISH') || action?.includes('COMPLETE')) return 'badge-green';
    if (action?.includes('CREATE') || action?.includes('START'))    return 'badge-blue';
    if (action === 'LOGIN' || action === 'LOGOUT')                  return 'badge-purple';
    return 'badge-gray';
  }

  statsEntries(obj: Record<string, number> | undefined): { key: string; val: number }[] {
    if (!obj) return [];
    return Object.entries(obj)
      .map(([key, val]) => ({ key, val }))
      .sort((a, b) => b.val - a.val)
      .slice(0, 8);
  }

  maxVal(obj: Record<string, number> | undefined): number {
    if (!obj) return 1;
    return Math.max(...Object.values(obj), 1);
  }

  barWidth(val: number, max: number): string {
    return Math.round((val / max) * 100) + '%';
  }

  trackById(_: number, item: AuditLog) { return item.id; }
}
