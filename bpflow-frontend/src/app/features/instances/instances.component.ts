import { Component, inject, signal, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface WorkflowNode {
  id: string;
  label: string;
  type: string;
  assignedRole?: string;
}

interface Workflow {
  id: string;
  name: string;
  description: string;
  status: string;
  version: number;
  nodes: WorkflowNode[];
}

interface ExecutionStep {
  nodeId: string;
  nodeName: string;
  performedBy: string;
  action: string;
  comment: string;
  timestamp: string;
  durationMs: number;
}

interface WorkflowInstance {
  id: string;
  workflowId: string;
  workflowName: string;
  workflowVersion: number;
  status: 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'SUSPENDED' | 'ERROR';
  currentNodeId: string;
  activeNodeIds: string[];
  referenceNumber: string;
  priority: string;
  initiatedBy: string;
  clientId: string;
  startedAt: string;
  completedAt?: string;
  dueAt?: string;
  slaBreached: boolean;
  riskScore: number;
  history: ExecutionStep[];
  variables: Record<string, unknown>;
}

interface Task {
  id: string;
  instanceId: string;
  workflowName: string;
  nodeId: string;
  nodeName: string;
  title: string;
  status: 'NEW' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
  assignedTo: string;
  assignedRole: string;
  priority: string;
  dueAt?: string;
  createdAt: string;
  comment?: string;
}

@Component({
  selector: 'app-instances',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './instances.component.html',
  styleUrl: './instances.component.css'
})
export class InstancesComponent implements OnInit {
  private http = inject(HttpClient);

  // Data
  workflows   = signal<Workflow[]>([]);
  instances   = signal<WorkflowInstance[]>([]);
  tasks       = signal<Task[]>([]);
  selectedInst= signal<WorkflowInstance | null>(null);
  instTasks   = signal<Task[]>([]);

  // UI state
  loading     = signal(true);
  activeTab   = signal<'instances' | 'tasks'>('instances');
  showStartModal   = signal(false);
  showTaskModal    = signal(false);
  showDetailModal  = signal(false);
  submitting  = signal(false);

  // Forms
  startForm = { workflowId: '', clientId: '', priority: 'NORMAL', notes: '' };
  taskForm  = { action: 'APPROVE', comment: '' };
  selectedTask = signal<Task | null>(null);

  readonly PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'];

  ngOnInit() {
    this.loadAll();
  }

  loadAll() {
    this.loading.set(true);
    // Load published workflows
    this.http.get<Workflow[]>('/api/workflows').subscribe({
      next: wfs => this.workflows.set(wfs.filter(w => w.status === 'PUBLISHED')),
      error: () => {}
    });
    // Load all instances
    this.http.get<WorkflowInstance[]>('/api/instances').subscribe({
      next: inst => { this.instances.set(inst); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
    // Load all tasks for current user (by role, backend filters)
    this.http.get<Task[]>('/api/tasks').subscribe({
      next: t => this.tasks.set(t),
      error: () => {}
    });
  }

  // ── Start modal ────────────────────────────────────────────────
  openStartModal() {
    const f1 = this.workflows().find(w => w.name === 'f1-proces');
    this.startForm = {
      workflowId: f1?.id ?? (this.workflows()[0]?.id ?? ''),
      clientId: '',
      priority: 'NORMAL',
      notes: ''
    };
    this.showStartModal.set(true);
  }

  startInstance() {
    if (!this.startForm.workflowId) return;
    this.submitting.set(true);
    this.http.post<WorkflowInstance>('/api/instances/start', {
      workflowId: this.startForm.workflowId,
      clientId: this.startForm.clientId || undefined,
      priority: this.startForm.priority,
      variables: this.startForm.notes ? { notes: this.startForm.notes } : {}
    }).subscribe({
      next: inst => {
        this.instances.update(list => [inst, ...list]);
        this.showStartModal.set(false);
        this.submitting.set(false);
        this.viewDetail(inst);
      },
      error: (e) => {
        alert('Error al iniciar: ' + (e?.error?.message || 'Verifica que el usuario tenga rol MANAGER o ADMIN.'));
        this.submitting.set(false);
      }
    });
  }

  // ── Detail modal ───────────────────────────────────────────────
  viewDetail(inst: WorkflowInstance) {
    this.selectedInst.set(inst);
    this.showDetailModal.set(true);
    this.http.get<Task[]>(`/api/tasks/by-instance/${inst.id}`).subscribe({
      next: all => this.instTasks.set(all),
      error: () => {}
    });
  }

  refreshDetail() {
    const inst = this.selectedInst();
    if (!inst) return;
    this.http.get<WorkflowInstance>(`/api/instances/${inst.id}`).subscribe({
      next: updated => {
        this.selectedInst.set(updated);
        this.instances.update(list => list.map(i => i.id === updated.id ? updated : i));
      }
    });
  }

  // ── Task modal ─────────────────────────────────────────────────
  openTaskModal(task: Task) {
    this.selectedTask.set(task);
    this.taskForm = { action: 'APPROVE', comment: '' };
    this.showTaskModal.set(true);
  }

  completeTask() {
    const t = this.selectedTask();
    if (!t) return;
    this.submitting.set(true);
    this.http.post<WorkflowInstance>(`/api/tasks/${t.id}/complete`, {
      action: this.taskForm.action,
      comment: this.taskForm.comment,
      formData: {}
    }).subscribe({
      next: () => {
        this.showTaskModal.set(false);
        this.submitting.set(false);
        this.loadAll();
        if (this.showDetailModal()) this.refreshDetail();
      },
      error: (e) => {
        alert('Error: ' + (e?.error?.message || 'No se pudo completar la tarea.'));
        this.submitting.set(false);
      }
    });
  }

  cancelInstance(id: string) {
    if (!confirm('¿Cancelar esta instancia?')) return;
    this.http.post<WorkflowInstance>(`/api/instances/${id}/cancel`, { reason: 'Cancelado manualmente' }).subscribe({
      next: updated => {
        this.instances.update(list => list.map(i => i.id === updated.id ? updated : i));
        if (this.selectedInst()?.id === id) this.selectedInst.set(updated);
      }
    });
  }

  downloadAuditPdf(inst: WorkflowInstance) {
    const url = `/api/instances/${inst.id}/pdf`;
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const a = document.createElement('a');
        const objectUrl = URL.createObjectURL(blob);
        a.href = objectUrl;
        a.download = `audit-${inst.referenceNumber}.pdf`;
        a.click();
        URL.revokeObjectURL(objectUrl);
      },
      error: () => alert('Error al generar el reporte PDF')
    });
  }

  // ── Helpers ────────────────────────────────────────────────────
  currentNodeLabel(inst: WorkflowInstance): string {
    const wf = this.workflows().find(w => w.id === inst.workflowId);
    if (!wf || !inst.currentNodeId) return '—';
    return wf.nodes.find(n => n.id === inst.currentNodeId)?.label ?? '—';
  }

  statusClass(status: string): string {
    const m: Record<string, string> = {
      RUNNING:   'status-running',
      COMPLETED: 'status-completed',
      CANCELLED: 'status-cancelled',
      SUSPENDED: 'status-suspended',
      ERROR:     'status-error'
    };
    return m[status] ?? 'status-running';
  }

  taskStatusClass(status: string): string {
    const m: Record<string, string> = {
      NEW:        'task-new',
      IN_PROGRESS:'task-inprogress',
      COMPLETED:  'task-completed',
      REJECTED:   'task-rejected',
      CANCELLED:  'task-cancelled'
    };
    return m[status] ?? 'task-new';
  }

  priorityClass(p: string): string {
    const m: Record<string, string> = {
      LOW: 'pri-low', NORMAL: 'pri-normal', HIGH: 'pri-high', CRITICAL: 'pri-critical'
    };
    return m[p] ?? 'pri-normal';
  }

  isDecisionNode(inst: WorkflowInstance): boolean {
    const wf = this.workflows().find(w => w.id === inst.workflowId);
    if (!wf || !inst.currentNodeId) return false;
    const node = wf.nodes.find(n => n.id === inst.currentNodeId);
    return node?.type === 'DECISION';
  }

  pendingTasks(status: string = 'NEW'): Task[] {
    return this.tasks().filter(t => t.status === status || t.status === 'IN_PROGRESS');
  }

  formatDate(d?: string): string {
    if (!d) return '—';
    return new Date(d).toLocaleString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  trackById(_: number, item: { id: string }) { return item.id; }
}
