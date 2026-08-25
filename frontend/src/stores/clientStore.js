import { defineStore } from 'pinia';
import { ref } from 'vue';
import { clientService } from '../services/clientService';

export const useClientStore = defineStore('clients', () => {
  const clients = ref([]);
  const loading = ref(false);

  async function fetchClients() {
    loading.value = true;
    try {
      const { data } = await clientService.list();
      clients.value = data.data;
    } finally {
      loading.value = false;
    }
  }

  async function createClient(clientData) {
    const { data } = await clientService.create(clientData);
    await fetchClients();
    return data.data;
  }

  async function updateClientStatus(id, status) {
    await clientService.updateStatus(id, status);
    await fetchClients();
  }

  return { clients, loading, fetchClients, createClient, updateClientStatus };
});
