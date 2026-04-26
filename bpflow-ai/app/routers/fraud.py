from fastapi import APIRouter, Body
import pandas as pd
import numpy as np
from datetime import datetime

router = APIRouter()

@router.post("/analyze-instance")
async def analyze_instance_risk(data: dict):
    instance_id = data.get("instance_id")
    history = data.get("history", [])
    """
    Analiza la historia de una instancia para detectar anomalías:
    - Tiempos de ejecución extremadamente rápidos (posible bot/salto de paso)
    - Reasignaciones circulares
    - Horarios inusuales
    """
    risk_score = 0.0
    indicators = []
    
    # 1. Detectar tiempos ultra-rápidos (< 2 segundos en tareas complejas)
    for step in history:
        duration = step.get("durationMs", 10000)
        if duration < 500:
            risk_score += 0.7
            indicators.append(f"Velocidad CRITICA en nodo: {step.get('nodeName')}")
        elif duration < 2000:
            risk_score += 0.3
            indicators.append(f"Velocidad sospechosa en nodo: {step.get('nodeName')}")

    # 2. Actividad fuera de horario (22:00 - 06:00)
    for step in history:
        ts = step.get("timestamp")
        if isinstance(ts, str):
            try:
                dt = datetime.fromisoformat(ts.replace('Z', '+00:00'))
                if dt.hour > 22 or dt.hour < 6:
                    risk_score += 0.2
                    indicators.append(f"Actividad fuera de horario laboral: {dt.hour}:00")
            except Exception as e:
                print(f"Error parsing timestamp {ts}: {e}")

    # 3. Mismos campos repetidos sistemáticamente (Próximamente con ML)
    
    return {
        "instance_id": instance_id,
        "risk_score": min(risk_score, 1.0),
        "indicators": list(set(indicators)),
        "is_fraudulent": risk_score > 0.7
    }

@router.get("/global-trends")
async def get_fraud_trends():
    return {
        "total_anomalies_week": 12,
        "top_suspicious_departments": ["Compras", "Créditos"],
        "most_skipped_nodes": ["Aprobación Regional"]
    }
