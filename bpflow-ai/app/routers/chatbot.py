from fastapi import APIRouter, Request
from pydantic import BaseModel
from typing import Optional
import re
from datetime import datetime

router = APIRouter()

class ChatMessage(BaseModel):
    message: str
    userId: Optional[str] = None
    context: Optional[str] = None  # current page: workflows, tasks, instances, etc.

class ChatResponse(BaseModel):
    reply: str
    type: str = "text"  # text | data | action
    data: Optional[dict] = None

# ─── Intent patterns ─────────────────────────────────────────────
INTENTS = [
    # System stats
    (r"(cuántos?|cantidad|total).*(workflow|proceso)", "count_workflows"),
    (r"(cuántas?|cuántos?|total).*(instancia|proceso.corriendo|proceso.activo)", "count_instances"),
    (r"(cuántas?|cuántos?|total).*(tarea|task)", "count_tasks"),
    (r"(cuántos?|total).*(usuario|user)", "count_users"),
    (r"(resumen|stats|estadística|dashboard|estado del sistema)", "summary"),

    # Workflows
    (r"(lista|mostrar|ver|dame).*(workflow|proceso)", "list_workflows"),
    (r"(workflow|proceso).*(publicado|published)", "list_published"),
    (r"(workflow|proceso).*(borrador|draft)", "list_draft"),

    # Tasks
    (r"(tarea|task).*(pendiente|nueva|sin atender)", "pending_tasks"),
    (r"(tarea|task).*(vencida|overdue)", "overdue_tasks"),
    (r"mis\s+tareas", "my_tasks"),

    # Instances
    (r"(instancia|proceso).*(activo|corriendo|running)", "running_instances"),
    (r"(instancia|proceso).*(completado|finalizado)", "completed_instances"),

    # Audit
    (r"(último|reciente|bitácora|auditoria|log)", "recent_logs"),

    # Help
    (r"(ayuda|help|qué puedes|qué sabes|comandos|funciones)", "help"),
    (r"(hola|buenas?|saludos?|hey)", "greeting"),
    (r"(gracias?|thanks)", "thanks"),
]

def detect_intent(text: str) -> str:
    t = text.lower()
    for pattern, intent in INTENTS:
        if re.search(pattern, t):
            return intent
    return "unknown"

async def get_db(request: Request):
    return request.app.database

@router.post("/chat", response_model=ChatResponse)
async def chat(msg: ChatMessage, request: Request):
    db = await get_db(request)
    intent = detect_intent(msg.message)
    reply, data = await handle_intent(intent, db)
    return ChatResponse(reply=reply, data=data)

