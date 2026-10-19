import { api, cleanParams, unwrap } from '../axios';

// M1–M18, E9
export const getMetaSettings = () => api.get('/admin/meta/settings').then(unwrap);
export const updateMetaSettings = (body) => api.put('/admin/meta/settings', body).then(unwrap);
export const testMetaSettings = () => api.post('/admin/meta/settings/test').then(unwrap);
export const listWabas = (params) => api.get('/admin/whatsapp/accounts', { params: cleanParams(params) }).then(unwrap);
export const addPlatformWaba = (body) => api.post('/admin/whatsapp/accounts', body).then(unwrap);
export const getWaba = (id) => api.get(`/admin/whatsapp/accounts/${id}`).then(unwrap);
export const updateWabaToken = (id, accessToken) => api.put(`/admin/whatsapp/accounts/${id}/token`, { accessToken }).then(unwrap);
export const syncWaba = (id) => api.post(`/admin/whatsapp/accounts/${id}/sync`).then(unwrap);
export const testWaba = (id) => api.post(`/admin/whatsapp/accounts/${id}/test`).then(unwrap);
export const subscribeWaba = (id) => api.post(`/admin/whatsapp/accounts/${id}/subscribe`).then(unwrap);
export const shareWabaCreditLine = (id) => api.post(`/admin/whatsapp/accounts/${id}/share-credit-line`).then(unwrap);
export const manualConnect = (body) => api.post('/admin/whatsapp/manual-connect', body).then(unwrap);
export const listSenders = (params) => api.get('/admin/senders', { params: cleanParams(params) }).then(unwrap);
export const getSender = (id) => api.get(`/admin/senders/${id}`).then(unwrap);
export const updateSender = (id, body) => api.patch(`/admin/senders/${id}`, body).then(unwrap);
export const assignSender = (id, body) => api.post(`/admin/senders/${id}/assign`, body).then(unwrap);
export const unassignSender = (id) => api.post(`/admin/senders/${id}/unassign`).then(unwrap);
export const disconnectSenderAdmin = (id, reason) => api.post(`/admin/senders/${id}/disconnect`, { reason }).then(unwrap);
export const getMetaHealth = () => api.get('/admin/meta/health').then(unwrap);
