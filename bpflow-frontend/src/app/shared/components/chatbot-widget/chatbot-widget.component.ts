import { Component, inject, signal, ElementRef, ViewChild, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatbotService, ChatResponse } from '../../../core/services/chatbot.service';
import { Router } from '@angular/router';

interface Message {
  text: string;
  sender: 'user' | 'bot';
  timestamp: Date;
}

@Component({
  selector: 'app-chatbot-widget',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chatbot-widget.component.html',
  styleUrl: './chatbot-widget.component.css'
})
export class ChatbotWidgetComponent implements AfterViewChecked {
  private chatbotService = inject(ChatbotService);
  private router = inject(Router);

  @ViewChild('scrollContainer') private scrollContainer!: ElementRef;

  isOpen = signal(false);
  isLoading = signal(false);
  currentMessage = '';
  messages = signal<Message[]>([
    {
      text: '¡Hola! Soy tu asistente BPFlow. ¿En qué puedo ayudarte hoy?',
      sender: 'bot',
      timestamp: new Date()
    }
  ]);

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  toggleChat() {
    this.isOpen.update(v => !v);
  }

  sendMessage() {
    if (!this.currentMessage.trim() || this.isLoading()) return;

    const userText = this.currentMessage.trim();
    this.messages.update(msgs => [...msgs, {
      text: userText,
      sender: 'user',
      timestamp: new Date()
    }]);

    this.currentMessage = '';
    this.isLoading.set(true);

    const context = this.router.url.split('/')[1] || 'dashboard';

    this.chatbotService.sendMessage(userText, context).subscribe({
      next: (res: ChatResponse) => {
        this.messages.update(msgs => [...msgs, {
          text: res.reply,
          sender: 'bot',
          timestamp: new Date()
        }]);
        this.isLoading.set(false);
      },
      error: () => {
        this.messages.update(msgs => [...msgs, {
          text: 'Lo siento, hubo un error al procesar tu solicitud.',
          sender: 'bot',
          timestamp: new Date()
        }]);
        this.isLoading.set(false);
      }
    });
  }

  private scrollToBottom(): void {
    try {
      this.scrollContainer.nativeElement.scrollTop = this.scrollContainer.nativeElement.scrollHeight;
    } catch (err) {}
  }

  formatText(text: string): string {
    // Basic markdown support for bold and lists
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
      .replace(/•/g, '&bull;');
  }
}
