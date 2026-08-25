# Simple Report Centre - Deployment Guide

## Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [Local Development (Dev Profile)](#2-local-development-dev-profile)
3. [Production Deployment (Prod Profile)](#3-production-deployment-prod-profile)
4. [Docker Compose Full Stack](#4-docker-compose-full-stack)
5. [API Reference](#5-api-reference)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Prerequisites

### Required Software
| Tool | Version | Purpose |
|---|---|---|
| Java JDK | 17+ (LTS) | Backend runtime |
| Gradle | 8.x | Build tool |
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

## 2. Local Development (Dev Profile)

The **dev** profile uses:
- **H2 in-memory database** (DB2 compatibility mode)
- **Local MinIO** (S3-compatible) via Docker
- **Local RabbitMQ** via Docker

### Step 1: Start Infrastructure Containers

```bash
cd c:\report-centre
docker-compose up -d
```

Wait for healthy status:
```bash
docker-compose ps
```

Verify:
- MinIO console: http://localhost:9001 (minioadmin / minioadmin)
- RabbitMQ console: http://localhost:15672 (guest / guest)

### Step 2: Start Backend

```bash
cd c:\report-centre\backend

# Option A: Using Gradle directly
gradle bootRun

# Option B: Build JAR then run
gradle build
java -jar build/libs/report-centre-1.0.0.jar
```

Backend starts at: **http://localhost:8080**

H2 Console available at: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:reportdb`
- Username: `sa` (no password)

### Step 3: Start Frontend

```bash
cd c:\report-centre\frontend

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

## 3. Production Deployment (Prod Profile)

### Step 1: Set Environment Variables

```bash
# Database
set DB2_USERNAME=db2inst1
set DB2_PASSWORD=your_secure_password

# RabbitMQ
set RABBITMQ_HOST=rabbitmq.yourcompany.com
set RABBITMQ_PORT=5672
set RABBITMQ_USERNAME=app_user
set RABBITMQ_PASSWORD=rabbit_secure_password

# MinIO / S3
set MINIO_ENDPOINT=https://s3.yourcompany.com
set MINIO_ACCESS_KEY=your_access_key
set MINIO_SECRET_KEY=your_secret_key
```

### Step 2: Run Database Migration on DB2

```bash
db2 connect to REPORTDB user db2inst1
db2 -tvf c:\report-centre\backend\src\main\resources\db\migration\V1__init_schema.sql
db2 -tvf c:\report-centre\backend\src\main\resources\data.sql
```

**IMPORTANT:** Change default admin passwords after first login.

### Step 3: Build and Deploy Backend

```bash
cd c:\report-centre\backend
gradle build -x test
java -jar build/libs/report-centre-1.0.0.jar --spring.profiles.active=prod
```

### Step 4: Build and Deploy Frontend

```bash
cd c:\report-centre\frontend
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

## 4. Docker Compose Full Stack Deployment

### Add backend Dockerfile (`backend/Dockerfile`)

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/report-centre-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Add frontend Dockerfile (`frontend/Dockerfile`)

```dockerfile
FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### Frontend NGINX config (`frontend/nginx.conf`)

```nginx
server {
    listen 80;
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://backend:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### Add services to docker-compose.yml

```yaml
  backend:
    build: ./backend
    container_name: report-centre-backend
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: dev
    depends_on:
      - minio
      - rabbitmq
    networks:
      - report-net

  frontend:
    build: ./frontend
    container_name: report-centre-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - report-net
```

### Deploy full stack:

```bash
cd c:\report-centre
docker-compose up -d --build
```

Access the application at: **http://localhost**

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

### External Endpoints (API Key + HMAC required)

| Endpoint | Method | Headers | Description |
|---|---|---|---|
| `/api/v1/external/reports` | POST | X-API-KEY, X-SIGNATURE | Upload PDF for watermarking |
| `/api/v1/external/reports/{id}` | GET | X-API-KEY | Query report status |
| `/api/v1/external/reports/{id}/download` | GET | X-API-KEY | Get presigned download URL |

### Example: External Upload (curl)

```bash
curl -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: ak_live_sample_key_8f9021a8d0119e7a" \
  -H "X-SIGNATURE: <hmac-sha256-hash>" \
  -F "file=@financial_report.pdf" \
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

### Backend Issues

| Problem | Solution |
|---|---|
| "Cannot connect to RabbitMQ" | Ensure `docker-compose up -d` is running and healthcheck passes |
| "Cannot connect to MinIO" | Check http://localhost:9000 is accessible; verify credentials in application.yml |
| "Port 8080 already in use" | Use `--server.port=8081` flag or stop the conflicting process |
| "Bean creation exception" | Ensure MinIO buckets exist (minio-init container ran successfully) |
| "H2 console 404" | Ensure `dev` profile is active (check startup logs for profile) |

### Frontend Issues

| Problem | Solution |
|---|---|
| "npm not recognized" | Install Node.js 18+ from https://nodejs.org; restart terminal |
| "Module not found" | Delete `node_modules`, run `npm install` again |
| "CORS error in browser" | Ensure backend is running on port 8080 and WebConfig CORS is set |
| "401 on every request" | Check browser localStorage has `accessToken`; re-login if expired |

### Watermark Issues

| Problem | Solution |
|---|---|
| Report stuck in PENDING_WATERMARK | Check RabbitMQ queue for unprocessed messages; verify consumer logs |
| Watermark fails with error | Check backend logs for `WatermarkEngine` errors; PDF may be encrypted |
| QR code missing | Check ZXing dependency is included in build.gradle |

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
