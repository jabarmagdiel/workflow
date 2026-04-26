# BPFlow — Business Process Management System
## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│  Angular SPA (Web)          Flutter App (Mobile)                │
│  - Auth Guards              - Auth Interceptors                  │
│  - Voice UI                 - Push Notifications                 │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTPS / WSS
┌────────────────────▼────────────────────────────────────────────┐
│                    API GATEWAY (Nginx)                          │
│  /api/**  →  Spring Boot    /ai/**  →  FastAPI                  │
└──────────┬────────────────────────────┬────────────────────────┘
           │                            │
┌──────────▼──────────┐    ┌────────────▼────────────────────────┐
│  SPRING BOOT (8080) │    │  FASTAPI AI SERVICE (8000)          │
│                     │    │                                      │
│  Modules:           │    │  Modules:                            │
│  - Auth / JWT       │    │  - Voice NLP (Whisper + spaCy)       │
│  - Workflow Engine  │    │  - Fraud Detection (scikit-learn)    │
│  - Task Manager     │    │  - AI Assistant (OpenAI / local)     │
│  - User CRUD        │    │  - Analytics Engine                  │
│  - Notificaciones   │    │                                      │
│  - WebSocket        │    │                                      │
│  - Audit Logs       │    │                                      │
└──────────┬──────────┘    └────────────┬────────────────────────┘
           │                            │
           └──────────┬─────────────────┘
                      │
          ┌───────────▼────────────┐
          │   MongoDB (27017)      │
          │                        │
          │  Collections:          │
          │  - users               │
          │  - roles               │
          │  - workflows           │
          │  - workflow_nodes      │
          │  - workflow_edges      │
          │  - workflow_instances  │
          │  - tasks               │
          │  - forms               │
          │  - form_fields         │
          │  - notifications       │
          │  - audit_logs          │
          │  - fraud_alerts        │
          │  - risk_scores         │
          │  - voice_commands      │
          └────────────────────────┘
```

## Módulos del Sistema

| # | Módulo | Stack | Puerto |
|---|--------|-------|--------|
| 1 | Auth / JWT | Spring Boot | 8080 |
| 2 | Workflow Designer | Spring Boot + Angular | 8080/4200 |
| 3 | Voz NLP | FastAPI + Whisper | 8000 |
| 4 | Formularios Dinámicos | Spring Boot + Angular | 8080/4200 |
| 5 | Motor de Workflow | Spring Boot | 8080 |
| 6 | Gestión Usuarios | Spring Boot | 8080 |
| 7 | Panel de Tareas | Angular | 4200 |
| 8 | Monitoreo RT | WebSockets | 8080 |
| 9 | Analítica | FastAPI + MongoDB | 8000 |
| 10 | IA Asistente | FastAPI + OpenAI | 8000 |
| 11 | Detección Fraude | FastAPI + scikit-learn | 8000 |
| 12 | Notificaciones | WebSocket | 8080 |

## Roles y Permisos

| Rol | Permisos |
|-----|----------|
| ADMIN | Todo el sistema |
| DESIGNER | Crear/editar workflows y formularios |
| MANAGER | Monitorear instancias, ver analítica |
| OFFICER | Ver y completar tareas asignadas |
| CLIENT | Ver estado de sus trámites |

## Flujo de Autenticación

```
Cliente → POST /api/auth/login → JWT (accessToken + refreshToken)
         → Cada request: Authorization: Bearer <token>
         → Token expira 1h → POST /api/auth/refresh → nuevo accessToken
         → Logout → POST /api/auth/logout → invalida refreshToken
```
