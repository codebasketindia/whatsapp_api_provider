import { api, cleanParams, downloadFile, toFormData, unwrap } from '../axios';

// CP1–CP11
export const uploadCampaignFile = (file) =>
  api.post('/partner/campaigns/upload', toFormData({ file }), { headers: { 'Content-Type': 'multipart/form-data' } }).then(unwrap);
export const createCampaign = (body) => api.post('/partner/campaigns', body).then(unwrap);
export const updateCampaign = (id, body) => api.put(`/partner/campaigns/${id}`, body).then(unwrap);
export const prepareCampaign = (id) => api.post(`/partner/campaigns/${id}/prepare`).then(unwrap);
export const launchCampaign = (id, body) => api.post(`/partner/campaigns/${id}/launch`, body).then(unwrap);
export const pauseCampaign = (id) => api.post(`/partner/campaigns/${id}/pause`).then(unwrap);
export const resumeCampaign = (id) => api.post(`/partner/campaigns/${id}/resume`).then(unwrap);
export const cancelCampaign = (id) => api.post(`/partner/campaigns/${id}/cancel`).then(unwrap);
export const listMyCampaigns = (params) => api.get('/partner/campaigns', { params: cleanParams(params) }).then(unwrap);
export const getMyCampaign = (id) => api.get(`/partner/campaigns/${id}`).then(unwrap);
export const listCampaignRecipients = (id, params) =>
  api.get(`/partner/campaigns/${id}/recipients`, { params: cleanParams(params) }).then(unwrap);
export const downloadCampaignReport = (id, format = 'xlsx') =>
  downloadFile(`/partner/campaigns/${id}/report`, { format }, `campaign-${id}-report.${format}`);
export const deleteCampaign = (id) => api.delete(`/partner/campaigns/${id}`).then(unwrap);
// CP14
export const rescheduleCampaign = (id, scheduledAt) => api.post(`/partner/campaigns/${id}/reschedule`, { scheduledAt }).then(unwrap);

// CP12–CP13
export const listCampaigns = (params) => api.get('/admin/campaigns', { params: cleanParams(params) }).then(unwrap);
export const pauseCampaignAdmin = (id, reason) => api.post(`/admin/campaigns/${id}/pause`, { reason }).then(unwrap);
export const cancelCampaignAdmin = (id, reason) => api.post(`/admin/campaigns/${id}/cancel`, { reason }).then(unwrap);
