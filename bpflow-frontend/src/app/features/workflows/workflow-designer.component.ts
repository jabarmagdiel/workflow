import { Component, inject, signal, OnInit, OnDestroy, HostListener, NgZone } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/* ─── Interfaces ───────────────────────────────────────────── */
interface WorkflowEdge {
  id: string;
  sourceNodeId: string;
  targetNodeId: string;
  label?: string;
  condition?: string;
  type?: 'SEQUENCE' | 'CONDITIONAL' | 'DEFAULT';
}

interface WorkflowNode {
  id: string;
  label: string;
  type: 'START' | 'END' | 'TASK' | 'DECISION' | 'PARALLEL_GATEWAY' | 'JOIN_GATEWAY' | 'SUBPROCESS';
  assignedRole?: string;
  assignedUserId?: string;
  slaHours?: number;
  startNode: boolean;
  endNode: boolean;
  x: number;
  y: number;
}

interface Workflow {
  id: string;
  name: string;
  description: string;
  category?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED' | 'ARCHIVED';
  version: number;
  nodes: WorkflowNode[];
  edges: WorkflowEdge[]
  defaultSlaHours?: number;
}

/* ─── Palette element definition ───────────────────────────── */
export interface PaletteItem {
  type: WorkflowNode['type'];
  label: string;
  icon: string;
  role?: string;
}

/* ─── Canvas geometry constants ────────────────────────────── */
const NW = 140, NH = 60;
const DR = 46;
const CR = 26;

@Component({
  selector: 'app-workflow-designer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './workflow-designer.component.html',
  styleUrl: './workflow-designer.component.css'
})
export class WorkflowDesignerComponent implements OnInit, OnDestroy {
  private http   = inject(HttpClient);
  private zone   = inject(NgZone);

  /* ── Data signals ─────────────────────────────────── */
  workflows    = signal<Workflow[]>([]);
  selectedWf   = signal<Workflow | null>(null);
  selectedNode = signal<WorkflowNode | null>(null);
  loading      = signal(true);
  creating     = signal(false);
  addingNode   = signal(false);
  addingEdge   = signal(false);
  publishing   = signal(false);

  /* ── Canvas transform ─────────────────────────────── */
  tx    = signal(80);
  ty    = signal(80);
  scale = signal(0.85);
  isPanning = false;
  private panStart = { x: 0, y: 0, tx: 0, ty: 0 };

  /* ── Drag & Drop palette ──────────────────────────── */
  draggingPalette: PaletteItem | null = null;
  dropPreview: { x: number; y: number } | null = null;
  showDropLabel = false;

  /* ── Node drag on canvas ──────────────────────────── */
  draggingNode: WorkflowNode | null = null;
  private nodeDragStart = { mx: 0, my: 0, nx: 0, ny: 0 };

  /* ── Edge drawing mode ────────────────────────────── */
  edgeDrawing = signal(false);
  edgeSrcNode: WorkflowNode | null = null;
  edgePreviewEnd: { x: number; y: number } | null = null;

  /* ── Voice ────────────────────────────────────────── */
  voiceListening = signal(false);
  voiceText      = signal('');
  private recognition: any = null;

  /* ── Modal flags ──────────────────────────────────── */
  showCreateModal  = signal(false);
  showAddEdgeModal = signal(false);
  showNodeEdit     = signal(false);

  /* ── Form models ──────────────────────────────────── */
  readonly roles = ['ADMIN', 'MANAGER', 'OFFICER', 'ANALYST'];
  newWf   = { name: '', description: '', category: '', defaultSlaHours: 48 };
  newEdge = { sourceNodeId: '', targetNodeId: '', label: '', condition: '' };
  editingNode: Partial<WorkflowNode> = {};

