import { api, cleanParams, downloadFile, unwrap } from '../axios';

// DB1–DB4
export const getAdminDashboard = () => api.get('/admin/dashboard').then(unwrap);
export const getAdminDashboardChart = (params) => api.get('/admin/dashboard/chart', { params: cleanParams(params) }).then(unwrap);
export const getPartnerDashboard = () => api.get('/partner/dashboard').then(unwrap);
export const getPartnerDashboardChart = (params) => api.get('/partner/dashboard/chart', { params: cleanParams(params) }).then(unwrap);

// RP1–RP6: format=json returns data; xlsx/pdf/csv downloads a file.
const report = (path) => ({
  get: (params) => api.get(path, { params: cleanParams({ ...params, format: 'json' }) }).then(unwrap),
  download: (params, format = 'xlsx') =>
    downloadFile(path, { ...params, format }, `${path.split('/').pop()}-report.${format}`),
});

export const partnerReport = report('/admin/reports/partners');
export const categoryReport = report('/admin/reports/categories');
export const countryReport = report('/admin/reports/countries');
export const revenueReport = report('/admin/reports/revenue');
export const reconciliationReport = report('/admin/reports/reconciliation');
export const myUsageReport = report('/partner/reports/usage');
