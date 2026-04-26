import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ChatResponse {
  reply: string;
  type: string;
  data?: any;
}

@Injectable({
  providedIn: 'root'
})
export class ChatbotService {
  private http = inject(HttpClient);
  private apiUrl = '/api/chatbot/message';

  sendMessage(message: string, context?: string): Observable<ChatResponse> {
    return this.http.post<ChatResponse>(this.apiUrl, {
      message,
      context
    });
  }
}
