#!/usr/bin/env bash
# ============================================================================
#  startapp.sh — Start the Report Centre SIT environment
#
#  Usage:
#    ./startapp.sh            Build and start all services
#    ./startapp.sh --no-build Start without rebuilding (faster if no code changes)
#    ./startapp.sh --smoke    Build, start, and run smoke tests
#    ./startapp.sh --stop     Stop all services
#    ./startapp.sh --status   Show container status
#    ./startapp.sh --help     Show this help
# ============================================================================
set -euo pipefail

# ── Colours ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No colour

# ── Defaults ─────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_URL="http://localhost:8080"
FRONTEND_URL="http://localhost:8880"
MAX_WAIT=60          # seconds to wait for backend
SMOKE_TEST=false
NO_BUILD=false
ACTION="start"       # start | stop | status

# ── Helpers ──────────────────────────────────────────────────────────────────
info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()      { echo -e "${GREEN}[ OK ]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
fail()    { echo -e "${RED}[FAIL]${NC}  $*"; }
divider() { echo -e "${BOLD}────────────────────────────────────────────────────────────${NC}"; }

usage() {
  echo "Usage: $0 [--no-build] [--smoke] [--stop] [--status] [--help]"
  echo ""
  echo "Options:"
  echo "  (default)     Build images and start all containers"
  echo "  --no-build    Start without rebuilding Docker images"
  echo "  --smoke       Run smoke tests after startup"
  echo "  --stop        Stop all containers"
  echo "  --status      Show container status and exit"
  echo "  --help        Show this help message"
  exit 0
}

# ── Parse args ───────────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --no-build)  NO_BUILD=true ;;
    --smoke)     SMOKE_TEST=true ;;
    --stop)      ACTION="stop" ;;
    --status)    ACTION="status" ;;
    --help|-h)   usage ;;
    *)           warn "Unknown option: $arg"; usage ;;
  esac
done

cd "$SCRIPT_DIR"

# ── Pre-flight checks ───────────────────────────────────────────────────────
preflight() {
  local missing=0

  if ! command -v docker &>/dev/null; then
    fail "docker is not installed or not in PATH"
    missing=1
  fi

  # docker-compose v1 or v2
  if docker compose version &>/dev/null; then
    DC="docker compose"
  elif command -v docker-compose &>/dev/null; then
    DC="docker-compose"
  else
    fail "docker-compose is not installed"
    missing=1
  fi

  if [ "$missing" -eq 1 ]; then
    fail "Prerequisites not met. Exiting."
    exit 1
  fi

  ok "Docker ($DC) found"
}

# ── Stop ─────────────────────────────────────────────────────────────────────
do_stop() {
  info "Stopping all containers..."
  $DC down
  ok "All containers stopped"
  exit 0
}

# ── Status ───────────────────────────────────────────────────────────────────
do_status() {
  echo ""
  $DC ps
  echo ""
  info "Backend health:  $(curl -s -o /dev/null -w '%{http_code}' "$BACKEND_URL/actuator/health" 2>/dev/null || echo 'unreachable')"
  info "Frontend:        $(curl -s -o /dev/null -w '%{http_code}' "$FRONTEND_URL" 2>/dev/null || echo 'unreachable')"
  exit 0
}

# ── Wait for backend health ─────────────────────────────────────────────────
wait_for_backend() {
  info "Waiting for backend to become healthy (max ${MAX_WAIT}s)..."
  local elapsed=0
  while [ "$elapsed" -lt "$MAX_WAIT" ]; do
    local status
    status=$(curl -s -o /dev/null -w '%{http_code}' "$BACKEND_URL/actuator/health" 2>/dev/null || echo "000")
    if [ "$status" = "200" ]; then
      ok "Backend is healthy (${elapsed}s)"
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
    printf "\r  elapsed: %ds " "$elapsed"
  done
  echo ""
  fail "Backend did not become healthy within ${MAX_WAIT}s"
  fail "Check logs: $DC logs backend"
  return 1
}

