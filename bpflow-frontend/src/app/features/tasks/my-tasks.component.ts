import { Component, inject, signal, OnInit, OnDestroy, NgZone, computed, ElementRef, ViewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../core/services/notification.service';
import { VoiceService } from '../../core/services/voice.service';
import { Subscription } from 'rxjs';

interface Attachment {
  fileName: string;
  fileType: string;
  fileUrl: string;
  fileSize: number;
  uploadedAt: string;
  uploadedBy: string;
}

interface Task {
  id: string;
  instanceId: string;
  workflowId: string;
  workflowName: string;
  nodeId: string;
  nodeName: string;
  title: string;
  description: string;
  assignedTo: string;
  assignedRole: string;
  status: 'NEW' | 'IN_PROGRESS' | 'COMPLETED' | 'REJECTED' | 'CANCELLED' | 'DELEGATED';
  priority: 'LOW' | 'NORMAL' | 'HIGH' | 'CRITICAL';
  dueAt: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
  comment?: string;
  overdue: boolean;
  slaBreached: boolean;
  attachments?: Attachment[];
}

@Component({
  selector: 'app-my-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './my-tasks.component.html',
  styleUrl: './my-tasks.component.css'
})
export class MyTasksComponent implements OnInit, OnDestroy {
  private http         = inject(HttpClient);
  private zone         = inject(NgZone);
  private notifService = inject(NotificationService);
  public  voiceService = inject(VoiceService);

  @ViewChild('commentBox') commentBox?: ElementRef<HTMLTextAreaElement>;

  tasks          = signal<Task[]>([]);
  activeFilter   = signal<string>('NEW');
  selectedTask   = signal<Task | null>(null);
  processing     = signal(false);
  decisionComment = '';
  voiceFeedback  = signal<string | null>(null);

  private subs: Subscription[] = [];

  // ── Shortcuts
  readonly COMMANDS = [
    { keys: ['Alt+V'], label: 'Activar/Desactivar voz' },
    { keys: ['"Aprobar"'], label: 'Aprobar tarea seleccionada' },
    { keys: ['"Rechazar"'], label: 'Rechazar tarea seleccionada' },
    { keys: ['"Tarea 1"'], label: 'Seleccionar tarea N' },
    { keys: ['"Mostrar nuevas"'], label: 'Filtrar nuevas' },
    { keys: ['"En curso"'], label: 'Filtrar en curso' },
    { keys: ['"Completadas"'], label: 'Filtrar completadas' },
    { keys: ['"Recargar"'], label: 'Actualizar bandeja' },
    { keys: ['"Comentar ..."'], label: 'Agregar comentario' },
  ];

  tabs = [
    { label: 'Nuevas',      key: 'NEW',         icon: '🔴' },
    { label: 'En Curso',    key: 'IN_PROGRESS',  icon: '🟡' },
    { label: 'Completadas', key: 'COMPLETED',    icon: '🟢' },
    { label: 'Rechazadas',  key: 'REJECTED',     icon: '⚫' },
  ];

  filteredTasks = computed(() => this.tasks().filter(t => t.status === this.activeFilter()));
  tasksByStatus = (s: string) => this.tasks().filter(t => t.status === s);

  ngOnInit() {
    this.loadTasks();
    this.subs.push(
      this.notifService.events$.subscribe(ev => {
        if (['TASK_COMPLETED','TASK_REASSIGNED','INSTANCE_STARTED'].includes(ev.event)) this.loadTasks();
      }),
      this.voiceService.commandBus$.subscribe(cmd => this.zone.run(() => this.handleCommand(cmd)))
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }

  loadTasks() {
    this.http.get<Task[]>('/api/tasks/my').subscribe({
      next:  d => this.tasks.set(d),
      error: () => this.tasks.set([])
    });
  }

  selectTask(task: Task) {
    this.selectedTask.set(task);
    this.decisionComment = task.comment || '';
    if (task.status === 'NEW') {
      this.http.post<Task>(`/api/tasks/${task.id}/start`, {}).subscribe({
        next: u => {
          this.tasks.update(l => l.map(t => t.id === u.id ? u : t));
          this.selectedTask.set(u);
        }
      });
    }
  }

  decide(action: string) {
    const task = this.selectedTask();
    if (!task) return;
    this.processing.set(true);
    this.http.post<object>(`/api/tasks/${task.id}/complete`, {
      action, comment: this.decisionComment, formData: {}
    }).subscribe({
      next: () => {
        this.processing.set(false);
        this.loadTasks();
        this.selectedTask.set(null);
        this.activeFilter.set(action === 'APPROVE' ? 'COMPLETED' : 'REJECTED');
      },
      error: () => this.processing.set(false)
    });
  }

  onFileSelected(event: any) {
    const file: File = event.target.files[0];
    const task = this.selectedTask();
    if (!file || !task) return;
    this.processing.set(true);
    const fd = new FormData();
    fd.append('file', file);
    this.http.post<Task>(`/api/tasks/${task.id}/attachments`, fd).subscribe({
      next:  u  => { this.processing.set(false); this.selectedTask.set(u); this.tasks.update(l => l.map(t => t.id === u.id ? u : t)); },
      error: () => this.processing.set(false)
    });
  }

  // ── Voice ────────────────────────────────────────────────
  private handleCommand(cmd: any) {
    switch (cmd.intent) {
      case 'APPROVE':
        if (this.selectedTask()) { this.showFeedback('✅ Aprobando tarea...'); this.decide('APPROVE'); }
        else this.showFeedback('⚠️ Primero selecciona una tarea');
        break;
      case 'REJECT':
        if (this.selectedTask()) { this.showFeedback('❌ Rechazando tarea...'); this.decide('REJECT'); }
        else this.showFeedback('⚠️ Primero selecciona una tarea');
        break;
      case 'RELOAD':
        this.showFeedback('🔄 Actualizando bandeja...');
        this.loadTasks();
        break;
      case 'FILTER_TASKS':
        this.activeFilter.set(cmd.entities.filter);
        const label = this.tabs.find(t => t.key === cmd.entities.filter)?.label ?? cmd.entities.filter;
        this.showFeedback(`🔍 Mostrando: ${label}`);
        break;
      case 'SELECT_TASK': {
        const idx = cmd.entities.index ?? 0;
        const t = this.filteredTasks()[idx];
        if (t) { this.selectTask(t); this.showFeedback(`📋 Tarea "${t.title}" abierta`); }
        else this.showFeedback(`⚠️ No hay tarea #${idx + 1} en esta vista`);
        break;
      }
      case 'SET_COMMENT':
        this.decisionComment = cmd.entities.text;
        this.showFeedback(`💬 Comentario: "${cmd.entities.text}"`);
        setTimeout(() => this.commentBox?.nativeElement?.focus(), 100);
        break;
    }
  }

  private showFeedback(msg: string) {
    this.voiceFeedback.set(msg);
    setTimeout(() => this.voiceFeedback.set(null), 3000);
  }

  // ── Helpers ──────────────────────────────────────────────
  translateStatus(s: string) {
    return ({ NEW:'Nueva', IN_PROGRESS:'En Curso', COMPLETED:'Completada', REJECTED:'Rechazada', CANCELLED:'Cancelada', DELEGATED:'Delegada' } as any)[s] ?? s;
  }
  translatePriority(p: string) {
    return ({ LOW:'Baja', NORMAL:'Normal', HIGH:'Alta', CRITICAL:'Crítica' } as any)[p] ?? p;
  }
  priorityClass(p: string) {
    return ({ CRITICAL:'priority-critical', HIGH:'priority-high', NORMAL:'priority-normal', LOW:'priority-low' } as any)[p] ?? 'priority-low';
  }
  priorityIcon(p: string) {
    return ({ CRITICAL:'🔴', HIGH:'🟠', NORMAL:'🔵', LOW:'⚪' } as any)[p] ?? '⚪';
  }
}