  /* ── Palette items ────────────────────────────────── */
  palette: PaletteItem[] = [
    { type: 'START',            label: 'Inicio',       icon: 'start'    },
    { type: 'END',              label: 'Fin',          icon: 'end'      },
    { type: 'TASK',             label: 'Tarea',        icon: 'task'     },
    { type: 'DECISION',         label: 'Decisión',     icon: 'decision' },
    { type: 'PARALLEL_GATEWAY', label: 'Paralelo',     icon: 'parallel' },
    { type: 'JOIN_GATEWAY',     label: 'Unión',        icon: 'join'     },
    { type: 'SUBPROCESS',       label: 'Sub-Proceso',  icon: 'sub'      },
  ];

  ngOnInit() { this.loadWorkflows(); }
  ngOnDestroy() { this.stopVoice(); }

  /* ─── Data ──────────────────────────────────────────── */
  loadWorkflows() {
    this.loading.set(true);
    this.http.get<Workflow[]>('/api/workflows').subscribe({
      next:  d  => { this.workflows.set(d); this.loading.set(false); },
      error: () => this.loading.set(false)
    });
  }

  selectWorkflow(wf: Workflow) {
    this.selectedWf.set(wf);
    this.selectedNode.set(null);
    this.edgeDrawing.set(false);
    this.edgeSrcNode = null;
    setTimeout(() => this.fitView(), 0);
  }

  /* ─── SVG transform string ──────────────────────────── */
  get svgTransform(): string {
    return `translate(${this.tx()},${this.ty()}) scale(${this.scale()})`;
  }

  get gridX() { return this.tx() % 30; }
  get gridY() { return this.ty() % 30; }
  get zoomPct() { return (this.scale() * 100).toFixed(0); }

  /* ─── Pan ───────────────────────────────────────────── */
  onBgMousedown(e: MouseEvent) {
    if (e.button !== 0) return;
    if (this.draggingPalette) return;
    if (this.edgeDrawing()) return;
    if (this.draggingNode) return;
    this.isPanning = true;
    this.panStart  = { x: e.clientX, y: e.clientY, tx: this.tx(), ty: this.ty() };
    e.preventDefault();
  }

  onCanvasMousemove(e: MouseEvent) {
    // Palette drag preview
    if (this.draggingPalette) {
      const pos = this.svgPoint(e);
      this.dropPreview = pos;
      return;
    }
    // Node drag on canvas
    if (this.draggingNode) {
      const dx = (e.clientX - this.nodeDragStart.mx) / this.scale();
      const dy = (e.clientY - this.nodeDragStart.my) / this.scale();
      this.draggingNode.x = this.snap(this.nodeDragStart.nx + dx);
      this.draggingNode.y = this.snap(this.nodeDragStart.ny + dy);
      // Force change detection by updating the signal reference
      this.selectedWf.update(wf => wf ? { ...wf, nodes: [...wf.nodes] } : wf);
      return;
    }
    // Edge preview
    if (this.edgeDrawing() && this.edgeSrcNode) {
      this.edgePreviewEnd = this.svgPoint(e);
      return;
    }
    // Pan
    if (this.isPanning) {
      this.tx.set(this.panStart.tx + (e.clientX - this.panStart.x));
      this.ty.set(this.panStart.ty + (e.clientY - this.panStart.y));
    }
  }

  onCanvasMouseup(e: MouseEvent) {
    // Drop palette item
    if (this.draggingPalette && this.dropPreview) {
      const item = this.draggingPalette;
      this.dropNodeAt(item, this.dropPreview.x, this.dropPreview.y);
    }
    this.draggingPalette = null;
    this.dropPreview = null;

    // Finish node drag → persist position
    if (this.draggingNode) {
      this.persistNodePosition(this.draggingNode);
      this.draggingNode = null;
    }

    this.isPanning = false;
    this.edgePreviewEnd = null;
  }

  private snap(v: number, grid = 20) { return Math.round(v / grid) * grid; }

  /* Convert screen coords → canvas SVG coords */
  private svgPoint(e: MouseEvent) {
    const el = (e.currentTarget as HTMLElement).getBoundingClientRect();
    const mx = e.clientX - el.left;
    const my = e.clientY - el.top;
    return {
      x: this.snap((mx - this.tx()) / this.scale()),
      y: this.snap((my - this.ty()) / this.scale())
    };
  }

