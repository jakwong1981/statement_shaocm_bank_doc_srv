import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import api from '../services/api';

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref(localStorage.getItem('accessToken') || '');
  const refreshToken = ref(localStorage.getItem('refreshToken') || '');
  const username = ref(localStorage.getItem('username') || '');
  const role = ref(localStorage.getItem('role') || '');

  const isAuthenticated = computed(() => !!accessToken.value);

  async function login(user, password) {
    const { data } = await api.post('/admin/auth/login', {
      username: user,
      password: password,
    });
    const resp = data.data;
    accessToken.value = resp.accessToken;
    refreshToken.value = resp.refreshToken;
    username.value = resp.username;
    role.value = resp.role;
    localStorage.setItem('accessToken', resp.accessToken);
    localStorage.setItem('refreshToken', resp.refreshToken);
    localStorage.setItem('username', resp.username);
    localStorage.setItem('role', resp.role);
  }

  function logout() {
    accessToken.value = '';
    refreshToken.value = '';
    username.value = '';
    role.value = '';
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
  }

  return { accessToken, refreshToken, username, role, isAuthenticated, login, logout };
});
