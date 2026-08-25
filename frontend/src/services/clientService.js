import api from './api';

export const clientService = {
  list() {
    return api.get('/admin/clients');
  },
  create(data) {
    return api.post('/admin/clients', data);
  },
  updateStatus(id, status) {
    return api.patch(`/admin/clients/${id}/status`, null, { params: { status } });
  },
};
