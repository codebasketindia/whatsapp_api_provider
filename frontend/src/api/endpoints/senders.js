import { api, unwrap } from '../axios';

// Partner E1–E8
export const listMySenders = () => api.get('/partner/senders').then(unwrap);
export const getMySender = (id) => api.get(`/partner/senders/${id}`).then(unwrap);
export const setDefaultSender = (id) => api.patch(`/partner/senders/${id}/default`).then(unwrap);
export const getEmbeddedSignupConfig = () => api.get('/partner/embedded-signup/config').then(unwrap);
export const completeEmbeddedSignup = (body) => api.post('/partner/embedded-signup/complete', body).then(unwrap);
export const logEmbeddedSignupEvent = (body) => api.post('/partner/embedded-signup/events', body).then(unwrap);
export const reconnectSender = (id, body) => api.post(`/partner/senders/${id}/reconnect`, body).then(unwrap);
export const disconnectMySender = (id) => api.post(`/partner/senders/${id}/disconnect`, { confirm: true }).then(unwrap);
