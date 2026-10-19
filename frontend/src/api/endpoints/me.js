import { api, cleanParams, unwrap } from '../axios';

// A8–A15
export const getMe = () => api.get('/me').then(unwrap);
export const updateMe = (body) => api.put('/me', body).then(unwrap);
export const changePassword = (body) => api.post('/me/change-password', body).then(unwrap);
export const setTwoFactor = (body) => api.put('/me/two-factor', body).then(unwrap);
export const listNotifications = (params) => api.get('/me/notifications', { params: cleanParams(params) }).then(unwrap);
export const getUnreadNotificationCount = () => api.get('/me/notifications/unread-count').then(unwrap);
export const markNotificationRead = (id) => api.post(`/me/notifications/${id}/read`).then(unwrap);
export const markAllNotificationsRead = () => api.post('/me/notifications/read-all').then(unwrap);
