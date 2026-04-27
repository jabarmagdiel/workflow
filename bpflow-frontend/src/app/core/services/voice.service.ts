import { Injectable, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs';

export interface VoiceCommand {
    intent: string;
    entities: any;
    rawText: string;
}

@Injectable({ providedIn: 'root' })
export class VoiceService {
    private http = inject(HttpClient);

    isListening = signal<boolean>(false);
    lastTranscript = signal<string>('');
    isSupported = signal<boolean>(false);

    // Canal para que los componentes escuchen comandos procesados
    private commandBus = new Subject<VoiceCommand>();
    public commandBus$ = this.commandBus.asObservable();

    private recognition: any = null;

    constructor() {
        // Guard: solo inicializar si el navegador soporta la API
        try {
            const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
            if (SpeechRecognition) {
                this.recognition = new SpeechRecognition();
                this.recognition.lang = 'es-ES';
                this.recognition.continuous = true; // Mantener encendido mientras se habla
                this.recognition.interimResults = true; // Mostrar resultados parciales

                this.recognition.onresult = (event: any) => {
                    let interimTranscript = '';
                    let finalTranscript = '';

                    for (let i = event.resultIndex; i < event.results.length; ++i) {
                        if (event.results[i].isFinal) {
                            finalTranscript += event.results[i][0].transcript;
                        } else {
                            interimTranscript += event.results[i][0].transcript;
                        }
                    }

                    const currentText = finalTranscript || interimTranscript;
                    this.lastTranscript.set(currentText);

                    if (finalTranscript) {
                        this.processCommand(finalTranscript.trim());
                    }
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

    private executeAction(res: any) {
        if (!res || res.intent === 'UNKNOWN') {
            console.warn('Voice: Intent not recognized', res);
            return;
        }

        const command: VoiceCommand = {
            intent: res.intent,
            entities: res.entities,
            rawText: res.raw_text
        };

        console.log('🚀 Despachando comando de voz:', command);
        this.commandBus.next(command);
    }
}
