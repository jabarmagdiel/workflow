from fastapi import FastAPI, Depends, HTTPException, UploadFile, File
from fastapi.middleware.cors import CORSMiddleware
import os
from app.routers import voice, fraud, analytics, chatbot
from motor.motor_asyncio import AsyncIOMotorClient

app = FastAPI(title="BPFlow AI Service", version="1.0.0")

# MongoDB connection
MONGO_URI = os.getenv("MONGODB_URI", "mongodb://bpflow:bpflow_secret_2024@localhost:27017/bpflow_db?authSource=admin")
client = AsyncIOMotorClient(MONGO_URI)
db = client.bpflow_db

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup_db_client():
    app.mongodb_client = client
    app.database = db

@app.on_event("shutdown")
async def shutdown_db_client():
    app.mongodb_client.close()

# Routes
app.include_router(voice.router,    prefix="/voice",     tags=["Voice"])
app.include_router(fraud.router,    prefix="/fraud",     tags=["Fraud"])
app.include_router(analytics.router,prefix="/analytics", tags=["Analytics"])
app.include_router(chatbot.router,  prefix="/chat",      tags=["Chatbot"])

@app.get("/health")
async def health_check():
    return {"status": "healthy", "database": "connected" if db is not None else "disconnected"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