async def handle_intent(intent: str, db) -> tuple[str, Optional[dict]]:
    try:
        if intent == "greeting":
            return ("¡Hola! Soy **BPFlow AI**, tu asistente de procesos. "
                    "Puedo ayudarte con información sobre workflows, tareas e instancias. "
                    "¿Qué necesitas?", None)

        elif intent == "thanks":
            return ("¡Con gusto! ¿Hay algo más en lo que pueda ayudarte?", None)

        elif intent == "help":
            return (
                "**Puedo ayudarte con:**\n"
                "• *¿Cuántos workflows hay?*\n"
                "• *Lista los workflows publicados*\n"
                "• *¿Cuántas instancias están activas?*\n"
                "• *¿Cuántas tareas están pendientes?*\n"
                "• *Muéstrame los últimos eventos*\n"
                "• *Resumen del sistema*\n"
                "• *¿Cuántos usuarios hay?*",
                None
            )

        elif intent == "summary":
            wf_count  = await db.workflows.count_documents({})
            inst_run  = await db.workflow_instances.count_documents({"status": "RUNNING"})
            task_new  = await db.tasks.count_documents({"status": "NEW"})
            task_done = await db.tasks.count_documents({"status": "COMPLETED"})
            users     = await db.users.count_documents({})
            return (
                f"**Estado actual del sistema:**\n"
                f"📐 **{wf_count}** workflows registrados\n"
                f"🔄 **{inst_run}** instancias en ejecución\n"
                f"⏳ **{task_new}** tareas pendientes\n"
                f"✅ **{task_done}** tareas completadas\n"
                f"👥 **{users}** usuarios",
                {"workflows": wf_count, "running": inst_run, "pending": task_new, "completed": task_done}
            )

        elif intent == "count_workflows":
            n = await db.workflows.count_documents({})
            return (f"Hay **{n}** workflows registrados en el sistema.", {"count": n})

        elif intent == "count_instances":
            total   = await db.workflow_instances.count_documents({})
            running = await db.workflow_instances.count_documents({"status": "RUNNING"})
            return (f"Hay **{total}** instancias en total, de las cuales **{running}** están activas.", {"total": total, "running": running})

        elif intent == "count_tasks":
            total = await db.tasks.count_documents({})
            pend  = await db.tasks.count_documents({"status": "NEW"})
            return (f"Hay **{total}** tareas en el sistema. **{pend}** están pendientes.", {"total": total, "pending": pend})

        elif intent == "count_users":
            n = await db.users.count_documents({})
            return (f"El sistema tiene **{n}** usuarios registrados.", {"count": n})

        elif intent == "list_workflows":
            cursor = db.workflows.find({}, {"name": 1, "status": 1, "version": 1}).limit(8)
            wfs = await cursor.to_list(8)
            if not wfs:
                return ("No hay workflows registrados aún.", None)
            lines = "\n".join(f"• **{w['name']}** ({w.get('status','?')}) v{w.get('version',1)}" for w in wfs)
            return (f"**Workflows registrados:**\n{lines}", {"items": len(wfs)})

        elif intent == "list_published":
            cursor = db.workflows.find({"status": "PUBLISHED"}, {"name": 1, "version": 1}).limit(8)
            wfs = await cursor.to_list(8)
            if not wfs:
                return ("No hay workflows publicados actualmente.", None)
            lines = "\n".join(f"• **{w['name']}** v{w.get('version',1)}" for w in wfs)
            return (f"**Workflows publicados:**\n{lines}", None)

        elif intent == "list_draft":
            cursor = db.workflows.find({"status": "DRAFT"}, {"name": 1}).limit(8)
            wfs = await cursor.to_list(8)
            if not wfs:
                return ("No hay workflows en borrador.", None)
            lines = "\n".join(f"• {w['name']}" for w in wfs)
            return (f"**Workflows en borrador:**\n{lines}", None)

        elif intent == "pending_tasks":
            n = await db.tasks.count_documents({"status": "NEW"})
            cursor = db.tasks.find({"status": "NEW"}, {"title": 1, "assignedRole": 1}).limit(5)
            tasks = await cursor.to_list(5)
            lines = "\n".join(f"• {t.get('title','Sin título')} [{t.get('assignedRole','')}]" for t in tasks)
            return (f"Hay **{n}** tareas pendientes:{chr(10) if tasks else ''}{lines}", {"count": n})

        elif intent == "overdue_tasks":
            n = await db.tasks.count_documents({"overdue": True})
            if n == 0:
                return ("¡Excelente! No hay tareas vencidas en este momento. 🎉", {"count": 0})
            return (f"Hay **{n}** tareas vencidas que requieren atención urgente. ⚠️", {"count": n})

        elif intent == "running_instances":
            cursor = db.workflow_instances.find({"status": "RUNNING"}, {"reference": 1, "workflowName": 1}).limit(6)
            insts = await cursor.to_list(6)
            if not insts:
                return ("No hay instancias de proceso en ejecución actualmente.", None)
            lines = "\n".join(f"• {i.get('reference','?')} — {i.get('workflowName','?')}" for i in insts)
            return (f"**Instancias en ejecución:**\n{lines}", None)

        elif intent == "completed_instances":
            n = await db.workflow_instances.count_documents({"status": "COMPLETED"})
            return (f"Se han completado **{n}** instancias de proceso.", {"count": n})

        elif intent == "recent_logs":
            cursor = db.audit_logs.find({}, {"action": 1, "userEmail": 1, "timestamp": 1}).sort("timestamp", -1).limit(5)
            logs = await cursor.to_list(5)
            if not logs:
                return ("No hay eventos registrados en la bitácora.", None)
            lines = "\n".join(
                f"• **{l.get('action','?')}** — {l.get('userEmail','anon')} "
                for l in logs
            )
            return (f"**Últimos eventos:**\n{lines}", None)

        else:
            return (
                "No entendí bien tu pregunta. Prueba con algo como:\n"
                "• *¿Cuántos workflows hay?*\n"
                "• *Resumen del sistema*\n"
                "• *Tareas pendientes*\n"
                "• *Escribe 'ayuda' para ver más opciones*",
                None
            )
    except Exception as e:
        return (f"⚠️ Error al consultar el sistema: {str(e)}", None)
