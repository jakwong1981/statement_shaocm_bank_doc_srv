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
      <button class="logout-btn" @click="handleLogout">Logout</button>
    </aside>
    <main class="main-content">
      <div class="page-header">
        <h1>Dashboard</h1>
        <p style="color: var(--text-secondary); margin-top: 4px;">Welcome, {{ auth.username }}</p>
      </div>
      <div class="card-grid">
        <div class="card">
          <h3 style="font-size: 13px; color: var(--text-secondary);">Total Reports</h3>
          <p style="font-size: 32px; font-weight: 700;">{{ stats.total }}</p>
        </div>
        <div class="card">
          <h3 style="font-size: 13px; color: var(--text-secondary);">Pending Watermark</h3>
          <p style="font-size: 32px; font-weight: 700; color: var(--warning);">{{ stats.pending }}</p>
        </div>
        <div class="card">
          <h3 style="font-size: 13px; color: var(--text-secondary);">Ready</h3>
          <p style="font-size: 32px; font-weight: 700; color: var(--success);">{{ stats.ready }}</p>
        </div>
        <div class="card">
          <h3 style="font-size: 13px; color: var(--text-secondary);">Failed</h3>
          <p style="font-size: 32px; font-weight: 700; color: var(--danger);">{{ stats.failed }}</p>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../stores/authStore';
import api from '../services/api';

const router = useRouter();
const auth = useAuthStore();
const stats = ref({ total: 0, pending: 0, ready: 0, failed: 0 });

onMounted(async () => {
  try {
    const { data } = await api.get('/admin/reports', { params: { page: 0, size: 1 } });
    stats.value.total = data.data.totalElements || 0;
  } catch (e) {
    console.error('Failed to load stats', e);
  }
});

function handleLogout() {
  auth.logout();
  router.push('/login');
}
</script>
