import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../stores/authStore';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/LoginView.vue'),
  },
  {
    path: '/',
    redirect: '/dashboard',
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/reports',
    name: 'Reports',
    component: () => import('../views/ReportsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/clients',
    name: 'Clients',
    component: () => import('../views/ClientsView.vue'),
    meta: { requiresAuth: true, role: 'SUPER_ADMIN' },
  },
  {
    path: '/audit-logs',
    name: 'AuditLogs',
    component: () => import('../views/AuditLogsView.vue'),
    meta: { requiresAuth: true },
  },
  {
    path: '/error-logs',
    name: 'ErrorLogs',
    component: () => import('../views/ErrorLogsView.vue'),
    meta: { requiresAuth: true, role: 'SUPER_ADMIN' },
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    next('/login');
  } else {
    next();
  }
});

export default router;
