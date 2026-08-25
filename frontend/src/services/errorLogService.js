import api from './api';

export const errorLogService = {
  list(params = {}) {
    return api.get('/admin/error-logs', { params });
  },
};
