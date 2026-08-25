import { defineStore } from 'pinia';
import { ref } from 'vue';
import { reportService } from '../services/reportService';

export const useReportStore = defineStore('reports', () => {
  const reports = ref([]);
  const totalPages = ref(0);
  const loading = ref(false);

  async function fetchReports(page = 0, size = 20, filters = {}) {
    loading.value = true;
    try {
      const { data } = await reportService.list({ page, size, ...filters });
      const pageData = data.data;
      reports.value = pageData.content;
      totalPages.value = pageData.totalPages;
    } finally {
      loading.value = false;
    }
  }

  async function uploadReport(file, benchmarkTag, metadata) {
    const { data } = await reportService.upload(file, benchmarkTag, metadata);
    return data.data;
  }

  async function downloadReport(reportId) {
    const { data } = await reportService.download(reportId);
    const blob = new Blob([data], { type: 'application/pdf' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `report-${reportId}.pdf`;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  return { reports, totalPages, loading, fetchReports, uploadReport, downloadReport };
});
