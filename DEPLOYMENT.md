# Simple Report Centre - Deployment Guide

## Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [SIT Docker Compose Full Stack Deployment](#2-sit-docker-compose-full-stack-deployment)
3. [Local Development (Dev Profile)](#3-local-development-dev-profile)
4. [Production Deployment (Prod Profile)](#4-production-deployment-prod-profile)
5. [API Reference](#5-api-reference)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Prerequisites

### Required Software
| Tool | Version | Purpose |
|---|---|---|
| Java JDK | 17+ (LTS) | Backend runtime |
| Maven | 3.9+ | Build tool |
| Node.js | 18+ LTS | Frontend build |
| npm | 9+ | Frontend packages |
| Docker & Docker Compose | 24+ / v2 | Container runtime |

### Optional (Production Only)
| Tool | Version | Purpose |
|---|---|---|
| IBM DB2 Server | 11.5+ (LUW) | Enterprise database |
| RabbitMQ | 3.12+ | Message broker |
| MinIO / AWS S3 | Latest | Object storage |

---

## 2. SIT Docker Compose Full Stack Deployment

This is the recommended approach for **SIT (System Integration Testing)**. The entire stack is containerised and orchestrated via Docker Compose. The backend is built with **Maven** inside Docker (multi-stage build).

### Architecture Overview

| Service | Container Name | Image | Port | Profile |
|---|---|---|---|---|
| MinIO (S3) | `sit-minio` | `minio/minio:latest` | 9000 (API), 9001 (Console) | — |
| MinIO Init | `sit-minio-init` | `minio/mc:latest` | — (init job) | — |
| RabbitMQ | `sit-rabbitmq` | `rabbitmq:3.12-management-alpine` | 5672 (AMQP), 15672 (Mgmt UI) | — |
| Backend | `sit-backend` | Built from `backend/Dockerfile` | 8080 | `sit` |
| Frontend | `sit-frontend` | Built from `frontend/Dockerfile` | 8880 → 80 (NGINX) | — |

All containers are connected via the `sit-net` bridge network.

### Spring Profile: `sit`

The SIT environment uses the **`sit`** Spring profile, which provides:
- **H2 in-memory database** (DB2 compatibility mode) — no external DB required
- **Docker-friendly service hostnames** (`rabbitmq`, `minio`) as defaults
- **H2 console enabled** for debugging
- **Hibernate DDL auto-update** for schema management
- **Seed data** loaded via `data.sql`

### Backend Dockerfile (`backend/Dockerfile`)

The backend uses a **multi-stage Maven build**:

```dockerfile
# ====== Stage 1: Build with Maven ======
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ====== Stage 2: Runtime ======
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /build/target/report-centre-1.0.0.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Key points:
- Stage 1 uses `maven:3.9-eclipse-temurin-17` to compile and package the application
- Dependencies are cached via `mvn dependency:go-offline` before copying source code
- Stage 2 uses a minimal JRE Alpine image with a non-root `appuser`
- Health check uses Spring Boot Actuator `/actuator/health` endpoint

### Frontend Dockerfile (`frontend/Dockerfile`)

```dockerfile
# ====== Stage 1: Build ======
FROM node:18-alpine AS builder
WORKDIR /build
COPY package*.json ./
RUN npm install
COPY . .
RUN npm run build

# ====== Stage 2: Serve with NGINX ======
FROM nginx:1.25-alpine
COPY --from=builder /build/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
```

### NGINX Config (`frontend/nginx.conf`)

```nginx
server {
    listen 80;
    server_name _;

    root /usr/share/nginx/html;
    index index.html;

    # Vue SPA routing
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API requests to backend
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 100m;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff2?)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

### Docker Compose (`docker-compose.yml`)

```yaml
services:

  minio:
    image: minio/minio:latest
    container_name: sit-minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data
    networks:
      - sit-net

  minio-init:
    image: minio/mc:latest
    container_name: sit-minio-init
    depends_on:
      - minio
    networks:
      - sit-net
    entrypoint: /bin/sh
    command: >
      -c "
      sleep 10 &&
      mc alias set sitminio http://minio:9000 minioadmin minioadmin &&
      mc mb --ignore-existing sitminio/report-staging-raw &&
      mc mb --ignore-existing sitminio/report-staging-watermarked &&
      mc mb --ignore-existing sitminio/report-archive &&
      echo Buckets created
      "

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    container_name: sit-rabbitmq
    environment:
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
    ports:
      - "5672:5672"
      - "15672:15672"
    networks:
      - sit-net

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: sit-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: sit
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: guest
      SPRING_RABBITMQ_PASSWORD: guest
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    depends_on:
      - minio
      - minio-init
      - rabbitmq
    networks:
      - sit-net

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: sit-frontend
    ports:
      - "8880:80"
    depends_on:
      - backend
    networks:
      - sit-net

volumes:
  minio-data:

networks:
  sit-net:
    driver: bridge
```

### Step-by-Step: Deploy SIT Stack

#### Step 1: Build and start all services

```bash
cd statement_shaocm_bank_doc_srv
docker compose up -d --build
```

This will:
1. Build the backend JAR using Maven inside a Docker container
2. Build the frontend using Node.js inside a Docker container
3. Start MinIO and create the required S3 buckets
4. Start RabbitMQ with management UI
5. Start the backend with the `sit` profile
6. Start the NGINX frontend

> **Note:** The first build may take several minutes as Maven downloads all dependencies.

#### Step 2: Verify all containers are running

```bash
docker compose ps
```

Expected output:

| Container | Status | Ports |
|---|---|---|
| `sit-backend` | Up (healthy) | 0.0.0.0:8080→8080 |
| `sit-frontend` | Up (healthy) | 0.0.0.0:8880→80 |
| `sit-minio` | Up | 0.0.0.0:9000-9001→9000-9001 |
| `sit-minio-init` | Exited (0) | — |
| `sit-rabbitmq` | Up | 0.0.0.0:5672→5672, 0.0.0.0:15672→15672 |

#### Step 3: Access the application

| Service | URL | Credentials |
|---|---|---|
| Frontend (App) | http://localhost:8880 | — |
| Backend API | http://localhost:8080 | — |
| Backend Health | http://localhost:8080/actuator/health | — |
| H2 Console | http://localhost:8080/h2-console | JDBC URL: `jdbc:h2:mem:reportdb`, User: `sa` |
| MinIO Console | http://localhost:9001 | `minioadmin` / `minioadmin` |
| RabbitMQ Mgmt | http://localhost:15672 | `guest` / `guest` |

#### Step 4: Login

Open http://localhost:8880/login

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | SUPER_ADMIN (full access) |
| `operator` | `admin123` | OPERATOR (reports access) |
| `auditor` | `admin123` | AUDITOR (audit logs access) |

### Managing the SIT Stack

```bash
# Stop all services (preserve volumes)
docker compose down

# Stop and remove all data (volumes)
docker compose down -v

# Rebuild only the backend
docker compose up -d --build backend

# Rebuild only the frontend
docker compose up -d --build frontend

# View backend logs
docker logs -f sit-backend

# View frontend logs
docker logs -f sit-frontend

# View all service logs
docker compose logs -f
```

---

## 3. Local Development (Dev Profile)

The **dev** profile uses:
- **H2 in-memory database** (DB2 compatibility mode)
- **Local MinIO** (S3-compatible) via Docker
- **Local RabbitMQ** via Docker

### Step 1: Start Infrastructure Containers

```bash
docker compose up -d minio rabbitmq
```

Wait for healthy status:
```bash
docker compose ps
```

Verify:
- MinIO console: http://localhost:9001 (minioadmin / minioadmin)
- RabbitMQ console: http://localhost:15672 (guest / guest)

### Step 2: Start Backend

```bash
cd backend

# Option A: Using Maven directly
mvn spring-boot:run

# Option B: Build JAR then run
mvn clean package -DskipTests
java -jar target/report-centre-1.0.0.jar
```

Backend starts at: **http://localhost:8080**

H2 Console available at: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:reportdb`
- Username: `sa` (no password)

### Step 3: Start Frontend

```bash
cd frontend

# Install dependencies (first time only)
npm install

# Start dev server
npm run dev
```

Frontend starts at: **http://localhost:5173**
- Proxies `/api` requests to `http://localhost:8080`

### Step 4: Login

Open http://localhost:5173/login

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | SUPER_ADMIN (full access) |
| `operator` | `admin123` | OPERATOR (reports access) |
| `auditor` | `admin123` | AUDITOR (audit logs access) |

---

## 4. Production Deployment (Prod Profile)

### Step 1: Set Environment Variables

```bash
# Database
export DB2_USERNAME=db2inst1
export DB2_PASSWORD=your_secure_password

# RabbitMQ
export RABBITMQ_HOST=rabbitmq.yourcompany.com
export RABBITMQ_PORT=5672
export RABBITMQ_USERNAME=app_user
export RABBITMQ_PASSWORD=rabbit_secure_password

# MinIO / S3
export MINIO_ENDPOINT=https://s3.yourcompany.com
export MINIO_ACCESS_KEY=your_access_key
export MINIO_SECRET_KEY=your_secret_key
```

### Step 2: Run Database Migration on DB2

```bash
db2 connect to REPORTDB user db2inst1
db2 -tvf backend/src/main/resources/db/migration/V1__init_schema.sql
db2 -tvf backend/src/main/resources/db/migration/V2__add_system_error_logs.sql
db2 -tvf backend/src/main/resources/data.sql
```

**IMPORTANT:** Change default admin passwords after first login.

### Step 3: Build and Deploy Backend

```bash
cd backend
mvn clean package -DskipTests
java -jar target/report-centre-1.0.0.jar --spring.profiles.active=prod
```

### Step 4: Build and Deploy Frontend

```bash
cd frontend
npm run build
# Deploy the dist/ folder to NGINX or static hosting
```

### Step 5: NGINX Reverse Proxy Configuration

```nginx
server {
    listen 443 ssl;
    server_name reportcentre.yourcompany.com;

    ssl_certificate     /etc/ssl/certs/reportcentre.crt;
    ssl_certificate_key /etc/ssl/private/reportcentre.key;

    # Frontend SPA
    location / {
        root /var/www/report-centre/dist;
        try_files $uri $uri/ /index.html;
    }

    # Backend API proxy
    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 110m;
    }

    # Block H2 console in production
    location /h2-console {
        deny all;
    }
}
```

---

## 5. API Reference

### Authentication

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/api/v1/admin/auth/login` | POST | Public | Admin login, returns JWT |
| `/api/v1/admin/auth/refresh` | POST | Public | Refresh expired token |

### Admin Endpoints (JWT Bearer required)

| Endpoint | Method | RBAC | Description |
|---|---|---|---|
| `/api/v1/admin/reports` | GET | OPERATOR, SUPER_ADMIN | Paginated report listing |
| `/api/v1/admin/reports` | POST | OPERATOR, SUPER_ADMIN | Upload PDF report |
| `/api/v1/admin/reports/{id}/download` | GET | OPERATOR, SUPER_ADMIN | Download watermarked PDF |
| `/api/v1/admin/clients` | GET | SUPER_ADMIN | List API clients |
| `/api/v1/admin/clients` | POST | SUPER_ADMIN | Create new client |
| `/api/v1/admin/clients/{id}/status` | PATCH | SUPER_ADMIN | Suspend/reactivate client |
| `/api/v1/admin/audit-logs` | GET | AUDITOR, SUPER_ADMIN | Query audit trail |
| `/api/v1/admin/error-logs` | GET | SUPER_ADMIN | Query system error logs (supports `from`/`to` timestamp filter) |

### External Endpoints (API Key + HMAC required)

| Endpoint | Method | Headers | Description |
|---|---|---|---|
| `/api/v1/external/reports` | POST | X-API-KEY, X-SIGNATURE | Upload PDF (optional `report_id` to replace existing) |
| `/api/v1/external/reports/{id}` | GET | X-API-KEY | Query report status |
| `/api/v1/external/reports/{id}/download` | GET | X-API-KEY | Get presigned download URL |

### Example: External Upload (curl)

```bash
# New report
curl -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: ak_live_sample_key_8f9021a8d0119e7a" \
  -H "X-SIGNATURE: <hmac-sha256-hash>" \
  -F "file=@financial_report.pdf" \
  -F "benchmark_tag=Q3-AUDIT-2026"

# Replace existing report (include report_id)
curl -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: ak_live_sample_key_8f9021a8d0119e7a" \
  -H "X-SIGNATURE: <hmac-sha256-hash>" \
  -F "file=@updated_report.pdf" \
  -F "report_id=existing-report-uuid-here" \
  -F "benchmark_tag=Q3-AUDIT-2026"
```

### Example: Admin Login (curl)

```bash
curl -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

## 6. Troubleshooting

### Docker Build Issues

| Problem | Solution |
|---|---|
| Frontend build fails with `npm ci` error | No `package-lock.json` present; Dockerfile uses `npm install` instead |
| Frontend build fails with `Unexpected token` in JSON | BOM (byte order mark) in source files; run `sed -i '' '1s/^\xEF\xBB\xBF//' <file>` to strip |
| `nginx: [emerg] unknown directive "server"` | BOM character in `nginx.conf`; strip BOM from the file |
| `sit-minio-init` fails with `sh is not a recognized command` | `minio/mc:latest` uses `mc` as entrypoint; set `entrypoint: /bin/sh` in docker-compose.yml |
| Port 80 already allocated | Change frontend port mapping from `80:80` to `8880:80` in docker-compose.yml |
| Container name already in use | Run `docker rm -f sit-minio sit-rabbitmq sit-backend sit-frontend` to remove orphaned containers |

### Backend Issues

| Problem | Solution |
|---|---|
| "Cannot connect to RabbitMQ" | Ensure all containers are running with `docker compose ps`; check RabbitMQ is ready before backend starts |
| "Cannot connect to MinIO" | Check http://localhost:9000 is accessible; verify credentials in application.yml |
| "Port 8080 already in use" | Use `--server.port=8081` flag or stop the conflicting process |
| "Bean creation exception" | Ensure MinIO buckets exist (minio-init container ran successfully) |
| "H2 console 404" | Ensure `sit` or `dev` profile is active (check startup logs for profile) |
| Backend health check failing | Actuator endpoint at `/actuator/health` requires `spring-boot-starter-actuator` dependency in pom.xml |

### Frontend Issues

| Problem | Solution |
|---|---|
| "npm not recognized" | Install Node.js 18+ from https://nodejs.org; restart terminal |
| "Module not found" | Delete `node_modules`, run `npm install` again |
| "CORS error in browser" | Ensure backend WebConfig includes your frontend origin (e.g. `http://localhost:8880`); SecurityConfig must have `.cors(Customizer.withDefaults())` enabled |
| "401 on every request" | Check browser localStorage has `accessToken`; re-login if expired |
| "403 on login from browser" | CORS issue — ensure `SecurityConfig` has `.cors(Customizer.withDefaults())` and `WebConfig` allows the frontend origin |

### Watermark Issues

| Problem | Solution |
|---|---|
| Report stuck in PENDING_WATERMARK | Check RabbitMQ queue for unprocessed messages; verify consumer logs |
| Watermark fails with error | Check backend logs for `WatermarkEngine` errors; PDF may be encrypted |
| QR code missing | Check ZXing dependency is included in pom.xml |

### Database Issues (Dev)

| Problem | Solution |
|---|---|
| Seed data not loaded | Ensure `spring.sql.init.mode: always` and `defer-datasource-initialization: true` in application.yml |
| Tables not created | H2 auto-creates via Hibernate `ddl-auto: update`; check entity annotations |

### Security Checklist for Production

- [ ] Change default admin password immediately
- [ ] Generate new JWT secret (base64-encoded, min 256 bits)
- [ ] Enable HTTPS/TLS termination at NGINX or load balancer
- [ ] Configure firewall rules for DB2, RabbitMQ, MinIO ports
- [ ] Set MinIO/S3 buckets to private (presigned URLs only)
- [ ] Enable DB2 SSL connections (sslConnection=true)
- [ ] Set up rate limiting on NGINX
- [ ] Disable H2 console (already disabled in prod profile)
- [ ] Configure log rotation and monitoring
- [ ] Set up database and storage backup strategy
