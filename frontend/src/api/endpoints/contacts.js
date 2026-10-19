import { api, cleanParams, downloadFile, toFormData, unwrap } from '../axios';

const multipart = { headers: { 'Content-Type': 'multipart/form-data' } };

// C1–C12
export const listContacts = (params) => api.get('/partner/contacts', { params: cleanParams(params) }).then(unwrap);
export const createContact = (body) => api.post('/partner/contacts', body).then(unwrap);
export const getContact = (id) => api.get(`/partner/contacts/${id}`).then(unwrap);
export const updateContact = (id, body) => api.put(`/partner/contacts/${id}`, body).then(unwrap);
export const deleteContact = (id) => api.delete(`/partner/contacts/${id}`).then(unwrap);
export const bulkDeleteContacts = (ids) => api.post('/partner/contacts/bulk-delete', { ids }).then(unwrap);
export const importContacts = (values) => api.post('/partner/contacts/import', toFormData(values), multipart).then(unwrap);
export const previewContactImport = (file) => api.post('/partner/contacts/import/preview', toFormData({ file }), multipart).then(unwrap);
export const downloadContactImportSample = () => downloadFile('/partner/contacts/import/sample', undefined, 'contacts-sample.csv');
export const listContactTags = () => api.get('/partner/contacts/tags').then(unwrap);
export const optOutContact = (id, reason) => api.post(`/partner/contacts/${id}/opt-out`, { reason }).then(unwrap);
export const optInContact = (id) => api.post(`/partner/contacts/${id}/opt-in`, { confirmConsent: true }).then(unwrap);
export const listOptOuts = (params) => api.get('/partner/opt-outs', { params: cleanParams(params) }).then(unwrap);
export const getOptOutSettings = () => api.get('/partner/settings/opt-out').then(unwrap);
export const updateOptOutSettings = (body) => api.put('/partner/settings/opt-out', body).then(unwrap);

// G1–G4
export const listContactGroups = (params) => api.get('/partner/contact-groups', { params: cleanParams(params) }).then(unwrap);
export const createContactGroup = (body) => api.post('/partner/contact-groups', body).then(unwrap);
export const updateContactGroup = (id, body) => api.put(`/partner/contact-groups/${id}`, body).then(unwrap);
export const deleteContactGroup = (id) => api.delete(`/partner/contact-groups/${id}`).then(unwrap);
export const addGroupMembers = (id, contactIds) => api.post(`/partner/contact-groups/${id}/members`, { contactIds }).then(unwrap);
export const removeGroupMembers = (id, contactIds) =>
  api.delete(`/partner/contact-groups/${id}/members`, { data: { contactIds } }).then(unwrap);
