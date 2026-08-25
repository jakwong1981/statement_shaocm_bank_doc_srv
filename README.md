# Simple Report Centre

Enterprise-grade report ingestion and management platform. Third-party systems upload PDFs via authenticated APIs, the engine applies dynamic multi-zone watermarks, and administrators manage everything through a web portal.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2 (Java 17) |
| Frontend | Vue.js 3 + Vite + Pinia |
| Database | IBM DB2 (prod) / H2 (dev/sit) |
| Message Broker | RabbitMQ |
| Object Storage | MinIO (S3-compatible) |
| PDF Processing | Apache PDFBox 3.x |
| Containerization | Docker Compose |

## Quick Start (SIT Environment)

```bash
cd c:\report-centre
docker-compose up -d --build
```

This starts 5 containers:
- **sit-frontend** - Vue.js admin portal on http://localhost:80
- **sit-backend** - Spring Boot API on http://localhost:8080
- **sit-minio** - Object storage on http://localhost:9001
- **sit-rabbitmq** - Message broker on http://localhost:15672
- **sit-minio-init** - Auto-creates storage buckets

Default login: **admin / admin123**

## Local Development (Without Docker)

```bash
# 1. Start only infrastructure containers
docker-compose up -d minio rabbitmq

# 2. Start backend (needs Java 17+ and Maven)
cd backend
mvn spring-boot:run

# 3. Start frontend (needs Node.js 18+)
cd ../frontend
npm install
npm run dev
```

Then open http://localhost:5173

## Project Structure

```
report-centre/
+-- docker-compose.yml
+-- backend/
|   +-- pom.xml
|   +-- Dockerfile
|   +-- src/main/java/com/reportcentre/
|       +-- config/         (MinIO, RabbitMQ, Web configs)
|       +-- controller/     (REST controllers)
|       +-- dto/            (Request/Response DTOs)
|       +-- entity/         (JPA entities + enums)
|       +-- exception/      (Global error handler)
|       +-- repository/     (Spring Data JPA)
|       +-- security/       (JWT, API Key, HMAC filters)
|       +-- service/        (Business logic)
|       +-- watermark/      (PDF watermark engine)
+-- frontend/
    +-- package.json
    +-- Dockerfile
    +-- nginx.conf
    +-- src/
        +-- router/         (Vue Router)
        +-- services/       (Axios API clients)
        +-- stores/         (Pinia state)
        +-- views/          (Page components)
```

## API Endpoints

### External (API Key + HMAC)
- ``POST /api/v1/external/reports`` - Upload PDF
- ``GET /api/v1/external/reports/{id}`` - Check status
- ``GET /api/v1/external/reports/{id}/download`` - Download

### Admin (JWT Bearer)
- ``POST /api/v1/admin/auth/login`` - Login
- ``GET /api/v1/admin/reports`` - List reports
- ``POST /api/v1/admin/reports`` - Upload report
- ``GET /api/v1/admin/clients`` - List API clients (SUPER_ADMIN)
- ``POST /api/v1/admin/clients`` - Create client (SUPER_ADMIN)
- ``GET /api/v1/admin/audit-logs`` - Query audit trail

## Default Users (SIT/Dev)

| Username | Password | Role |
|---|---|---|
| admin | admin123 | SUPER_ADMIN |
| operator | admin123 | OPERATOR |
| auditor | admin123 | AUDITOR |
