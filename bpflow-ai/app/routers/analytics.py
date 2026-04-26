from fastapi import APIRouter
import random

router = APIRouter()

@router.get("/bottlenecks")
async def detect_bottlenecks(workflow_id: str):
    """
    Identifica nodos que están retrasando el flujo global.
    """
    return {
        "workflow_id": workflow_id,
        "bottlenecks": [
            {"node": "Asesoría Legal", "avg_delay_hours": 48.5, "impact": "High"},
            {"node": "Validación de Fondos", "avg_delay_hours": 12.2, "impact": "Medium"}
        ],
        "recommendation": "Aumentar personal en Asesoría Legal o simplificar formulario adjunto."
    }

@router.get("/predict-completion")
async def predict_completion_time(instance_id: str):
    return {
        "instance_id": instance_id,
        "estimated_completion": "2024-03-25T14:00:00",
        "probability_on_time": 0.85
    }
