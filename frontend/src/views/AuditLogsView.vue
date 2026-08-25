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
        <h1>Audit Logs</h1>
      </div>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Actor</th>
              <th>Action</th>
              <th>Target Report</th>
              <th>IP Address</th>
              <th>Timestamp</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id">
              <td>{{ log.id }}</td>
              <td>
                <span style="font-size: 12px; color: var(--text-secondary);">{{ log.actorType }}</span><br/>
                <span style="font-family: monospace; font-size: 12px;">{{ log.actorId }}</span>
              </td>
              <td><span :class="actionBadge(log.action)">{{ log.action }}</span></td>
              <td style="font-family: monospace; font-size: 12px;">{{ log.targetReportId || '-' }}</td>
              <td>{{ log.ipAddress }}</td>
              <td>{{ formatDate(log.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="pagination">
          <button :disabled="currentPage === 0" @click="currentPage--; loadLogs()">Prev</button>
          <span style="padding: 6px 12px;">Page {{ currentPage + 1 }} of {{ totalPages || 1 }}</span>
          <button :disabled="currentPage + 1 >= totalPages" @click="currentPage++; loadLogs()">Next</button>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { auditService } from '../services/auditService';

const auth = useAuthStore();
const logs = ref([]);
const currentPage = ref(0);
const totalPages = ref(0);

onMounted(() => loadLogs());

async function loadLogs() {
  try {
    const { data } = await auditService.list({ page: currentPage.value, size: 20 });
    logs.value = data.data.content;
    totalPages.value = data.data.totalPages;
  } catch (e) {
    console.error('Failed to load audit logs', e);
  }
}

function actionBadge(action) {
  const map = {
    UPLOAD: 'badge badge-success',
    DOWNLOAD: 'badge badge-info',
    DELETE: 'badge badge-danger',
    LOGIN: 'badge badge-warning',
    KEY_ROTATION: 'badge badge-info',
  };
  return map[action] || 'badge';
}

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  });
}
</script>
