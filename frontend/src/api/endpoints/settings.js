import { api, toFormData, unwrap } from '../axios';

/** Groups: company, gst, payment-gateway, smtp, charging, wallet, rate-limit, security (ST1–ST5). */
export const SETTINGS_GROUPS = ['company', 'gst', 'payment-gateway', 'smtp', 'charging', 'wallet', 'rate-limit', 'security', 'ai'];

export const getSettings = (group) => api.get(`/admin/settings/${group}`).then(unwrap);
export const updateSettings = (group, body) => api.put(`/admin/settings/${group}`, body).then(unwrap);
export const uploadCompanyLogo = (file) =>
  api.post('/admin/settings/company/logo', toFormData({ file }), { headers: { 'Content-Type': 'multipart/form-data' } }).then(unwrap);
export const testSmtp = (to) => api.post('/admin/settings/smtp/test', { to }).then(unwrap);
export const testPaymentGateway = () => api.post('/admin/settings/payment-gateway/test').then(unwrap);
