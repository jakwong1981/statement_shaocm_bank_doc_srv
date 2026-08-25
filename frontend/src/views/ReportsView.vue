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
      <div class="page-header" style="display: flex; justify-content: space-between; align-items: center;">
        <h1>Reports</h1>
        <button class="btn btn-primary" @click="showUpload = true">Upload Report</button>
      </div>

      <div class="card" style="margin-bottom: 16px; display: flex; gap: 12px; align-items: flex-end;">
        <div class="form-group" style="margin: 0; flex: 1;">
          <label>Status Filter</label>
          <select v-model="statusFilter" @change="loadReports">
            <option value="">All</option>
            <option value="PENDING_WATERMARK">Pending</option>
            <option value="PROCESSING">Processing</option>
            <option value="READY">Ready</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
        <div class="form-group" style="margin: 0; flex: 1;">
          <label>Benchmark Tag</label>
          <input v-model="tagFilter" @input="loadReports" placeholder="Filter by tag..." />
        </div>
      </div>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Filename</th>
              <th>Status</th>
              <th>Size</th>
              <th>Pages</th>
              <th>Benchmark</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in reportStore.reports" :key="r.reportId">
              <td>{{ r.filename }}</td>
              <td><span :class="statusBadge(r.status)">{{ r.status }}</span></td>
              <td>{{ formatSize(r.fileSizeBytes) }}</td>
              <td>{{ r.pageCount || '-' }}</td>
              <td>{{ r.benchmarkTag || '-' }}</td>
              <td>{{ formatDate(r.createdAt) }}</td>
              <td>
                <button v-if="r.status === 'READY'" class="btn btn-sm btn-primary"
                  @click="reportStore.downloadReport(r.reportId)">Download</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button :disabled="currentPage === 0" @click="currentPage--; loadReports()">Prev</button>
          <span style="padding: 6px 12px;">Page {{ currentPage + 1 }} of {{ reportStore.totalPages || 1 }}</span>
          <button :disabled="currentPage + 1 >= reportStore.totalPages" @click="currentPage++; loadReports()">Next</button>
        </div>
      </div>

      <div v-if="showUpload" class="modal-overlay" @click.self="showUpload = false">
        <div class="modal">
          <h3>Upload Report</h3>
          <div class="form-group">
            <label>PDF File</label>
            <input type="file" accept=".pdf" @change="selectedFile = $event.target.files[0]" />
          </div>
          <div class="form-group">
            <label>Benchmark Tag (optional)</label>
            <input v-model="uploadTag" placeholder="e.g. Q3-AUDIT-2026" />
          </div>
          <div class="modal-actions">
            <button class="btn" @click="showUpload = false">Cancel</button>
            <button class="btn btn-primary" @click="handleUpload" :disabled="!selectedFile">Upload</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { useReportStore } from '../stores/reportStore';

const auth = useAuthStore();
const reportStore = useReportStore();
const currentPage = ref(0);
const statusFilter = ref('');
const tagFilter = ref('');
const showUpload = ref(false);
const selectedFile = ref(null);
const uploadTag = ref('');

onMounted(() => loadReports());

function loadReports() {
  const filters = {};
  if (statusFilter.value) filters.status = statusFilter.value;
  if (tagFilter.value) filters.benchmarkTag = tagFilter.value;
  reportStore.fetchReports(currentPage.value, 20, filters);
}

async function handleUpload() {
  if (!selectedFile.value) return;
  await reportStore.uploadReport(selectedFile.value, uploadTag.value || null, null);
  showUpload.value = false;
  selectedFile.value = null;
  uploadTag.value = '';
  loadReports();
}

function statusBadge(status) {
  const map = {
    READY: 'badge badge-success',
    PENDING_WATERMARK: 'badge badge-warning',
    PROCESSING: 'badge badge-info',
    FAILED: 'badge badge-danger',
  };
  return map[status] || 'badge';
}

function formatSize(bytes) {
  if (!bytes) return '-';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / 1048576).toFixed(1) + ' MB';
}

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}
</script>
