import { api, cleanParams, unwrap } from '../axios';

// R1–R7
export const listRatePlans = (params) => api.get('/admin/rate-plans', { params: cleanParams(params) }).then(unwrap);
export const createRatePlan = (body) => api.post('/admin/rate-plans', body).then(unwrap);
export const getRatePlan = (id) => api.get(`/admin/rate-plans/${id}`).then(unwrap);
export const updateRatePlan = (id, body) => api.put(`/admin/rate-plans/${id}`, body).then(unwrap);
export const cloneRatePlan = (id, body) => api.post(`/admin/rate-plans/${id}/clone`, body).then(unwrap);
export const setRatePlanStatus = (id, status) => api.patch(`/admin/rate-plans/${id}/status`, { status }).then(unwrap);
export const getMyRatePlan = () => api.get('/partner/rate-plan').then(unwrap);
