import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotificationService, AppNotification } from '../../core/services/notification.service';
import { trigger, transition, style, animate } from '@angular/animations';

@Component({
  selector: 'app-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css'],
  animations: [
    trigger('toastAnimation', [
      transition(':enter', [
        style({ transform: 'translateX(100%)', opacity: 0 }),
        animate('0.3s cubic-bezier(0.16, 1, 0.3, 1)', style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('0.3s cubic-bezier(0.16, 1, 0.3, 1)', style({ transform: 'translateX(100%)', opacity: 0 }))
      ])
    ])
  ]
})
export class NotificationsComponent {
  public notificationService = inject(NotificationService);

  getIcon(type: string): string {
    switch (type) {
      case 'TASK_ASSIGNED': return '📝';
      case 'TASK_OVERDUE': return '⏰';
      case 'FRAUD_ALERT': return '🚨';
      case 'SLA_BREACH': return '⚠️';
      case 'SYSTEM_MESSAGE': return '💬';
      default: return '🔔';
    }
  }

  getBorderColor(type: string): string {
    if (type.includes('FRAUD') || type.includes('BREACH')) return 'border-rose-500/50 bg-rose-500/10 text-rose-400';
    if (type.includes('TASK')) return 'border-blue-500/50 bg-blue-500/10 text-blue-400';
    if (type.includes('SLA_WARNING')) return 'border-amber-500/50 bg-amber-500/10 text-amber-400';
    return 'border-indigo-500/50 bg-indigo-500/10 text-indigo-400';
  }
}
