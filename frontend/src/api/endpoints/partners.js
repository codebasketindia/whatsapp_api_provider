import { api, cleanParams, unwrap } from '../axios';

// P1–P17
export const listPartners = (params) => api.get('/admin/partners', { params: cleanParams(params) }).then(unwrap);
export const createPartner = (body) => api.post('/admin/partners', body).then(unwrap);
export const getPartner = (id) => api.get(`/admin/partners/${id}`).then(unwrap);
export const updatePartner = (id, body) => api.put(`/admin/partners/${id}`, body).then(unwrap);
export const setPartnerStatus = (id, body) => api.patch(`/admin/partners/${id}/status`, body).then(unwrap);
export const setPartnerRatePlan = (id, ratePlanId) => api.patch(`/admin/partners/${id}/rate-plan`, { ratePlanId }).then(unwrap);
export const setPartnerOwnNumber = (id, allowed) => api.patch(`/admin/partners/${id}/own-number`, { allowed }).then(unwrap);
export const setPartnerKyc = (id, kycVerified) => api.patch(`/admin/partners/${id}/kyc`, { kycVerified }).then(unwrap);
export const resetPartnerPassword = (id) => api.post(`/admin/partners/${id}/reset-password`).then(unwrap);
export const loginAsPartner = (id, reason) => api.post(`/admin/partners/${id}/login-as`, { reason }).then(unwrap);
export const listPartnerSenders = (id) => api.get(`/admin/partners/${id}/senders`).then(unwrap);
export const assignPartnerSender = (id, body) => api.post(`/admin/partners/${id}/senders`, body).then(unwrap);
export const removePartnerSender = (id, senderId) => api.delete(`/admin/partners/${id}/senders/${senderId}`).then(unwrap);
export const getPartnerUsage = (id, params) => api.get(`/admin/partners/${id}/usage`, { params: cleanParams(params) }).then(unwrap);
export const getPartnerActivity = (id, params) => api.get(`/admin/partners/${id}/activity`, { params: cleanParams(params) }).then(unwrap);
export const listPartnerApiKeys = (id) => api.get(`/admin/partners/${id}/api-keys`).then(unwrap);
export const updatePartnerApiKey = (id, keyId, body) => api.patch(`/admin/partners/${id}/api-keys/${keyId}`, body).then(unwrap);