  /* ─── Palette drag start ────────────────────────────── */
  onPaletteDragStart(item: PaletteItem, e: MouseEvent) {
    e.preventDefault();
    this.draggingPalette = item;
    this.dropPreview = null;
    this.isPanning = false;
  }

  /* ─── Drop node ─────────────────────────────────────── */
  dropNodeAt(item: PaletteItem, x: number, y: number) {
    const wf = this.selectedWf();
    if (!wf || wf.status !== 'DRAFT') return;
    this.addingNode.set(true);
    const payload = {
      label:      item.label,
      type:       item.type,
      x, y,
      startNode:  item.type === 'START',
      endNode:    item.type === 'END',
      assignedRole: '',
      slaHours:   24,
    };
    this.http.post<Workflow>(`/api/workflows/${wf.id}/nodes`, payload).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
        this.addingNode.set(false);
        // Open quick-edit for the new node
        const newNode = u.nodes[u.nodes.length - 1];
        if (newNode) { this.openNodeEdit(newNode); }
      },
      error: () => this.addingNode.set(false)
    });
  }

  /* ─── Move node on canvas ───────────────────────────── */
  onNodeMousedown(node: WorkflowNode, e: MouseEvent) {
    if (this.edgeDrawing()) {
      this.handleEdgeClick(node, e);
      return;
    }
    e.stopPropagation();
    e.preventDefault();
    this.selectNode(node);
    this.draggingNode = node;
    this.nodeDragStart = { mx: e.clientX, my: e.clientY, nx: node.x, ny: node.y };
  }

  private persistNodePosition(node: WorkflowNode) {
    const wf = this.selectedWf();
    if (!wf) return;
    // PATCH position (fire-and-forget style, server will respond with full workflow)
    this.http.patch<Workflow>(`/api/workflows/${wf.id}/nodes/${node.id}`,
      { x: node.x, y: node.y }).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
      }
    });
  }

  /* ─── Node edit (quick panel) ───────────────────────── */
  openNodeEdit(node: WorkflowNode) {
    this.editingNode = { ...node };
    this.showNodeEdit.set(true);
    this.selectedNode.set(node);
  }

  saveNodeEdit() {
    const wf = this.selectedWf();
    if (!wf || !this.editingNode.id) return;
    this.http.patch<Workflow>(`/api/workflows/${wf.id}/nodes/${this.editingNode.id}`,
      this.editingNode).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
        this.showNodeEdit.set(false);
        // Sync selected node reference
        const updated = u.nodes.find(n => n.id === this.editingNode.id);
        if (updated) this.selectedNode.set(updated);
      }
    });
  }

  /* ─── Edge drawing mode ─────────────────────────────── */
  toggleEdgeDrawing() {
    const next = !this.edgeDrawing();
    this.edgeDrawing.set(next);
    this.edgeSrcNode = null;
    this.edgePreviewEnd = null;
    if (!next) this.clearSelection();
  }

  handleEdgeClick(node: WorkflowNode, e: Event) {
    e.stopPropagation();
    if (!this.edgeSrcNode) {
      this.edgeSrcNode = node;
    } else if (this.edgeSrcNode.id !== node.id) {
      this.connectNodes(this.edgeSrcNode, node);
      this.edgeSrcNode = null;
      this.edgePreviewEnd = null;
      this.edgeDrawing.set(false);
    }
  }

  connectNodes(src: WorkflowNode, tgt: WorkflowNode) {
    const wf = this.selectedWf();
    if (!wf) return;
    this.http.post<Workflow>(`/api/workflows/${wf.id}/edges`, {
      sourceNodeId: src.id,
      targetNodeId: tgt.id,
      type: 'SEQUENCE'
    }).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
      }
    });
  }

  /* ─── Zoom ──────────────────────────────────────────── */
  onWheel(e: WheelEvent) {
    e.preventDefault();
    this.scale.set(Math.max(0.15, Math.min(3, this.scale() * (e.deltaY < 0 ? 1.1 : 0.9))));
  }
  zoomIn()    { this.scale.set(Math.min(3,    this.scale() * 1.2)); }
  zoomOut()   { this.scale.set(Math.max(0.15, this.scale() / 1.2)); }
  zoomReset() { this.tx.set(80); this.ty.set(80); this.scale.set(0.85); }

  fitView() {
    const wf = this.selectedWf();
    if (!wf || !wf.nodes.length) return;
    const xs = wf.nodes.map(n => n.x ?? 0), ys = wf.nodes.map(n => n.y ?? 0);
    const pad = 120;
    const minX = Math.min(...xs) - pad, maxX = Math.max(...xs) + pad;
    const minY = Math.min(...ys) - pad, maxY = Math.max(...ys) + pad;
    const cw = maxX - minX, ch = maxY - minY;
    const vw = window.innerWidth - 320 - 72, vh = window.innerHeight - 120; // account for palette
    const s  = Math.min(vw / cw, vh / ch, 1.15);
    this.scale.set(+s.toFixed(3));
    this.tx.set(Math.round((vw - cw * s) / 2 - minX * s));
    this.ty.set(Math.round((vh - ch * s) / 2 - minY * s + 40));
  }

  /* ─── Geometry helpers ──────────────────────────────── */
  private getNode(id: string): WorkflowNode | undefined {
    return this.selectedWf()?.nodes.find(n => n.id === id);
  }

  private borderPt(n: WorkflowNode, angle: number) {
    const cx = n.x ?? 0, cy = n.y ?? 0;
    const cos = Math.cos(angle), sin = Math.sin(angle);
    switch (n.type) {
      case 'START': case 'END':
        return { x: cx + CR * cos, y: cy + CR * sin };
      case 'TASK': case 'SUBPROCESS': {
        const t = Math.min((NW / 2 + 2) / (Math.abs(cos) || 1e-9),
                           (NH / 2 + 2) / (Math.abs(sin) || 1e-9));
        return { x: cx + t * cos, y: cy + t * sin };
      }
      default: {
        const t = DR / ((Math.abs(cos) || 1e-9) + (Math.abs(sin) || 1e-9));
        return { x: cx + t * cos, y: cy + t * sin };
      }
    }
  }

  private exitPt(src: WorkflowNode, tx: number, ty: number) {
    return this.borderPt(src, Math.atan2(ty - (src.y ?? 0), tx - (src.x ?? 0)));
  }

  private entryPt(tgt: WorkflowNode, sx: number, sy: number) {
    return this.borderPt(tgt, Math.atan2(sy - (tgt.y ?? 0), sx - (tgt.x ?? 0)));
  }

  private ctrlPts(sp: {x:number;y:number}, ep: {x:number;y:number},
                  srcY: number, tgtY: number) {
    const dx = ep.x - sp.x, dy = ep.y - sp.y;
    const adx = Math.abs(dx), ady = Math.abs(dy);
    if (ep.x < sp.x - 60 && ady < adx) {
      const bot = Math.max(srcY, tgtY) + 140;
      return { c1x: sp.x, c1y: bot, c2x: ep.x, c2y: bot };
    }
    if (ady > adx) {
      const v = ady * 0.38;
      return { c1x: sp.x, c1y: sp.y + Math.sign(dy) * v,
               c2x: ep.x, c2y: ep.y - Math.sign(dy) * v };
    }
    const h = Math.max(50, adx * 0.42);
    return { c1x: sp.x + Math.sign(dx || 1) * h, c1y: sp.y,
             c2x: ep.x - Math.sign(dx || 1) * h, c2y: ep.y };
  }

  edgePath(e: WorkflowEdge): string {
    const src = this.getNode(e.sourceNodeId), tgt = this.getNode(e.targetNodeId);
    if (!src || !tgt) return '';
    const sp = this.exitPt(src, tgt.x ?? 0, tgt.y ?? 0);
    const ep = this.entryPt(tgt, src.x ?? 0, src.y ?? 0);
    const { c1x, c1y, c2x, c2y } = this.ctrlPts(sp, ep, src.y ?? 0, tgt.y ?? 0);
    return `M${sp.x},${sp.y} C${c1x},${c1y} ${c2x},${c2y} ${ep.x},${ep.y}`;
  }

  edgeMid(e: WorkflowEdge): { x: number; y: number } | null {
    const src = this.getNode(e.sourceNodeId), tgt = this.getNode(e.targetNodeId);
    if (!src || !tgt || !e.label) return null;
    const sp = this.exitPt(src, tgt.x ?? 0, tgt.y ?? 0);
    const ep = this.entryPt(tgt, src.x ?? 0, src.y ?? 0);
    const { c1x, c1y, c2x, c2y } = this.ctrlPts(sp, ep, src.y ?? 0, tgt.y ?? 0);
    const t = 0.5, m = 0.5;
    return {
      x: m**3*sp.x + 3*m**2*t*c1x + 3*m*t**2*c2x + t**3*ep.x,
      y: m**3*sp.y + 3*m**2*t*c1y + 3*m*t**2*c2y + t**3*ep.y
    };
  }

  /* Edge preview line (while drawing) */
  get edgePreviewPath(): string {
    if (!this.edgeSrcNode || !this.edgePreviewEnd) return '';
    const src = this.edgeSrcNode;
    const sp = this.exitPt(src, this.edgePreviewEnd.x, this.edgePreviewEnd.y);
    return `M${sp.x},${sp.y} L${this.edgePreviewEnd.x},${this.edgePreviewEnd.y}`;
  }

  /* ─── Node visual helpers ───────────────────────────── */
  splitLabel(label: string, max = 14): string[] {
    const words = label.split(' ');
    const lines: string[] = [];
    let cur = '';
    for (const w of words) {
      if (!cur) { cur = w; }
      else if ((cur + ' ' + w).length <= max) cur += ' ' + w;
      else { lines.push(cur); cur = w; }
    }
    if (cur) lines.push(cur);
    return lines.slice(0, 3);
  }

  labelY(i: number, total: number) { return i * 14 - (total - 1) * 7; }

  nodeColor(n: WorkflowNode): string {
    if (n.type === 'START') return '#10b981';
    if (n.type === 'END') {
      const l = n.label.toLowerCase();
      if (l.includes('aprobado'))  return '#16a34a';
      if (l.includes('rechazado')) return '#dc2626';
      if (l.includes('cancelado')) return '#d97706';
      return '#64748b';
    }
    if (['DECISION','PARALLEL_GATEWAY','JOIN_GATEWAY'].includes(n.type)) return '#d97706';
    return '#0891b2';
  }

  nodeStroke(n: WorkflowNode): string {
    if (this.edgeDrawing() && this.edgeSrcNode?.id === n.id) return '#22d3ee';
    return this.selectedNode()?.id === n.id ? '#ffffff' : this.nodeColor(n);
  }

  selectNode(node: WorkflowNode, e?: Event) {
    e?.stopPropagation();
    if (this.edgeDrawing()) { this.handleEdgeClick(node, e || new Event('')); return; }
    this.selectedNode.set(this.selectedNode()?.id === node.id ? null : node);
    this.showNodeEdit.set(false);
  }

  clearSelection() {
    this.selectedNode.set(null);
    this.showNodeEdit.set(false);
    if (!this.edgeDrawing()) {
      this.edgeSrcNode = null;
      this.edgePreviewEnd = null;
    }
  }

  /* ─── CRUD ──────────────────────────────────────────── */
  openCreateModal() {
    this.newWf = { name: '', description: '', category: '', defaultSlaHours: 48 };
    this.showCreateModal.set(true);
  }

  createWorkflow() {
    if (!this.newWf.name) return;
    this.creating.set(true);
    this.http.post<Workflow>('/api/workflows', this.newWf).subscribe({
      next: wf => {
        this.workflows.update(l => [wf, ...l]);
        this.selectWorkflow(wf);
        this.showCreateModal.set(false); this.creating.set(false);
      },
      error: () => this.creating.set(false)
    });
  }

  deleteNode(wfId: string, nodeId: string) {
    if (!confirm('¿Eliminar este nodo y sus conexiones?')) return;
    this.http.delete<Workflow>(`/api/workflows/${wfId}/nodes/${nodeId}`).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
        this.selectedNode.set(null);
        this.showNodeEdit.set(false);
      }
    });
  }

  deleteEdge(edgeId: string) {
    const wf = this.selectedWf();
    if (!wf) return;
    this.http.delete<Workflow>(`/api/workflows/${wf.id}/edges/${edgeId}`).subscribe({
      next: u => {
        this.selectedWf.set(u);
        this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
      }
    });
  }

  publishWorkflow(id: string) {
    this.publishing.set(true);
    this.http.post<Workflow>(`/api/workflows/${id}/publish`, {}).subscribe({
      next: u => {
        this.selectedWf.set(u); this.workflows.update(l => l.map(w => w.id === u.id ? u : w));
        this.publishing.set(false);
      },
      error: err => {
        alert('Error: ' + (err?.error?.message || 'El workflow debe tener exactamente 1 nodo de inicio y al menos 1 nodo final.'));
        this.publishing.set(false);
      }
    });
  }

  /* ─── Voice commands (designer only) ───────────────── */
  toggleVoice() {
    this.voiceListening() ? this.stopVoice() : this.startVoice();
  }

  startVoice() {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) { alert('Tu navegador no soporta reconocimiento de voz.'); return; }
    this.recognition = new SpeechRecognition();
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
    const wf = this.selectedWf();
    if (!wf || wf.status !== 'DRAFT') return;

    // "agregar tarea [nombre]"
    const taskMatch = text.match(/(?:agregar|añadir|crear)\s+(?:tarea|task)\s+(.+)/);
    if (taskMatch) {
      const label = this.capitalize(taskMatch[1]);
      const xs = wf.nodes.map(n => n.x);
      const x = xs.length ? Math.max(...xs) + 220 : 300;
      const y = wf.nodes[Math.floor(wf.nodes.length / 2)]?.y ?? 300;
      this.dropNodeAt({ type: 'TASK', label, icon: 'task' }, x, y);
      return;
    }
    // "agregar inicio"
    if (/(?:agregar|añadir)\s+(?:nodo\s+)?inicio/.test(text)) {
      this.dropNodeAt({ type: 'START', label: 'Inicio', icon: 'start' }, 100, 300);
      return;
    }
    // "agregar fin"
    if (/(?:agregar|añadir)\s+(?:nodo\s+)?fin/.test(text)) {
      const xs = wf.nodes.map(n => n.x);
      this.dropNodeAt({ type: 'END', label: 'Fin', icon: 'end' }, xs.length ? Math.max(...xs) + 220 : 600, 300);
      return;
    }
    // "agregar decisión"
    if (/(?:agregar|añadir)\s+(?:gateway\s+)?decisi[oó]n/.test(text)) {
      const xs = wf.nodes.map(n => n.x);
      this.dropNodeAt({ type: 'DECISION', label: 'Decisión', icon: 'decision' }, xs.length ? Math.max(...xs) + 220 : 400, 300);
      return;
    }
    // "publicar"
    if (/publicar/.test(text)) { this.publishWorkflow(wf.id); return; }
    // "ajustar vista"
    if (/(?:ajustar|centrar)\s+vista/.test(text)) { this.fitView(); return; }
    // "acercar"
    if (/acercar/.test(text)) { this.zoomIn(); return; }
    // "alejar"
    if (/alejar/.test(text)) { this.zoomOut(); return; }
  }

  private capitalize(s: string) { return s.charAt(0).toUpperCase() + s.slice(1); }

  /* ─── Misc helpers ──────────────────────────────────── */
  statusClass(s: string): string {
    const m: Record<string, string> = {
      DRAFT: 'bg-amber-950 text-amber-400',
      PUBLISHED: 'bg-emerald-950 text-emerald-400',
      DEPRECATED: 'bg-slate-800 text-slate-500',
      ARCHIVED: 'bg-slate-700 text-slate-500'
    };
    return m[s] ?? m['DRAFT'];
  }

  trackById(_: number, item: { id: string }) { return item.id; }
}
