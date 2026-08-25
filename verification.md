# Verification Guide

Step-by-step testing procedures for the SIT Docker environment.

---

## Table of Contents

- [1. Verify Containers Are Running](#1-verify-containers-are-running)
- [2. Frontend (Browser)](#2-frontend-browser)
- [3. Admin API (curl)](#3-admin-api-curl)
- [4. External API (API Key)](#4-external-api-api-key)
- [5. Infrastructure Consoles](#5-infrastructure-consoles)
- [6. Quick Health Check](#6-quick-health-check)
- [7. Full End-to-End Test Flow](#7-full-end-to-end-test-flow)
- [8. System Error Logs](#8-system-error-logs)

---

## 1. Verify Containers Are Running

```bash
docker-compose ps
```

You should see 5 containers with status `Up`:
- `sit-minio` — Object storage
- `sit-minio-init` — Exited after bucket creation (expected)
- `sit-rabbitmq` — Message broker
- `sit-backend` — Spring Boot API
- `sit-frontend` — Vue.js admin portal

Check backend logs to confirm Spring Boot started successfully:

```bash
docker-compose logs sit-backend | tail -20
```

Look for: `Started ReportCentreApplication in X seconds`

---

## 2. Frontend (Browser)

| URL | What to Test |
|-----|-------------|
| http://localhost:8880 | Login page should appear |
| Login with `admin` / `admin123` | Should redirect to Dashboard |
| Navigate to **Reports** | Should show empty report list |
| Navigate to **Clients** | Should show third-party clients (SUPER_ADMIN only) |
| Navigate to **Audit Logs** | Should show audit trail |
| Navigate to **Error Logs** (SUPER_ADMIN) | Should show error log viewer with timestamp filter |

---

## 3. Admin API (curl)

### 3.1 Login and Get JWT Token

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -m json.tool
```

Expected response:

```json
{
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "username": "admin",
    "role": "SUPER_ADMIN"
}
```

Save the `accessToken` value for subsequent requests.

### 3.2 List Reports (Empty Initially)

```bash
curl -s http://localhost:8080/api/v1/admin/reports \
  -H "Authorization: Bearer <your-access-token>" | python3 -m json.tool
```

### 3.3 Upload a Test PDF

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/reports \
  -H "Authorization: Bearer <your-access-token>" \
  -F "file=@test.pdf" \
  -F "benchmarkTag=BM-TEST-001" | python3 -m json.tool
```

The response should show `status: PENDING_WATERMARK`. Wait ~5 seconds, then check the report again — status should change to `READY`.

### 3.4 Get Report Details

```bash
curl -s http://localhost:8080/api/v1/admin/reports/<report-id> \
  -H "Authorization: Bearer <your-access-token>" | python3 -m json.tool
```

Verify:
- `status` is `READY`
- `checksumSha256` is populated
- `pageCount` is greater than 0
- `watermarkedStoragePath` is set

### 3.5 Download Watermarked PDF

```bash
curl -o watermarked.pdf -X GET http://localhost:8080/api/v1/admin/reports/<report-id>/download \
  -H "Authorization: Bearer <your-access-token>"
```

Open `watermarked.pdf` and verify watermark zones are visible (header, diagonal text, footer, QR code).

### 3.6 Check Audit Logs

```bash
curl -s http://localhost:8080/api/v1/admin/audit-logs \
  -H "Authorization: Bearer <your-access-token>" | python3 -m json.tool
```

Should contain entries for: `LOGIN`, `UPLOAD`, `DOWNLOAD` actions.

---

## 4. External API (API Key)

### 4.1 Create a Third-Party Client

```bash
curl -s -X POST http://localhost:8080/api/v1/admin/clients \
  -H "Authorization: Bearer <your-access-token>" \
  -H "Content-Type: application/json" \
  -d '{"clientName":"Test Corp","contactEmail":"test@example.com"}' | python3 -m json.tool
```

Expected response:

```json
{
    "id": "uuid-here",
    "clientName": "Test Corp",
    "apiKey": "generated-api-key-value",
    "status": "ACTIVE"
}
```

**Important:** Copy the `apiKey` — it is only shown once.

### 4.2 Upload PDF via External API

```bash
curl -s -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: <your-api-key>" \
  -F "file=@test.pdf" \
  -F "benchmarkTag=BM-EXT-001" | python3 -m json.tool
```

### 4.3 Replace Existing Report via External API

If `report_id` is provided and exists in the database, the original file is deleted from MinIO and replaced with the new upload. The report status is reset to `PENDING_WATERMARK` and re-processed.

```bash
# Save the report_id from the previous upload response
REPORT_ID="<report-id-from-step-4.2>"

curl -s -X POST http://localhost:8080/api/v1/external/reports \
  -H "X-API-KEY: <your-api-key>" \
  -F "file=@test.pdf" \
  -F "report_id=$REPORT_ID" \
  -F "benchmarkTag=BM-EXT-001-UPDATED" | python3 -m json.tool
```

Expected: Same `report_id` returned, status reset to `PENDING_WATERMARK`, `watermarkedAt` is null.

### 4.4 Check Report Status

```bash
curl -s http://localhost:8080/api/v1/external/reports/<report-id> \
  -H "X-API-KEY: <your-api-key>" | python3 -m json.tool
```

### 4.5 Download Watermarked PDF

```bash
curl -o ext_watermarked.pdf -X GET http://localhost:8080/api/v1/external/reports/<report-id>/download \
  -H "X-API-KEY: <your-api-key>"
```

---

## 5. Infrastructure Consoles

### 5.1 MinIO Console

| Property | Value |
|----------|-------|
| URL | http://localhost:9001 |
| Username | `minioadmin` |
| Password | `minioadmin` |

**What to check:**
- Browse buckets: `staging-raw` (original PDFs), `reports-watermarked` (processed PDFs), `report-archive`
- After uploading a report, verify files appear in both buckets

### 5.2 RabbitMQ Management

| Property | Value |
|----------|-------|
| URL | http://localhost:15672 |
| Username | `guest` |
| Password | `guest` |

**What to check:**
- **Queues** tab → watermark processing queue should be empty (messages consumed)
- **Exchanges** tab → report exchange should be listed
- **Connections** tab → backend connection should be active

### 5.3 H2 Database Console

| Property | Value |
|----------|-------|
| URL | http://localhost:8080/h2-console |
| JDBC URL | `jdbc:h2:mem:reportdb` |
| Username | `sa` |
| Password | *(leave empty)* |

**Useful queries:**

```sql
-- Check all reports
SELECT id, file_name, status, page_count, created_at FROM reports;

-- Check audit trail
SELECT actor_type, actor_id, action, target_report_id, ip_address, created_at FROM audit_logs ORDER BY created_at DESC;

-- Check users
SELECT username, role, is_active FROM users;

-- Check third-party clients
SELECT id, client_name, api_key, status FROM third_party_clients;
-- Check system error logs
SELECT id, http_status, http_method, request_uri, exception_class, error_message, created_at FROM system_error_logs ORDER BY created_at DESC;
```

---

## 6. Quick Health Check

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Expected response:

```json
{
    "status": "UP"
}
```

---

## 7. Full End-to-End Test Flow

Execute these steps in order to validate the complete system:

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | `docker-compose ps` | All 5 containers running |
| 2 | `curl http://localhost:8080/actuator/health` | `{"status":"UP"}` |
| 3 | Open http://localhost:8880 in browser | Login page displayed |
| 4 | Login with `admin` / `admin123` | Redirected to Dashboard |
| 5 | Login API: `POST /api/v1/admin/auth/login` | Receive JWT access token |
| 6 | Upload PDF: `POST /api/v1/admin/reports` | Status = `PENDING_WATERMARK` |
| 7 | Wait ~5 seconds | — |
| 8 | Check report: `GET /api/v1/admin/reports/{id}` | Status = `READY`, checksum populated |
| 9 | Download: `GET /api/v1/admin/reports/{id}/download` | Watermarked PDF downloaded |
| 10 | Open downloaded PDF | Watermark zones visible (header, diagonal, footer, QR) |
| 11 | Check audit logs: `GET /api/v1/admin/audit-logs` | LOGIN + UPLOAD + DOWNLOAD entries |
| 12 | Check error logs: `GET /api/v1/admin/error-logs` | Error entries captured (if any errors occurred) |
| 13 | Check MinIO console | Files in `staging-raw` and `reports-watermarked` |
| 14 | Check H2 console: `SELECT * FROM REPORTS` | Report row with READY status |
| 15 | Create client: `POST /api/v1/admin/clients` | API key returned |
| 16 | External upload: `POST /api/v1/external/reports` with API key | Report ingested and watermarked |
| 17 | External replace: `POST /api/v1/external/reports` with `report_id` | Same reportId returned, status reset to PENDING_WATERMARK |
| 18 | `docker-compose down` | All containers stopped |

---

## Troubleshooting

| Issue | Check |
|-------|-------|
| Backend not starting | `docker-compose logs sit-backend` — look for startup errors |
| Login fails (401) | Verify seed data loaded: check H2 `USERS` table has `admin` user |
| Upload fails (500) | Check MinIO is running: `docker-compose logs sit-minio` |
| Watermark never completes | Check RabbitMQ: `docker-compose logs sit-rabbitmq`, verify queue is being consumed |
| Frontend shows blank page | Check browser console for API errors, verify backend is accessible at port 8080 |
| H2 console won't connect | Ensure JDBC URL is exactly `jdbc:h2:mem:reportdb` with user `sa` and empty password |
| Port already in use | Check with `lsof -i :8080` or `lsof -i :8880` and stop conflicting processes |
| CORS 403 from browser | Ensure `SecurityConfig` has `.cors(Customizer.withDefaults())` and `WebConfig` allows `http://localhost:8880` |

---

## 8. System Error Logs

The system automatically captures all exceptions thrown by Spring controllers into the `SYSTEM_ERROR_LOGS` table.

### 8.1 Trigger a Test Error

```bash
# This should return 400 "Invalid credentials" and log the error
curl -s -X POST http://localhost:8080/api/v1/admin/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong_password"}'
```

### 8.2 Query Error Logs via API

```bash
curl -s http://localhost:8080/api/v1/admin/error-logs \
  -H "Authorization: Bearer <your-access-token>" | python3 -m json.tool
```

Expected: paginated response with error entries containing `errorMessage`, `exceptionClass`, `stackTrace`, `httpMethod`, `requestUri`, `httpStatus`, and `createdAt`.

### 8.3 Filter by Timestamp

```bash
curl -s "http://localhost:8080/api/v1/admin/error-logs?from=2026-08-25T00:00:00Z&to=2026-08-26T00:00:00Z" \
  -H "Authorization: Bearer <your-access-token>" | python3 -m json.tool
```

### 8.4 Verify in H2 Console

```sql
SELECT id, http_status, http_method, request_uri, exception_class, error_message, created_at
FROM system_error_logs
ORDER BY created_at DESC;
```

### 8.5 Verify in Frontend (SUPER_ADMIN)

1. Login as `admin` at http://localhost:8880
2. Navigate to **Error Logs** in the sidebar
3. Use the **From** / **To** datetime pickers to filter by time range
4. Click **Search** to apply the filter
5. Click **Details** on any entry to view the full stack trace
