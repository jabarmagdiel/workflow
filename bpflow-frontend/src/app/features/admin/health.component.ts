import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HealthService } from '../../core/services/health.service';

@Component({
  selector: 'app-health',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="p-8">
      <div class="mb-8">
        <h2 class="text-2xl font-bold text-slate-100">Estado del Sistema</h2>
        <p class="text-slate-400">Monitor de salud de microservicios e infraestructura</p>
      </div>

      <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        @for (service of healthService.systemStatus(); track service.name) {
          <div class="p-6 rounded-2xl border border-slate-800 bg-slate-900/50 backdrop-blur-md transition-all hover:border-slate-700">
            <div class="flex justify-between items-start mb-6">
              <div>
                <h3 class="text-lg font-bold text-slate-200">{{ service.name }}</h3>
                <p class="text-xs text-slate-500 mt-1">Sincronizado cada 10s</p>
              </div>
              <div [class]="'px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider ' + 
                 (service.status === 'UP' ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' : 'bg-rose-500/10 text-rose-400 border border-rose-500/20')">
                {{ service.status }}
              </div>
            </div>

            <div class="space-y-4">
              <div class="flex justify-between items-center text-sm">
                <span class="text-slate-400">Latencia de Respuesta</span>
                <span class="font-mono" [class.text-emerald-400]="service.latency! < 100" [class.text-amber-400]="service.latency! >= 100">
                  {{ service.latency === -1 ? 'N/A' : service.latency + 'ms' }}
                </span>
              </div>
              
              <div class="h-px bg-slate-800/50"></div>
              
              <div class="text-xs">
                <p class="text-slate-500 mb-2 uppercase font-bold tracking-tighter">Indicadores</p>
                <div class="grid grid-cols-2 gap-2">
                  @if (service.name === 'Backend Services') {
                    <div class="flex items-center gap-2 text-slate-400">
                      <span class="w-2 h-2 rounded-full bg-emerald-500"></span> MongoDB
                    </div>
                    <div class="flex items-center gap-2 text-slate-400">
                      <span class="w-2 h-2 rounded-full bg-emerald-500"></span> WebSocket
                    </div>
                  } @else {
                    <div class="flex items-center gap-2 text-slate-400">
                      <span class="w-2 h-2 rounded-full bg-emerald-500"></span> Model: GPT-4o
                    </div>
                    <div class="flex items-center gap-2 text-slate-400">
                      <span class="w-2 h-2 rounded-full bg-emerald-500"></span> Mongo Driver
                    </div>
                  }
                </div>
              </div>
            </div>

            <div class="mt-6 pt-4 border-t border-slate-800/30 flex items-center gap-2 text-[10px] text-slate-500">
               <span class="relative flex h-2 w-2">
                 <span class="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                 <span class="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
               </span>
               En vivo
            </div>
          </div>
        }
      </div>

      <!-- Detail Table for Admin -->
      <div class="mt-10 p-6 rounded-2xl border border-slate-800 bg-slate-900/30">
        <h4 class="text-slate-300 font-bold mb-4 flex items-center gap-2">
          <span>📊</span> Métricas de Infraestructura
        </h4>
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead>
              <tr class="text-slate-500 border-b border-slate-800">
                <th class="pb-3 font-semibold">Servicio</th>
                <th class="pb-3 font-semibold">Puerto</th>
                <th class="pb-3 font-semibold">Trafico</th>
                <th class="pb-3 font-semibold">Seguridad</th>
              </tr>
            </thead>
            <tbody class="text-slate-400">
              <tr class="border-b border-slate-800/30">
                <td class="py-4">Nginx Gateway</td>
                <td class="py-4">80</td>
                <td class="py-4">Normal</td>
                <td class="py-4"><span class="text-emerald-500">TLS Ready</span></td>
              </tr>
              <tr class="border-b border-slate-800/30">
                <td class="py-4">Spring Boot</td>
                <td class="py-4">8080</td>
                <td class="py-4">24 req/m</td>
                <td class="py-4"><span class="text-blue-500">JWT Filter</span></td>
              </tr>
              <tr>
                <td class="py-4">FastAPI AI</td>
                <td class="py-4">8000</td>
                <td class="py-4">8 req/m</td>
                <td class="py-4"><span class="text-blue-500">Internal Only</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  `
})
export class HealthComponent {
  public healthService = inject(HealthService);
}
