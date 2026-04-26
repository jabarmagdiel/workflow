from fastapi import APIRouter, UploadFile, File, Body
from typing import List, Optional
import spacy
import re

router = APIRouter()

# Load NLP model (Spanish)
try:
    nlp = spacy.load("es_core_news_sm")
except:
    # Fallback if model not installed during build
    nlp = None

@router.post("/parse-command")
async def parse_command(text: str = Body(..., embed=True)):
    """
    Interpreta un texto de voz y devuelve una acción estructurada.
    Ejemplos: 
    - "crear nodo Atención al Cliente"
    - "conectar nodo A con nodo B"
    - "asignar tarea a Juan"
    """
    doc = nlp(text.lower()) if nlp else None
    
    intent = "UNKNOWN"
    entities = {}

    # Reglas simples de extracción de intención/entidades
    if any(token.lemma_ in ["crear", "agregar", "nuevo"] for token in doc) and "nodo" in text:
        intent = "CREATE_NODE"
        # Extraer nombre del nodo
        match = re.search(r"nodo (.+)", text, re.IGNORE_CONTROL)
        entities["node_name"] = match.group(1).strip() if match else "Nuevo Nodo"
        
    elif "conectar" in text or "unir" in text:
        intent = "CONNECT_NODES"
        # Buscar "conectar [A] con [B]"
        match = re.search(r"conectar (.+) con (.+)", text)
        if match:
            entities["source"] = match.group(1).strip()
            entities["target"] = match.group(2).strip()

    elif "eliminar" in text:
        intent = "DELETE_ELEMENT"
        entities["element"] = text.split("eliminar")[-1].strip()

    return {
        "raw_text": text,
        "intent": intent,
        "entities": entities,
        "confidence": 0.95 if intent != "UNKNOWN" else 0.0
    }

@router.post("/transcribe")
async def transcribe_audio(file: UploadFile = File(...)):
    # Simulación de Whisper/STT
    # En producción usar: result = model.transcribe(audio_path)
    return {"text": "crear nodo Validación de Crédito"}
