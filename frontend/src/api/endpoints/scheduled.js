import { api, cleanParams, unwrap } from '../axios';

// SC1–SC7 — contract §4.20
export const listScheduled = (params) => api.get('/partner/scheduled-messages', { params: cleanParams(params) }).then(unwrap);
export const createScheduled = (body) => api.post('/partner/scheduled-messages', body).then(unwrap);
export const getScheduled = (uid) => api.get(`/partner/scheduled-messages/${uid}`).then(unwrap);
export const updateScheduled = (uid, body) => api.put(`/partner/scheduled-messages/${uid}`, body).then(unwrap);
export const rescheduleScheduled = (uid, scheduledAt) =>
  api.post(`/partner/scheduled-messages/${uid}/reschedule`, { scheduledAt }).then(unwrap);
export const cancelScheduled = (uid) => api.post(`/partner/scheduled-messages/${uid}/cancel`).then(unwrap);
export const sendScheduledNow = (uid) => api.post(`/partner/scheduled-messages/${uid}/send-now`).then(unwrap);
