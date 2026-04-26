# BPFlow - Intelligent Workflow Management System

BPFlow is a modern, microservice-based workflow management platform that integrates AI-driven analysis, voice commands, and real-time monitoring.

## 🚀 Project Architecture

- **bpflow-frontend**: Angular-based responsive dashboard with glassmorphism design.
- **bpflow-backend**: Spring Boot application managing core business logic and MongoDB integration.
- **bpflow-ai**: (In progress) Python-based service for intelligent workflow analysis and intent detection.
- **bpflow-mobile**: Flutter-based mobile application (in development).

## 🛠 Features

- **Dynamic Workflow Designer**: Drag-and-drop interface for business process modelling.
- **Real-Time Dashboards**: Health monitoring, fraud detection, and bottleneck analysis.
- **AI Chatbot**: Context-aware assistant for system querying and task management.
- **Audit Logging**: Comprehensive PDF reports and event tracking.
- **Dockerized Environment**: easy deployment using Docker Compose.

## 📦 Getting Started

### Prerequisites

- Docker & Docker Compose
- Java 17+
- Node.js & Angular CLI

### Local Deployment

1. Clone the repository.
2. Build the backend: `mvn clean install` inside `bpflow-backend`.
3. Build the frontend: `npm install` and `npm run build` inside `bpflow-frontend`.
4. Run the full stack: `docker-compose up --build`.

## 🌐 Deployment

This repository is ready for CI/CD deployment on platforms like GitHub Actions, Vercel, or Heroku.
