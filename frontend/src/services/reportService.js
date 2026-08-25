import api from './api';

export const reportService = {
  list(params = {}) {
    return api.get('/admin/reports', { params });
  },
  upload(file, benchmarkTag, metadata) {
    const formData = new FormData();
    formData.append('file', file);
    if (benchmarkTag) formData.append('benchmark_tag', benchmarkTag);
    if (metadata) formData.append('metadata', metadata);
    return api.post('/admin/reports', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  download(reportId) {
    return api.get(`/admin/reports/${reportId}/download`, { responseType: 'blob' });
  },
};
