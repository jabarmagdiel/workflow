import { Injectable, signal, inject } from '@angular/core';
import { AuthService } from './auth.service';
import { Subject } from 'rxjs';

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface SystemEvent {
  event: string;
  data: any;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private auth = inject(AuthService);
  private ws: WebSocket | null = null;
  
  // Real-time signals for notifications
  notifications = signal<AppNotification[]>([]);
  unreadCount = signal<number>(0);
  showPanel = signal<boolean>(false);
  
  // Temporal active toasts
  toasts = signal<AppNotification[]>([]);

  // Generic Event Bus for "Apply WS to everything"
  private eventBus = new Subject<SystemEvent>();
  public events$ = this.eventBus.asObservable();

  constructor() {
    this.connect();
  }

  togglePanel() {
    this.showPanel.update(v => !v);
  }

  connect() {
    const token = this.auth.token();
    if (!token) return;

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const targetUrl = `${protocol}//${host}/ws/notifications?token=${token}`;

    this.ws = new WebSocket(targetUrl);

    this.ws.onopen = () => {
      console.log('✅ WebSocket Connected');
    };

    this.ws.onmessage = (event) => {
      try {
        const rawData = JSON.parse(event.data);
        
        // Check if it's a generic System Event or a specific Notification
        if (rawData.event) {
          // It's a system event
          this.eventBus.next(rawData as SystemEvent);
        } else {
          // It's a standard notification
          this.addNotification(rawData as AppNotification);
        }
      } catch (e) {
        console.error('Error parsing WS data', e);
      }
    };

    this.ws.onerror = (err) => {
      console.error('WebSocket Error', err);
    };

    this.ws.onclose = () => {
      console.warn('WebSocket Closed. Reconnecting in 5s...');
      setTimeout(() => this.connect(), 5000);
    };
  }

  private addNotification(n: AppNotification) {
    this.notifications.update(list => [n, ...list]);
    this.unreadCount.update(c => c + 1);
    
    this.toasts.update(list => [n, ...list]);
    setTimeout(() => {
      this.toasts.update(list => list.filter(t => t.id !== n.id));
    }, 6000);
  }

  markAsRead(id: string) {
    this.notifications.update(list => list.map(n => n.id === id ? { ...n, read: true } : n));
    this.unreadCount.update(c => Math.max(0, c - 1));
  }
}
