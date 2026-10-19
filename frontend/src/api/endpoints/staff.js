import { api, cleanParams, unwrap } from '../axios';

// S1–S11
export const listPermissions = () => api.get('/admin/permissions').then(unwrap);
export const listRoles = () => api.get('/admin/roles').then(unwrap);
export const createRole = (body) => api.post('/admin/roles', body).then(unwrap);
export const updateRole = (id, body) => api.put(`/admin/roles/${id}`, body).then(unwrap);
export const deleteRole = (id) => api.delete(`/admin/roles/${id}`).then(unwrap);
export const listStaff = (params) => api.get('/admin/staff', { params: cleanParams(params) }).then(unwrap);
export const createStaff = (body) => api.post('/admin/staff', body).then(unwrap);
export const updateStaff = (uid, body) => api.put(`/admin/staff/${uid}`, body).then(unwrap);
export const setStaffStatus = (uid, status) => api.patch(`/admin/staff/${uid}/status`, { status }).then(unwrap);
export const resetStaffPassword = (uid) => api.post(`/admin/staff/${uid}/reset-password`).then(unwrap);
export const listAuditLogs = (params) => api.get('/admin/audit-logs', { params: cleanParams(params) }).then(unwrap);
