<template>
  <div class="layout">
    <aside class="sidebar">
      <h2>Report Centre</h2>
      <nav>
        <router-link to="/dashboard">Dashboard</router-link>
        <router-link to="/reports">Reports</router-link>
        <router-link to="/clients" v-if="auth.role === 'SUPER_ADMIN'">Clients</router-link>
        <router-link to="/audit-logs">Audit Logs</router-link>
        <router-link to="/error-logs" v-if="auth.role === 'SUPER_ADMIN'">Error Logs</router-link>
      </nav>
      <button class="logout-btn" @click="auth.logout(); $router.push('/login')">Logout</button>
    </aside>
    <main class="main-content">
      <div class="page-header">
        <h1>System Error Logs</h1>
      </div>

      <!-- Timestamp Filter -->
      <div class="card filter-bar">
        <div class="filter-group">
          <label>From</label>
          <input type="datetime-local" v-model="filterFrom" class="datetime-input" />
        </div>
        <div class="filter-group">
          <label>To</label>
          <input type="datetime-local" v-model="filterTo" class="datetime-input" />
        </div>
        <div class="filter-actions">
          <button class="btn btn-primary" @click="searchLogs">Search</button>
          <button class="btn btn-secondary" @click="clearFilter">Clear</button>
        </div>
      </div>

      <!-- Error Log Table -->
      <div class="card">
        <table>
          <thead>
            <tr>
              <th style="width: 60px;">ID</th>
              <th style="width: 70px;">Status</th>
              <th style="width: 80px;">Method</th>
              <th>URI</th>
              <th>Exception</th>
              <th>Error Message</th>
              <th style="width: 160px;">Timestamp</th>
              <th style="width: 60px;"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="logs.length === 0">
              <td colspan="8" style="text-align: center; color: var(--text-secondary); padding: 40px;">
                No error logs found
              </td>
            </tr>
            <tr v-for="log in logs" :key="log.id">
              <td style="font-family: monospace; font-size: 12px;">{{ log.id }}</td>
              <td><span :class="statusBadge(log.httpStatus)">{{ log.httpStatus }}</span></td>
              <td><span class="badge badge-info">{{ log.httpMethod }}</span></td>
              <td style="font-family: monospace; font-size: 12px; max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="log.requestUri">
                {{ log.requestUri }}
              </td>
              <td style="font-size: 12px;">
                <span style="font-family: monospace; color: var(--danger);">{{ shortClassName(log.exceptionClass) }}</span>
              </td>
              <td style="font-size: 13px; max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" :title="log.errorMessage">
                {{ log.errorMessage }}
              </td>
              <td style="font-size: 12px;">{{ formatDate(log.createdAt) }}</td>
              <td>
                <button class="btn btn-sm" @click="showDetail(log)" title="View details">Details</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button :disabled="currentPage === 0" @click="currentPage--; loadLogs()">Prev</button>
          <span style="padding: 6px 12px;">Page {{ currentPage + 1 }} of {{ totalPages || 1 }}</span>
          <button :disabled="currentPage + 1 >= totalPages" @click="currentPage++; loadLogs()">Next</button>
        </div>
      </div>

      <!-- Detail Modal -->
      <div class="modal-overlay" v-if="selectedLog" @click.self="selectedLog = null">
        <div class="modal" style="max-width: 800px;">
          <h3>Error #{{ selectedLog.id }} — {{ selectedLog.httpMethod }} {{ selectedLog.httpStatus }}</h3>
          <div style="margin-bottom: 12px;">
            <strong>URI:</strong>
            <code style="margin-left: 8px; font-size: 13px;">{{ selectedLog.requestUri }}</code>
          </div>
          <div style="margin-bottom: 12px;">
            <strong>Exception:</strong>
            <code style="margin-left: 8px; color: var(--danger); font-size: 13px;">{{ selectedLog.exceptionClass }}</code>
          </div>
          <div style="margin-bottom: 12px;">
            <strong>Message:</strong>
            <p style="margin-top: 4px; font-size: 14px;">{{ selectedLog.errorMessage }}</p>
          </div>
          <div style="margin-bottom: 12px;">
            <strong>IP:</strong> {{ selectedLog.ipAddress || '-' }}
            &nbsp;|&nbsp;
            <strong>Time:</strong> {{ formatDate(selectedLog.createdAt) }}
          </div>
          <div style="margin-bottom: 12px;">
            <strong>User Agent:</strong>
            <p style="margin-top: 4px; font-size: 12px; color: var(--text-secondary); word-break: break-all;">{{ selectedLog.userAgent || '-' }}</p>
          </div>
          <div>
            <strong>Stack Trace:</strong>
            <pre class="stack-trace">{{ selectedLog.stackTrace || 'No stack trace available' }}</pre>
          </div>
          <div class="modal-actions">
            <button class="btn btn-primary" @click="selectedLog = null">Close</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { errorLogService } from '../services/errorLogService';

const auth = useAuthStore();
const logs = ref([]);
const currentPage = ref(0);
const totalPages = ref(0);
const filterFrom = ref('');
const filterTo = ref('');
const selectedLog = ref(null);

onMounted(() => loadLogs());

async function loadLogs() {
  try {
    const params = { page: currentPage.value, size: 20 };
    if (filterFrom.value) {
      params.from = new Date(filterFrom.value).toISOString();
    }
    if (filterTo.value) {
      params.to = new Date(filterTo.value).toISOString();
    }
    const { data } = await errorLogService.list(params);
    logs.value = data.data.content;
    totalPages.value = data.data.totalPages;
  } catch (e) {
    console.error('Failed to load error logs', e);
  }
}

function searchLogs() {
  currentPage.value = 0;
  loadLogs();
}

function clearFilter() {
  filterFrom.value = '';
  filterTo.value = '';
  currentPage.value = 0;
  loadLogs();
}

function showDetail(log) {
  selectedLog.value = log;
}

function statusBadge(status) {
  if (!status) return 'badge';
  if (status >= 500) return 'badge badge-danger';
  if (status >= 400) return 'badge badge-warning';
  return 'badge';
}

function shortClassName(fqn) {
  if (!fqn) return '-';
  const parts = fqn.split('.');
  return parts[parts.length - 1];
}

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 16px;
  align-items: flex-end;
  flex-wrap: wrap;
}
.filter-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.filter-group label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}
.datetime-input {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  min-width: 200px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
.btn-secondary {
  background: #f3f4f6;
  color: var(--text);
  border: 1px solid var(--border);
}
.btn-secondary:hover {
  background: #e5e7eb;
}
.stack-trace {
  background: #1f2937;
  color: #e5e7eb;
  padding: 16px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  margin-top: 8px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
