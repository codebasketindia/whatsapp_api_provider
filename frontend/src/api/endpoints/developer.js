import { api, cleanParams, unwrap } from '../axios';

// D1–D12
export const listApiKeys = () => api.get('/partner/api-keys').then(unwrap);
export const createApiKey = (body) => api.post('/partner/api-keys', body).then(unwrap);
export const updateApiKey = (id, body) => api.patch(`/partner/api-keys/${id}`, body).then(unwrap);
export const regenerateApiKey = (id, password) => api.post(`/partner/api-keys/${id}/regenerate`, { password }).then(unwrap);
export const deleteApiKey = (id) => api.delete(`/partner/api-keys/${id}`).then(unwrap);
export const getWebhookConfig = () => api.get('/partner/webhook').then(unwrap);
export const updateWebhookConfig = (body) => api.put('/partner/webhook', body).then(unwrap);
export const rotateWebhookSecret = () => api.post('/partner/webhook/rotate-secret').then(unwrap);
export const testWebhook = (eventType) => api.post('/partner/webhook/test', { eventType }).then(unwrap);
export const listWebhookLogs = (params) => api.get('/partner/webhook/logs', { params: cleanParams(params) }).then(unwrap);
export const retryWebhookLog = (id) => api.post(`/partner/webhook/logs/${id}/retry`).then(unwrap);
export const getDeveloperInfo = () => api.get('/partner/developer/info').then(unwrap);
