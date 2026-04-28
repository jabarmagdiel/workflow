import { Component, inject, signal, OnInit, OnDestroy, NgZone, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NotificationService } from '../../core/services/notification.service';
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
  private http = inject(HttpClient);
  private zone = inject(NgZone);
  private notifService = inject(NotificationService);

  tasks        = signal<Task[]>([]);
  activeFilter = signal<string>('NEW');
  selectedTask = signal<Task | null>(null);
  processing   = signal(false);
  decisionComment = '';
  private eventSubscription?: Subscription;

  /* ── Voice ─────────────────────────────────────────── */
  voiceListening = signal(false);
  voiceText      = signal('');
  private recognition: any = null;

  tabs = [
    { label: 'Nuevas',     key: 'NEW'        },
    { label: 'En Curso',   key: 'IN_PROGRESS'},
    { label: 'Completadas',key: 'COMPLETED'  },
    { label: 'Rechazadas', key: 'REJECTED'   }
  ];

  filteredTasks = computed(() => this.tasks().filter((t: Task) => t.status === this.activeFilter()));

  tasksByStatus(status: string): Task[] {
    return this.tasks().filter((t: Task) => t.status === status);
  }

  ngOnInit() { 
    this.loadTasks(); 
    
    // Auto refresh on events
    this.eventSubscription = this.notifService.events$.subscribe(event => {
      if (['TASK_COMPLETED', 'TASK_REASSIGNED', 'INSTANCE_STARTED'].includes(event.event)) {
         this.loadTasks();
      }
    });
  }

  ngOnDestroy() { 
    this.stopVoice(); 
    this.eventSubscription?.unsubscribe();
  }

  loadTasks() {
    this.http.get<Task[]>('/api/tasks/my').subscribe({
      next:  (data: Task[]) => this.tasks.set(data),
      error: ()             => this.tasks.set([])
    });
  }

  selectTask(task: Task) {
    this.selectedTask.set(task);
    this.decisionComment = task.comment || '';
    if (task.status === 'NEW') {
      this.http.post<Task>(`/api/tasks/${task.id}/start`, {}).subscribe({
        next: (updated: Task) => {
          this.tasks.update((list: Task[]) => list.map((t: Task) => t.id === updated.id ? updated : t));
          this.selectedTask.set(updated);
        }
      });
    }
  }

  decide(action: string) {
    const task = this.selectedTask();
    if (!task) return;
    this.processing.set(true);
    this.http.post<object>(`/api/tasks/${task.id}/complete`, {
      action,
      comment: this.decisionComment,
      formData: {}
    }).subscribe({
      next:  () => {
        this.processing.set(false);
        this.loadTasks();
        this.selectedTask.set(null);
        this.activeFilter.set(action === 'APPROVE' ? 'COMPLETED' : 'REJECTED');
      },
      error: () => this.processing.set(false)
    });
  }

  translateStatus(s: string): string {
    const map: Record<string, string> = {
      NEW: 'Nueva',
      IN_PROGRESS: 'En Curso',
      COMPLETED: 'Completada',
      REJECTED: 'Rechazada',
      CANCELLED: 'Cancelada',
      DELEGATED: 'Delegada'
    };
    return map[s] || s;
  }

  translatePriority(p: string): string {
    const map: Record<string, string> = {
      LOW: 'Baja',
      NORMAL: 'Normal',
      HIGH: 'Alta',
      CRITICAL: 'Crítica'
    };
    return map[p] || p;
  }

  priorityClass(p: string): string {
    const map: Record<string, string> = {
      CRITICAL: 'bg-red-950 text-red-400 border border-red-900',
      HIGH:     'bg-orange-950 text-orange-400 border border-orange-900',
      NORMAL:   'bg-blue-950 text-blue-400 border border-blue-900',
      LOW:      'bg-slate-800 text-slate-400'
    };
    return map[p] ?? map['LOW'];
  }

  /* ── Voice commands ──────────────────────────────────── */
  toggleVoice() {
    this.voiceListening() ? this.stopVoice() : this.startVoice();
  }

  startVoice() {
    const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SR) { alert('Tu navegador no soporta reconocimiento de voz.'); return; }
    this.recognition = new SR();
    this.recognition.lang = 'es-ES';
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.onresult = (ev: any) => {
      const transcript = Array.from(ev.results as any[])
        .map((r: any) => r[0].transcript).join('');
      this.zone.run(() => {
        this.voiceText.set(transcript);
        if (ev.results[ev.results.length - 1].isFinal) {
          this.processVoiceCommand(transcript.toLowerCase().trim());
          this.voiceText.set('');
        }
      });
    };
    this.recognition.onerror = () => this.zone.run(() => this.stopVoice());
    this.recognition.onend   = () => this.zone.run(() => this.voiceListening.set(false));
    this.recognition.start();
    this.voiceListening.set(true);
  }

  stopVoice() {
    this.recognition?.stop();
    this.recognition = null;
    this.voiceListening.set(false);
    this.voiceText.set('');
  }

  private processVoiceCommand(text: string) {
    // "aprobar" → decide APPROVE
    if (/aprobar/.test(text) && this.selectedTask()) {
      this.decide('APPROVE');
      return;
    }
    // "rechazar" → decide REJECT
    if (/rechazar/.test(text) && this.selectedTask()) {
      this.decide('REJECT');
      return;
    }
    // "seleccionar tarea [n]" → select nth task
    const selMatch = text.match(/(?:seleccionar|abrir)\s+tarea\s+(?:número\s+)?(\d+)/);
    if (selMatch) {
      const idx = parseInt(selMatch[1], 10) - 1;
      const t = this.filteredTasks()[idx];
      if (t) this.selectTask(t);
      return;
    }
    // "mostrar nuevas | completadas | rechazadas | en curso"
    if (/nuevas?/.test(text))      { this.activeFilter.set('NEW');         return; }
    if (/en\s+curso/.test(text))   { this.activeFilter.set('IN_PROGRESS'); return; }
    if (/completadas?/.test(text)) { this.activeFilter.set('COMPLETED');   return; }
    if (/rechazadas?/.test(text))  { this.activeFilter.set('REJECTED');    return; }
    // "recargar" → reload
    if (/recargar/.test(text)) { this.loadTasks(); return; }
  }
}