# ── Wait for minio-init to finish ───────────────────────────────────────────
wait_for_minio_init() {
  info "Waiting for MinIO bucket initialisation..."
  local elapsed=0
  while [ "$elapsed" -lt 30 ]; do
    local state
    state=$(docker inspect --format='{{.State.Status}}' sit-minio-init 2>/dev/null || echo "unknown")
    if [ "$state" = "exited" ]; then
      local exit_code
      exit_code=$(docker inspect --format='{{.State.ExitCode}}' sit-minio-init 2>/dev/null || echo "-1")
      if [ "$exit_code" = "0" ]; then
        ok "MinIO buckets initialised"
        return 0
      else
        warn "minio-init exited with code $exit_code (buckets may already exist)"
        return 0
      fi
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  warn "minio-init did not finish in time (continuing anyway)"
}

# ── Smoke test ───────────────────────────────────────────────────────────────
run_smoke_test() {
  divider
  echo -e "${BOLD}  SMOKE TEST${NC}"
  divider

  local all_pass=true

  # 1) Login
  info "Test 1: Login API"
  local login_resp
  login_resp=$(curl -s -X POST "$BACKEND_URL/api/v1/admin/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')

  local token
  token=$(echo "$login_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])" 2>/dev/null || echo "")
  if [ -n "$token" ]; then
    ok "Login successful — JWT received"
  else
    fail "Login failed"
    all_pass=false
  fi

  # 2) Create test PDF
  local test_pdf="/tmp/startapp_test_$$.pdf"
  cat > "$test_pdf" << 'PDFEOF'
%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj
xref
0 4
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
0000000115 00000 n 
trailer<</Size 4/Root 1 0 R>>
startxref
190
%%EOF
PDFEOF

  # 3) Upload PDF
  info "Test 2: Upload PDF via Admin API"
  local upload_resp
  upload_resp=$(curl -s -X POST "$BACKEND_URL/api/v1/admin/reports" \
    -H "Authorization: Bearer $token" \
    -F "file=@$test_pdf" \
    -F "benchmarkTag=SMOKE-TEST")

  local report_id upload_status
  report_id=$(echo "$upload_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['reportId'])" 2>/dev/null || echo "")
  upload_status=$(echo "$upload_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])" 2>/dev/null || echo "")

  if [ "$upload_status" = "PENDING_WATERMARK" ] && [ -n "$report_id" ]; then
    ok "Upload successful — reportId: $report_id, status: $upload_status"
  else
    fail "Upload failed: $upload_resp"
    all_pass=false
  fi

  # 4) Wait for watermark
  if [ -n "$report_id" ]; then
    info "Test 3: Wait for watermark processing..."
    sleep 8

    local check_resp check_status
    check_resp=$(curl -s "$BACKEND_URL/api/v1/admin/reports/$report_id" \
      -H "Authorization: Bearer $token")
    check_status=$(echo "$check_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])" 2>/dev/null || echo "UNKNOWN")

    if [ "$check_status" = "READY" ]; then
      local checksum
      checksum=$(echo "$check_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data'].get('checksumSha256','')[:16])" 2>/dev/null || echo "")
      ok "Watermark complete — status: $check_status, checksum: ${checksum}..."
    else
      fail "Watermark incomplete — status: $check_status"
      all_pass=false
    fi

    # 5) Download
    info "Test 4: Download watermarked PDF"
    local dl_code
    dl_code=$(curl -s -o /dev/null -w '%{http_code}' \
      -X GET "$BACKEND_URL/api/v1/admin/reports/$report_id/download" \
      -H "Authorization: Bearer $token")

    if [ "$dl_code" = "200" ]; then
      ok "Download successful (HTTP $dl_code)"
    else
      fail "Download failed (HTTP $dl_code)"
      all_pass=false
    fi
  fi

  # 6) Create external client + upload
  info "Test 5: External API upload"
  local client_resp api_key
  client_resp=$(curl -s -X POST "$BACKEND_URL/api/v1/admin/clients" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    -d '{"clientName":"Smoke Test Client","contactEmail":"smoke@test.com"}')
  api_key=$(echo "$client_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['client']['apiKey'])" 2>/dev/null || echo "")

  if [ -n "$api_key" ]; then
    local ext_resp ext_status
    ext_resp=$(curl -s -X POST "$BACKEND_URL/api/v1/external/reports" \
      -H "X-API-KEY: $api_key" \
      -F "file=@$test_pdf" \
      -F "benchmark_tag=SMOKE-EXT")
    ext_status=$(echo "$ext_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['status'])" 2>/dev/null || echo "")

    if [ "$ext_status" = "PENDING_WATERMARK" ]; then
      ok "External upload successful — status: $ext_status"
    else
      fail "External upload failed: $ext_resp"
      all_pass=false
    fi
  else
    fail "Client creation failed"
    all_pass=false
  fi

  # 7) Error logs
  info "Test 6: Error logs API"
  # Trigger a deliberate error
  curl -s -X POST "$BACKEND_URL/api/v1/admin/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}' > /dev/null

  local err_resp err_count
  err_resp=$(curl -s "$BACKEND_URL/api/v1/admin/error-logs" \
    -H "Authorization: Bearer $token")
  err_count=$(echo "$err_resp" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['totalElements'])" 2>/dev/null || echo "0")

  if [ "$err_count" -gt 0 ] 2>/dev/null; then
    ok "Error logs captured ($err_count entries)"
  else
    warn "Error logs API works but no entries found (non-critical)"
  fi

  # Cleanup
  rm -f "$test_pdf"

  divider
  if $all_pass; then
    echo -e "  ${GREEN}${BOLD}ALL SMOKE TESTS PASSED${NC}"
  else
    echo -e "  ${RED}${BOLD}SOME SMOKE TESTS FAILED${NC}"
  fi
  divider
}

# ── Print summary ────────────────────────────────────────────────────────────
print_summary() {
  echo ""
  divider
  echo -e "${BOLD}  REPORT CENTRE — SIT Environment${NC}"
  divider
  echo ""
  echo -e "  ${CYAN}Frontend (Admin Portal):${NC}  $FRONTEND_URL"
  echo -e "  ${CYAN}Backend API:${NC}              $BACKEND_URL"
  echo -e "  ${CYAN}Backend Health:${NC}           $BACKEND_URL/actuator/health"
  echo -e "  ${CYAN}H2 Console:${NC}               $BACKEND_URL/h2-console"
  echo -e "  ${CYAN}MinIO Console:${NC}            http://localhost:9001  ${YELLOW}(minioadmin / minioadmin)${NC}"
  echo -e "  ${CYAN}RabbitMQ Console:${NC}         http://localhost:15672 ${YELLOW}(guest / guest)${NC}"
  echo ""
  echo -e "  ${BOLD}Default Users:${NC}"
  echo -e "    admin / admin123       (SUPER_ADMIN)"
  echo -e "    operator / admin123    (OPERATOR)"
  echo -e "    auditor / admin123     (AUDITOR)"
  echo ""
  divider
  echo -e "  ${BOLD}Stop:${NC}    $0 --stop"
  echo -e "  ${BOLD}Status:${NC}  $0 --status"
  echo -e "  ${BOLD}Logs:${NC}    docker-compose logs -f"
  divider
  echo ""
}

# ══════════════════════════════════════════════════════════════════════════════
#  MAIN
# ══════════════════════════════════════════════════════════════════════════════

echo ""
divider
echo -e "${BOLD}  Report Centre — Starting SIT Environment${NC}"
divider
echo ""

# Handle sub-commands
[ "$ACTION" = "stop" ]   && { preflight; do_stop; }
[ "$ACTION" = "status" ] && { preflight; do_status; }

preflight

# Build or just start
if $NO_BUILD; then
  info "Starting containers (no rebuild)..."
  $DC up -d
else
  info "Building and starting containers..."
  $DC up -d --build
fi

echo ""

# Wait for services
wait_for_minio_init
wait_for_backend

echo ""

# Print connection info
print_summary

# Run smoke tests if requested
if $SMOKE_TEST; then
  run_smoke_test
  echo ""
fi

ok "Application is ready!"
