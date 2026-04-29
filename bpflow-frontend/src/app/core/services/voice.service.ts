import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';

export interface VoiceCommand {
  intent: string;
  entities: any;
  rawText: string;
}

export interface VoiceStatus {
  listening: boolean;
  transcript: string;
  lastCommand: string;
  error: string | null;
}

@Injectable({ providedIn: 'root' })
export class VoiceService {
  private http = inject(HttpClient);

  isListening    = signal<boolean>(false);
  lastTranscript = signal<string>('');
  lastCommand    = signal<string>('');
  isSupported    = signal<boolean>(false);
  voiceError     = signal<string | null>(null);

  private commandBus = new Subject<VoiceCommand>();
  public commandBus$ = this.commandBus.asObservable();

  private recognition: any = null;
  private restartTimeout: any = null;

  constructor() {
    try {
      const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (SR) {
        this.recognition = new SR();
        this.recognition.lang = 'es-ES';
        this.recognition.continuous = true;
        this.recognition.interimResults = true;
        this.recognition.maxAlternatives = 3;

        this.recognition.onresult = (event: any) => {
          let interim = '', final = '';
          for (let i = event.resultIndex; i < event.results.length; ++i) {
            const t = event.results[i][0].transcript;
            if (event.results[i].isFinal) final += t;
            else interim += t;
          }
          this.lastTranscript.set((final || interim).trim());
          if (final.trim()) {
            this.processFull(final.toLowerCase().trim());
            setTimeout(() => this.lastTranscript.set(''), 1800);
          }
        };

        this.recognition.onend = () => {
          if (this.isListening()) {
            // Auto-restart for continuous use
            this.restartTimeout = setTimeout(() => {
              try { this.recognition.start(); } catch {}
            }, 300);
          }
        };

        this.recognition.onerror = (e: any) => {
          if (e.error === 'not-allowed') {
            this.voiceError.set('Permiso de micrófono denegado.');
            this.isListening.set(false);
          }
        };

        this.isSupported.set(true);
      }
    } catch (e) {
      console.warn('Speech Recognition not available');
    }
  }

  startListening() {
    if (!this.recognition) { alert('Tu navegador no soporta voz. Usa Chrome o Edge.'); return; }
    this.voiceError.set(null);
    this.isListening.set(true);
    try { this.recognition.start(); } catch {}
  }

  stopListening() {
    clearTimeout(this.restartTimeout);
    this.isListening.set(false);
    this.lastTranscript.set('');
    try { this.recognition?.stop(); } catch {}
  }

  toggleListening() {
    this.isListening() ? this.stopListening() : this.startListening();
  }

  // ──────────────────────────────────────────────────────────
  // LOCAL NLP — sin dependencia de AI externa
  // ──────────────────────────────────────────────────────────
  private processFull(text: string) {
    const cmd = this.parseLocal(text);
    if (cmd) {
      this.lastCommand.set(text);
      this.commandBus.next(cmd);
      return;
    }
    // Fallback: try AI if available
    this.http.post<any>('/ai/voice/parse-command', { text }).subscribe({
      next: (res) => {
        if (res?.intent && res.intent !== 'UNKNOWN') {
          this.lastCommand.set(text);
          this.commandBus.next({ intent: res.intent, entities: res.entities, rawText: text });
        }
      },
      error: () => {}
    });
  }

