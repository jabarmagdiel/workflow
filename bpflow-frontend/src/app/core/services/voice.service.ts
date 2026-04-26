import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class VoiceService {
    private http = inject(HttpClient);

    isListening = signal<boolean>(false);
    lastTranscript = signal<string>('');
    isSupported = signal<boolean>(false);

    private recognition: any = null;

    constructor() {
        // Guard: solo inicializar si el navegador soporta la API
        try {
            const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
            if (SpeechRecognition) {
                this.recognition = new SpeechRecognition();
                this.recognition.lang = 'es-ES';
                this.recognition.continuous = false;
                this.recognition.interimResults = false;

                this.recognition.onresult = (event: any) => {
                    const transcript = event.results[0][0].transcript;
                    this.lastTranscript.set(transcript);
                    this.processCommand(transcript);
                };

                this.recognition.onend = () => this.isListening.set(false);
                this.recognition.onerror = () => this.isListening.set(false);
                this.isSupported.set(true);
            }
        } catch (e) {
            console.warn('Speech Recognition not supported in this browser.');
        }
    }

    startListening() {
        if (!this.recognition) {
            alert('Tu navegador no soporta comandos de voz. Usa Chrome.');
            return;
        }
        this.isListening.set(true);
        this.recognition.start();
    }

    stopListening() {
        if (this.recognition) {
            this.recognition.stop();
        }
        this.isListening.set(false);
    }

    private processCommand(text: string) {
        this.http.post<any>('/ai/voice/parse-command', { text }).subscribe({
            next: (res: any) => this.executeAction(res),
            error: () => console.warn('AI voice service unavailable')
        });
    }

    private executeAction(action: any) {
        switch (action?.intent) {
            case 'CREATE_NODE':
                console.log('Voice: CREATE_NODE', action.entities?.node_name);
                break;
            case 'CONNECT_NODES':
                console.log('Voice: CONNECT_NODES', action.entities);
                break;
            default:
                console.log('Voice: Unknown intent', action);
        }
    }
}
