import api from './api';

export const auditService = {
  list(params = {}) {
    return api.get('/admin/audit-logs', { params });
  },
};
