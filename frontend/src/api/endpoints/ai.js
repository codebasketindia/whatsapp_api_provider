import { api, cleanParams, toFormData, unwrap } from '../axios';

// AI1–AI9 (partner) — contract §4.19
export const getAssistant = () => api.get('/partner/ai/assistant').then(unwrap);
export const updateAssistant = (body) => api.put('/partner/ai/assistant', body).then(unwrap);
export const listKnowledge = (params) => api.get('/partner/ai/knowledge', { params: cleanParams(params) }).then(unwrap);
export const createKnowledge = (body) => api.post('/partner/ai/knowledge', body).then(unwrap);
export const updateKnowledge = (id, body) => api.put(`/partner/ai/knowledge/${id}`, body).then(unwrap);
export const deleteKnowledge = (id) => api.delete(`/partner/ai/knowledge/${id}`).then(unwrap);
export const uploadKnowledgeMedia = (file, onUploadProgress) =>
  api
    .post('/partner/ai/knowledge/media', toFormData({ file }), {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
    })
    .then(unwrap);
export const testAssistant = (body) => api.post('/partner/ai/test', body).then(unwrap);
export const getAiUsage = (params) => api.get('/partner/ai/usage', { params: cleanParams(params) }).then(unwrap);

// AI11 (admin)
export const getAiUsageAdmin = (params) => api.get('/admin/ai/usage', { params: cleanParams(params) }).then(unwrap);
