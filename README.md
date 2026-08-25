# Simple Report Centre

Enterprise-grade PDF report ingestion, watermarking, and management platform. Third-party systems upload PDFs via authenticated APIs, the engine applies dynamic multi-zone watermarks, and administrators manage everything through a web portal.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Features](#features)
- [Security Model](#security-model)
- [Report Lifecycle](#report-lifecycle)
- [Watermark Engine](#watermark-engine)
- [Database Schema](#database-schema)
- [API Reference](#api-reference)
- [Frontend Application](#frontend-application)
- [Configuration](#configuration)
- [Deployment](#deployment)
- [Default Credentials](#default-credentials)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Docker Compose (sit-net)                        │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│   ┌─────────────┐         ┌──────────────────┐         ┌───────────┐   │
│   │   Frontend  │ ──────► │     Backend      │ ──────► │   MinIO   │   │
│   │  (Vue.js 3) │  :8880  │  (Spring Boot)   │  :9000  │  (S3)     │   │
│   │   NGINX     │         │    :8080         │         │  :9001    │   │
│   └─────────────┘         └────────┬─────────┘         └───────────┘   │
│                                     │                                    │
│                                     ▼                                    │
│                            ┌──────────────────┐                         │
│                            │    RabbitMQ      │                         │
│                            │     :5672        │                         │
│                            │     :15672       │                         │
│                            └──────────────────┘                         │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Container Topology

| Container | Image | Port(s) | Purpose |
|-----------|-------|---------|---------|
| sit-frontend | Custom (Node + NGINX) | 8880:80 | Vue.js admin portal |
| sit-backend | Custom (Maven + JRE) | 8080:8080 | Spring Boot REST API |
| sit-minio | minio/minio | 9000:9000, 9001:9001 | S3-compatible object storage |
| sit-rabbitmq | rabbitmq:3-management | 5672:5672, 15672:15672 | Message broker + management UI |
| sit-minio-init | minio/mc | — | Auto-creates storage buckets |

---

## Technology Stack

### Backend

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.2.5 |
| Language | Java | 17 |
| Build Tool | Maven | 3.9+ |
| Database (Prod) | IBM DB2 | 11.5 |
| Database (Dev/SIT) | H2 (DB2 mode) | 2.2.x |
| ORM | Spring Data JPA / Hibernate | 6.x |
| Security | Spring Security | 6.2 |
| Auth (Admin) | JWT (HMAC-SHA256) | jjwt 0.12.x |
| Auth (External) | API Key + HMAC-SHA256 | Custom |
| PDF Processing | Apache PDFBox | 3.x |
| QR Code | Google ZXing | 3.5.x |
| Object Storage | MinIO Java SDK | 8.5.x |
| Message Broker | Spring AMQP (RabbitMQ) | 3.1.x |
| Utilities | Lombok | 1.18.x |

### Frontend

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Vue.js | 3.4.x |
| Build Tool | Vite | 5.x |
| State Management | Pinia | 2.x |
| Router | Vue Router | 4.x |
| HTTP Client | Axios | 1.x |
| PDF Viewer | PDF.js | 3.x |
| Web Server | NGINX | 1.25 |

---

## Project Structure

```
statement_shaocm_bank_doc_srv/
├── docker-compose.yml              # Multi-service orchestration
├── docker/
│   └── minio-init.sh               # MinIO bucket initialization
├── backend/
│   ├── pom.xml                     # Maven dependencies
│   ├── Dockerfile                  # Multi-stage build (Maven → JRE)
│   └── src/main/
│       ├── java/com/reportcentre/
│       │   ├── ReportCentreApplication.java
│       │   ├── config/             # MinIO, RabbitMQ, Web configs
│       │   ├── controller/         # REST controllers (Admin + External)
│       │   ├── dto/                # Request/Response DTOs
│       │   ├── entity/             # JPA entities + enums
│       │   ├── exception/          # Global error handler + error logging
│       │   ├── repository/         # Spring Data JPA interfaces
│       │   ├── security/           # JWT, API Key, HMAC filters
│       │   ├── service/            # Business logic layer
│       │   └── watermark/          # PDF watermark engine
│       └── resources/
│           ├── application.yml     # Multi-profile configuration
│           ├── data.sql            # Seed data (dev/sit)
│           └── db/migration/
│               ├── V1__init_schema.sql     # DB2 DDL
│               └── V2__add_system_error_logs.sql  # Error log table
└── frontend/
    ├── package.json                # NPM dependencies
    ├── Dockerfile                  # Multi-stage build (Node → NGINX)
    ├── nginx.conf                  # SPA routing + API proxy
    ├── vite.config.js              # Dev proxy config
    └── src/
        ├── main.js                 # Vue app entry
        ├── App.vue                 # Root component
        ├── router/index.js         # Route definitions + guards
        ├── services/               # Axios API clients
        ├── stores/                 # Pinia state stores
        ├── views/                  # Page components
        └── assets/main.css         # Global styles
```

---

## Features

### Core Capabilities

- **PDF Report Ingestion** — Accept PDF uploads from both admin users and external third-party systems
- **Dynamic Multi-Zone Watermarking** — Apply watermarks with client info, timestamps, checksums, and QR codes
- **Asynchronous Processing** — RabbitMQ-driven message queue for non-blocking watermark operations
- **Role-Based Access Control** — Three-tier permission model (SUPER_ADMIN, OPERATOR, AUDITOR)
- **Immutable Audit Trail** — Track all system actions with actor, timestamp, IP, and user agent
- **System Error Logging** — Automatic capture of all exceptions with stack traces, searchable by timestamp
- **Third-Party Client Management** — Issue API keys and secrets for external integrations
- **S3-Compatible Storage** — MinIO for scalable, distributed file storage
- **Multi-Environment Support** — Dev (H2), SIT (H2 + Docker), and Production (DB2) profiles

---

## Security Model

### Dual Security Filter Chain

The application implements two independent security chains:

| Chain | Path Pattern | Auth Method | Filter |
|-------|--------------|-------------|--------|
| Admin API | `/api/v1/admin/**` | JWT Bearer Token | `JwtAuthFilter` |
| External API | `/api/v1/external/**` | API Key Header | `ApiKeyAuthFilter` + `HmacSignatureFilter` |

### Authentication Flow

**Admin (JWT):**
1. Client sends `POST /api/v1/admin/auth/login` with username/password
2. Server validates credentials and returns access + refresh tokens
3. Client includes `Authorization: Bearer <token>` on subsequent requests
4. `JwtAuthFilter` validates token signature and extracts user claims

**External (API Key + HMAC):**
1. Client includes `X-API-KEY: <key>` header on all requests
2. `ApiKeyAuthFilter` validates API key against stored hash
3. (Optional) `HmacSignatureFilter` validates `X-Signature` header using HMAC-SHA256

### Password & Secret Storage

- User passwords: BCrypt hashed
- API secrets: BCrypt hashed (shown only once at creation)
- JWT signing: HMAC-SHA256 with configurable secret key

---

## Report Lifecycle

```
┌──────────┐     ┌────────────────┐     ┌─────────────┐     ┌──────────┐
│  Upload  │────►│   Validate &   │────►│   Publish   │────►│ Watermark│
│  (PDF)   │     │  Store (MinIO) │     │  (RabbitMQ) │     │  Engine  │
└──────────┘     └────────────────┘     └─────────────┘     └────┬─────┘
                                                                  │
                    ┌──────────────┐                              │
                    │    READY     │◄─────────────────────────────┘
                    │  (Download)  │     Update status + checksum
                    └──────────────┘
```

### Status Transitions

| Status | Description |
|--------|-------------|
| `PENDING_WATERMARK` | File uploaded, awaiting processing |
| `PROCESSING` | Watermark engine is applying watermarks |
| `READY` | Watermarked PDF available for download |
| `FAILED` | Processing error occurred |

---

## Watermark Engine

The `WatermarkEngine` uses Apache PDFBox 3.x to apply four distinct watermark zones:

| Zone | Location | Content |
|------|----------|---------|
| Zone 1 | Header (top) | Benchmark reference tag + classification |
| Zone 2 | Center (diagonal) | Client ID + upload timestamp |
| Zone 3 | Footer (bottom) | SHA-256 checksum + page numbering |
| Zone 4 | QR Code (corner) | Report ID for verification |

### Processing Pipeline

1. Download raw PDF from MinIO staging bucket
2. Iterate through all pages applying watermark zones
3. Generate QR code using ZXing library
4. Compute SHA-256 checksum of final watermarked PDF
5. Upload watermarked version to `reports-watermarked` bucket
6. Update Report entity with status, checksum, and page count

---

## Database Schema

### USERS

| Column | Type | Description |
|--------|------|-------------|
| USERNAME | VARCHAR(50) PK | User login name |
| PASSWORD_HASH | VARCHAR(255) | BCrypt hashed password |
| ROLE | VARCHAR(20) | SUPER_ADMIN / OPERATOR / AUDITOR |
| IS_ACTIVE | BOOLEAN | Account active status |
| CREATED_AT | TIMESTAMP | Account creation time |

### THIRD_PARTY_CLIENTS

| Column | Type | Description |
|--------|------|-------------|
| ID | VARCHAR(36) PK | UUID client identifier |
| CLIENT_NAME | VARCHAR(100) | Display name |
| API_KEY | VARCHAR(255) | Unique API key (Base64Url) |
| API_SECRET_HASH | VARCHAR(255) | BCrypt hashed secret |
| STATUS | VARCHAR(20) | ACTIVE / SUSPENDED / REVOKED |
| CREATED_AT | TIMESTAMP | Creation time |

### REPORTS

| Column | Type | Description |
|--------|------|-------------|
| ID | VARCHAR(36) PK | UUID report identifier |
| FILE_NAME | VARCHAR(255) | Original filename |
| FILE_SIZE | BIGINT | File size in bytes |
| UPLOADER_TYPE | VARCHAR(20) | ADMIN / EXTERNAL |
| UPLOADER_ID | VARCHAR(100) | Username or client ID |
| BENCHMARK_TAG | VARCHAR(100) | Benchmark reference |
| STATUS | VARCHAR(20) | Current processing status |
| RAW_STORAGE_PATH | VARCHAR(500) | MinIO path (original) |
| WATERMARKED_STORAGE_PATH | VARCHAR(500) | MinIO path (watermarked) |
| CHECKSUM_SHA256 | VARCHAR(64) | SHA-256 of watermarked PDF |
| PAGE_COUNT | INTEGER | Number of pages |
| CREATED_AT | TIMESTAMP | Upload time |
| UPDATED_AT | TIMESTAMP | Last status change |

### AUDIT_LOGS

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT PK | Auto-increment ID |
| ACTOR_TYPE | VARCHAR(20) | USER / SYSTEM / EXTERNAL |
| ACTOR_ID | VARCHAR(100) | Username or client ID |
| ACTION | VARCHAR(30) | LOGIN, UPLOAD, DOWNLOAD, etc. |
| TARGET_REPORT_ID | VARCHAR(36) | Related report (nullable) |
| IP_ADDRESS | VARCHAR(45) | Client IP address |
| USER_AGENT | VARCHAR(500) | Browser/client info |
| CREATED_AT | TIMESTAMP | Event time |

### SYSTEM_ERROR_LOGS

| Column | Type | Description |
|--------|------|-------------|
| ID | BIGINT PK | Auto-increment ID |
| ERROR_MESSAGE | VARCHAR(2000) | Exception message |
| EXCEPTION_CLASS | VARCHAR(500) | Fully qualified exception class |
| STACK_TRACE | CLOB | Full stack trace |
| HTTP_METHOD | VARCHAR(10) | GET, POST, etc. |
| REQUEST_URI | VARCHAR(1000) | Request path |
| HTTP_STATUS | INTEGER | HTTP response status code |
| USER_AGENT | VARCHAR(512) | Client browser/agent info |
| IP_ADDRESS | VARCHAR(45) | Client IP address |
| CREATED_AT | TIMESTAMP | Error occurrence time |

---

## API Reference

### Admin API (JWT Bearer)

| Method | Endpoint | Description | Role |
|--------|----------|-------------|------|
| POST | `/api/v1/admin/auth/login` | Authenticate and get tokens | Public |
| POST | `/api/v1/admin/auth/refresh` | Refresh access token | Public |
| GET | `/api/v1/admin/reports` | List reports (paginated) | All authenticated |
| POST | `/api/v1/admin/reports` | Upload a PDF report | All authenticated |
| GET | `/api/v1/admin/reports/{id}` | Get report details | All authenticated |
| GET | `/api/v1/admin/reports/{id}/download` | Download watermarked PDF | All authenticated |
| GET | `/api/v1/admin/clients` | List third-party clients | SUPER_ADMIN |
| POST | `/api/v1/admin/clients` | Create new client | SUPER_ADMIN |
| PATCH | `/api/v1/admin/clients/{id}/status` | Update client status | SUPER_ADMIN |
| GET | `/api/v1/admin/audit-logs` | Query audit trail | AUDITOR, SUPER_ADMIN |
| GET | `/api/v1/admin/error-logs` | Query system error logs (supports `from`/`to` timestamp filter) | SUPER_ADMIN |

### External API (API Key + HMAC)

| Method | Endpoint | Description | Header |
|--------|----------|-------------|--------|
| POST | `/api/v1/external/reports` | Upload PDF report (optional `report_id` to replace existing) | `X-API-KEY` |
| GET | `/api/v1/external/reports/{id}` | Check report status | `X-API-KEY` |
| GET | `/api/v1/external/reports/{id}/download` | Download watermarked PDF | `X-API-KEY` |

### Example: External Upload (New Report)

```bash
curl -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: your-api-key-here" \
  -H "X-Signature: sha256=computed_hmac_signature" \
  -F "file=@report.pdf" \
  -F "benchmarkTag=BM-2024-Q1"
```

### Example: External Upload (Replace Existing Report)

If `report_id` is provided and exists in the database, the original file is deleted and replaced with the new upload. The report status is reset to `PENDING_WATERMARK` and re-processed.

```bash
curl -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: your-api-key-here" \
  -H "X-Signature: sha256=computed_hmac_signature" \
  -F "file=@updated_report.pdf" \
  -F "report_id=existing-report-uuid-here" \
  -F "benchmarkTag=BM-2024-Q1"
```

### Example: Admin Login

```bash
curl -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin123"}'
```

Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "SUPER_ADMIN"
}
```

---

## Frontend Application

### Pages

| Route | Component | Access | Description |
|-------|-----------|--------|-------------|
| `/login` | LoginView | Public | Authentication form |
| `/` | DashboardView | Authenticated | Overview dashboard |
| `/reports` | ReportsView | Authenticated | Report management (upload, list, download) |
| `/clients` | ClientsView | SUPER_ADMIN | Third-party client management |
| `/audit-logs` | AuditLogsView | AUDITOR+ | Audit trail browser |
| `/error-logs` | ErrorLogsView | SUPER_ADMIN | System error log viewer with timestamp filter |

### State Management (Pinia Stores)

| Store | Responsibility |
|-------|----------------|
| `authStore` | User session, tokens, login/logout |
| `reportStore` | Report list, pagination, upload/download |
| `clientStore` | Client list, create, status update |

### Route Guards

- Unauthenticated users are redirected to `/login`
- `/clients` route requires `SUPER_ADMIN` role
- `/error-logs` route requires `SUPER_ADMIN` role
- `/audit-logs` route requires `AUDITOR` or `SUPER_ADMIN` role
- API 401 responses auto-clear tokens and redirect to login

---

## Configuration

### Spring Profiles

| Profile | Database | Use Case |
|---------|----------|----------|
| `dev` | H2 (in-memory) | Local development |
| `sit` | H2 (in-memory) | Docker SIT environment |
| `prod` | IBM DB2 | Production deployment |

### Key Environment Variables

| Variable | Default (SIT) | Description |
|----------|---------------|-------------|
| `SPRING_PROFILES_ACTIVE` | `sit` | Active Spring profile |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:mem:reportdb` | Database connection |
| `SPRING_RABBITMQ_HOST` | `rabbitmq` | RabbitMQ hostname |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `MINIO_ENDPOINT` | `http://minio:9000` | MinIO server URL |
| `MINIO_ACCESS_KEY` | `minioadmin` | MinIO access key |
| `MINIO_SECRET_KEY` | `minioadmin` | MinIO secret key |
| `JWT_SECRET` | (in config) | JWT signing key |
| `JWT_EXPIRATION_MS` | `3600000` | Token expiry (1 hour) |

### MinIO Buckets (Auto-Created)

| Bucket | Purpose |
|--------|---------|
| `staging` | Raw uploaded PDFs (pre-watermark) |
| `reports-watermarked` | Processed watermarked PDFs |

---

## Deployment

### Quick Start (Docker Compose)

```bash
# Clone the repository
git clone https://github.com/jakwong1981/statement_shaocm_bank_doc_srv.git
cd statement_shaocm_bank_doc_srv

# Build and start all containers
docker-compose up -d --build

# Check container status
docker-compose ps
```

Access the application:
- Frontend: http://localhost:8880
- Backend API: http://localhost:8080
- MinIO Console: http://localhost:9001 (minioadmin / minioadmin)
- RabbitMQ Management: http://localhost:15672 (guest / guest)

### Local Development

```bash
# 1. Start infrastructure only
docker-compose up -d minio rabbitmq

# 2. Start backend (requires Java 17+ and Maven)
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Start frontend (requires Node.js 18+)
cd frontend
npm install
npm run dev
```

Frontend dev server: http://localhost:5173 (with API proxy to backend)

### Stopping

```bash
# Stop all containers (preserves data)
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed production deployment guide.

---

## Default Credentials

### SIT / Development Users

| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| admin | admin123 | SUPER_ADMIN | Full access (reports, clients, audit logs, error logs) |
| operator | admin123 | OPERATOR | Report upload, list, download |
| auditor | admin123 | AUDITOR | Report list, download, audit log access |

### Infrastructure

| Service | Username | Password |
|---------|----------|----------|
| MinIO | minioadmin | minioadmin |
| RabbitMQ | guest | guest |

---

## License

Internal / proprietary. All rights reserved.
