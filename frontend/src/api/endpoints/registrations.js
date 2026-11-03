import { API_BASE_URL, api, cleanParams, downloadFile, toFormData, unwrap } from '../axios';

/** "/api/public/passes/x.jpg" → URL the browser can load directly (public, no login). */
export const publicUrl = (path) => (path ? `${API_BASE_URL}${String(path).replace(/^\/api/, '')}` : null);

// RF1–RF9 (partner) — contract §4.21
export const listForms = () => api.get('/partner/registration-forms').then(unwrap);
export const getForm = (id) => api.get(`/partner/registration-forms/${id}`).then(unwrap);
export const createForm = (body) => api.post('/partner/registration-forms', body).then(unwrap);
export const updateForm = (id, body) => api.put(`/partner/registration-forms/${id}`, body).then(unwrap);
export const deleteForm = (id) => api.delete(`/partner/registration-forms/${id}`).then(unwrap);
export const uploadDesign = (file) =>
  api.post('/partner/registration-forms/design', toFormData({ file }), { headers: { 'Content-Type': 'multipart/form-data' } }).then(unwrap);
/** Returns an object URL of the rendered sample pass (caller revokes it). */
export const previewPass = (body) =>
  api.post('/partner/registration-forms/preview', body, { responseType: 'blob' }).then((res) => URL.createObjectURL(res.data));
export const broadcast = (id, body) => api.post(`/partner/registration-forms/${id}/broadcast`, body).then(unwrap);
export const exportRegistrations = (id) => downloadFile(`/partner/registration-forms/${id}/export`, undefined, `registrations-${id}.csv`);

// RG1–RG3
export const listRegistrations = (params) => api.get('/partner/registrations', { params: cleanParams(params) }).then(unwrap);
export const resendPass = (uid) => api.post(`/partner/registrations/${uid}/resend`).then((res) => res.data);
export const regeneratePass = (uid) => api.post(`/partner/registrations/${uid}/regenerate`).then(unwrap);

// PR1–PR3 (public, no login)
export const getPublicForm = (slug) => api.get(`/public/register/${encodeURIComponent(slug)}`).then(unwrap);
export const register = (slug, values) =>
  api.post(`/public/register/${encodeURIComponent(slug)}`, toFormData(values), { headers: { 'Content-Type': 'multipart/form-data' } }).then(unwrap);
