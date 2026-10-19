import { api, cleanParams, toFormData, unwrap } from '../axios';

// Partner TP1–TP11
export const listMyTemplates = (params) => api.get('/partner/templates', { params: cleanParams(params) }).then(unwrap);
export const createMyTemplate = (body) => api.post('/partner/templates', body).then(unwrap);
export const getMyTemplate = (id) => api.get(`/partner/templates/${id}`).then(unwrap);
export const updateMyTemplate = (id, body) => api.put(`/partner/templates/${id}`, body).then(unwrap);
export const deleteMyTemplate = (id) => api.delete(`/partner/templates/${id}`).then(unwrap);
export const submitMyTemplate = (id) => api.post(`/partner/templates/${id}/submit`).then(unwrap);
export const syncMyTemplates = (wabaDbId) => api.post('/partner/templates/sync', { wabaDbId }).then(unwrap);
export const getMyTemplateAnalytics = (id, params) =>
  api.get(`/partner/templates/${id}/analytics`, { params: cleanParams(params) }).then(unwrap);
export const listTemplateLibrary = (params) => api.get('/partner/template-library', { params: cleanParams(params) }).then(unwrap);
export const applyLibraryTemplate = (body) => api.post('/partner/template-library/use', body).then(unwrap);
export const uploadTemplateHeader = (wabaDbId, file) =>
  api
    .post('/partner/media/template-header', toFormData({ file, wabaDbId }), { headers: { 'Content-Type': 'multipart/form-data' } })
    .then(unwrap);

// Admin TP12–TP18
export const listTemplates = (params) => api.get('/admin/templates', { params: cleanParams(params) }).then(unwrap);
export const listTemplateReviewQueue = (params) => api.get('/admin/templates/review-queue', { params: cleanParams(params) }).then(unwrap);
export const approveTemplate = (id, note) => api.post(`/admin/templates/${id}/approve`, { note }).then(unwrap);
export const rejectTemplate = (id, reason) => api.post(`/admin/templates/${id}/reject`, { reason }).then(unwrap);
export const createPlatformTemplate = (body) => api.post('/admin/templates', body).then(unwrap);
export const syncTemplatesAdmin = (wabaDbId) => api.post('/admin/templates/sync', { wabaDbId }).then(unwrap);
export const deleteTemplateAdmin = (id) => api.delete(`/admin/templates/${id}`).then(unwrap);

/** Header media sample upload for admin-created platform templates (same contract as TP11, admin scope). */
export const uploadTemplateHeaderAdmin = (wabaDbId, file) =>
  api
    .post('/admin/media/template-header', toFormData({ file, wabaDbId }), { headers: { 'Content-Type': 'multipart/form-data' } })
    .then(unwrap);
