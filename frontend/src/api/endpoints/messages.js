import { api, cleanParams, downloadFile, toFormData, unwrap } from '../axios';

// Partner MS1–MS6
export const estimateCost = (body) => api.post('/partner/messages/estimate', body).then(unwrap);
export const sendMessage = (body) => api.post('/partner/messages/send', body).then(unwrap);
export const listMyMessages = (params) => api.get('/partner/messages', { params: cleanParams(params) }).then(unwrap);
export const getMyMessage = (uid) => api.get(`/partner/messages/${uid}`).then(unwrap);
export const exportMyMessages = (params) => downloadFile('/partner/messages/export', params, `messages.${params?.format || 'csv'}`);
export const uploadMedia = (senderId, file, onUploadProgress) =>
  api
    .post('/partner/media', toFormData({ file, senderId }), {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress,
    })
    .then(unwrap);

// Inbox IN1–IN6
export const listConversations = (params) => api.get('/partner/inbox/conversations', { params: cleanParams(params) }).then(unwrap);
export const listConversationMessages = (senderId, waId, params) =>
  api.get(`/partner/inbox/conversations/${senderId}/${waId}/messages`, { params: cleanParams(params) }).then(unwrap);
export const replyToConversation = (senderId, waId, body) =>
  api.post(`/partner/inbox/conversations/${senderId}/${waId}/reply`, body).then(unwrap);
export const markConversationRead = (senderId, waId, typing = false) =>
  api.post(`/partner/inbox/conversations/${senderId}/${waId}/read`, { typing }).then(unwrap);
export const getInboxStreamTicket = () => api.post('/partner/inbox/stream-ticket').then(unwrap);
// IN5: EventSource cannot send Authorization headers, so the stream lives on a public path and uses the IN6 ticket.
export const inboxStreamPath = '/public/inbox-stream';

// Admin ML1–ML3
export const listMessages = (params) => api.get('/admin/messages', { params: cleanParams(params) }).then(unwrap);
export const getMessage = (uid) => api.get(`/admin/messages/${uid}`).then(unwrap);
export const exportMessages = (params) => downloadFile('/admin/messages/export', params, `messages.${params?.format || 'csv'}`);
