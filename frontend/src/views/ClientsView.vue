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
        <h1>API Clients</h1>
        <button class="btn btn-primary" @click="showCreate = true">Create Client</button>
      </div>

      <div class="card">
        <table>
          <thead>
            <tr>
              <th>Client Name</th>
              <th>API Key</th>
              <th>Status</th>
              <th>Allowed IPs</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in clientStore.clients" :key="c.id">
              <td>{{ c.clientName }}</td>
              <td style="font-family: monospace; font-size: 12px;">{{ c.apiKey }}</td>
              <td><span :class="c.status === 'ACTIVE' ? 'badge badge-success' : 'badge badge-danger'">{{ c.status }}</span></td>
              <td>{{ c.allowedIps || 'Any' }}</td>
              <td>{{ formatDate(c.createdAt) }}</td>
              <td>
                <button v-if="c.status === 'ACTIVE'" class="btn btn-sm btn-danger"
                  @click="clientStore.updateClientStatus(c.id, 'SUSPENDED')">Suspend</button>
                <button v-if="c.status === 'SUSPENDED'" class="btn btn-sm btn-primary"
                  @click="clientStore.updateClientStatus(c.id, 'ACTIVE')">Reactivate</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
        <div class="modal">
          <h3>Create API Client</h3>
          <div class="form-group">
            <label>Client Name</label>
            <input v-model="newClient.clientName" placeholder="e.g. ACME Corp" />
          </div>
          <div class="form-group">
            <label>Allowed IPs (comma-separated, optional)</label>
            <input v-model="newClient.ips" placeholder="e.g. 10.0.0.1, 192.168.1.0/24" />
          </div>
          <div v-if="createdSecret" class="card" style="background: #fef3c7; border-color: #f59e0b; margin-top: 12px;">
            <p style="font-weight: 600; margin-bottom: 8px;">Save this secret - it will not be shown again!</p>
            <p style="font-family: monospace; font-size: 13px; word-break: break-all;">{{ createdSecret }}</p>
          </div>
          <div class="modal-actions">
            <button class="btn" @click="showCreate = false; createdSecret = ''">Close</button>
            <button v-if="!createdSecret" class="btn btn-primary" @click="handleCreate"
              :disabled="!newClient.clientName">Create</button>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../stores/authStore';
import { useClientStore } from '../stores/clientStore';

const auth = useAuthStore();
const clientStore = useClientStore();
const showCreate = ref(false);
const createdSecret = ref('');
const newClient = ref({ clientName: '', ips: '' });

onMounted(() => clientStore.fetchClients());

async function handleCreate() {
  const data = { clientName: newClient.value.clientName };
  if (newClient.value.ips) {
    data.allowedIps = newClient.value.ips.split(',').map(s => s.trim());
  }
  const result = await clientStore.createClient(data);
  createdSecret.value = result.apiSecret;
  newClient.value = { clientName: '', ips: '' };
}

function formatDate(iso) {
  if (!iso) return '-';
  return new Date(iso).toLocaleDateString();
}
</script>