  private parseLocal(t: string): VoiceCommand | null {
    // ── TASK COMMANDS ──────────────────────────────────────
    if (/aprobar|aprueba|aprobo|apruebo/.test(t))
      return { intent: 'APPROVE', entities: {}, rawText: t };

    if (/rechazar|rechaza|rechazo/.test(t))
      return { intent: 'REJECT', entities: {}, rawText: t };

    if (/recargar|actualiz|refrescar/.test(t))
      return { intent: 'RELOAD', entities: {}, rawText: t };

    if (/mostrar nuevas?|ver nuevas?|tareas nuevas?/.test(t))
      return { intent: 'FILTER_TASKS', entities: { filter: 'NEW' }, rawText: t };
    if (/en curso|progreso|activas?/.test(t))
      return { intent: 'FILTER_TASKS', entities: { filter: 'IN_PROGRESS' }, rawText: t };
    if (/completadas?|terminadas?|finalizadas?/.test(t))
      return { intent: 'FILTER_TASKS', entities: { filter: 'COMPLETED' }, rawText: t };
    if (/rechazadas?/.test(t))
      return { intent: 'FILTER_TASKS', entities: { filter: 'REJECTED' }, rawText: t };

    // seleccionar tarea 1, abrir tarea número 2 …
    const selM = t.match(/(?:seleccionar|abrir|abrir la|ver)\s+tarea\s+(?:número\s+)?(\w+)/);
    if (selM) {
      const num = this.wordToNum(selM[1]);
      return { intent: 'SELECT_TASK', entities: { index: num - 1 }, rawText: t };
    }

    // comentar / agregar comentario "texto"
    const comM = t.match(/(?:comentar|agregar\s+comentario|escribir?)[\s:]+(.+)/);
    if (comM)
      return { intent: 'SET_COMMENT', entities: { text: comM[1].trim() }, rawText: t };

    // ── WORKFLOW / DESIGNER COMMANDS ───────────────────────
    // "agregar tarea Revisión", "crear nodo Aprobación"
    const createM = t.match(/(?:agregar|crear|añadir|nuevo)\s+(?:tarea|nodo|paso|decisión|inicio|fin|sub[\s-]?proceso|paralelo|unión)\s*(.*)$/);
    if (createM) {
      const typeM = t.match(/(?:agregar|crear|añadir|nuevo)\s+(tarea|nodo|paso|decisión|inicio|fin|sub[\s-]?proceso|paralelo|unión)/);
      const ntype = this.labelToNodeType(typeM ? typeM[1] : 'tarea');
      const name = createM[1].trim() || this.nodeTypeLabel(ntype);
      return { intent: 'CREATE_NODE', entities: { node_type: ntype, node_name: name }, rawText: t };
    }

    if (/publicar|publish/.test(t))
      return { intent: 'PUBLISH', entities: {}, rawText: t };

    if (/acercar|zoom\s*in|m[aá]s\s*grande/.test(t))
      return { intent: 'ZOOM_IN', entities: {}, rawText: t };

    if (/alejar|zoom\s*out|m[aá]s\s*peque/.test(t))
      return { intent: 'ZOOM_OUT', entities: {}, rawText: t };

    if (/ajustar|encuadrar|fit|centrar|ver\s*todo/.test(t))
      return { intent: 'FIT_VIEW', entities: {}, rawText: t };

    if (/eliminar|borrar|delete/.test(t))
      return { intent: 'DELETE_ELEMENT', entities: {}, rawText: t };

    if (/conectar/.test(t))
      return { intent: 'CONNECT_NODES', entities: {}, rawText: t };

    if (/nuevo proceso|crear proceso|nuevo workflow/.test(t))
      return { intent: 'NEW_WORKFLOW', entities: {}, rawText: t };

    // ── NAVIGATION ────────────────────────────────────────
    if (/ir\s+a\s+tareas|abrir\s+tareas|mis\s+tareas/.test(t))
      return { intent: 'NAV_TASKS', entities: {}, rawText: t };
    if (/ir\s+a\s+dise[ñn]|abrir\s+dise[ñn]|workflow/.test(t))
      return { intent: 'NAV_DESIGNER', entities: {}, rawText: t };

    return null;
  }

  private wordToNum(w: string): number {
    const map: Record<string, number> = {
      uno:1, una:1, primero:1, primera:1, '1':1,
      dos:2, segunda:2, segundo:2, '2':2,
      tres:3, tercero:3, tercera:3, '3':3,
      cuatro:4, '4':4, cinco:5, '5':5,
      seis:6, '6':6, siete:7, '7':7,
      ocho:8, '8':8, nueve:9, '9':9, diez:10, '10':10
    };
    return map[w] ?? (parseInt(w, 10) || 1);
  }

  private labelToNodeType(label: string): string {
    if (/inicio/.test(label))    return 'START';
    if (/fin/.test(label))       return 'END';
    if (/decisi/.test(label))    return 'DECISION';
    if (/paralelo/.test(label))  return 'PARALLEL_GATEWAY';
    if (/uni.n/.test(label))     return 'JOIN_GATEWAY';
    if (/sub/.test(label))       return 'SUBPROCESS';
    return 'TASK';
  }

  private nodeTypeLabel(type: string): string {
    const m: Record<string, string> = {
      START:'Inicio', END:'Fin', DECISION:'Decisión',
      PARALLEL_GATEWAY:'Paralelo', JOIN_GATEWAY:'Unión', SUBPROCESS:'Sub-Proceso', TASK:'Tarea'
    };
    return m[type] ?? 'Tarea';
  }
}
