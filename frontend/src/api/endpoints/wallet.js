import { api, cleanParams, downloadFile, toFormData, unwrap } from '../axios';

// Admin W1–W11
export const getPartnerWallet = (partnerId) => api.get(`/admin/partners/${partnerId}/wallet`).then(unwrap);
export const listPartnerWalletTransactions = (partnerId, params) =>
  api.get(`/admin/partners/${partnerId}/wallet/transactions`, { params: cleanParams(params) }).then(unwrap);
export const adjustPartnerWallet = (partnerId, body) => api.post(`/admin/partners/${partnerId}/wallet/adjust`, body).then(unwrap);
export const listTopupRequests = (params) => api.get('/admin/topup-requests', { params: cleanParams(params) }).then(unwrap);
export const getTopupRequest = (id) => api.get(`/admin/topup-requests/${id}`).then(unwrap);
export const approveTopupRequest = (id, body = {}) => api.post(`/admin/topup-requests/${id}/approve`, body).then(unwrap);
export const rejectTopupRequest = (id, reason) => api.post(`/admin/topup-requests/${id}/reject`, { reason }).then(unwrap);
export const listCreditPacksAdmin = (params) => api.get('/admin/credit-packs', { params: cleanParams(params) }).then(unwrap);
export const createCreditPack = (body) => api.post('/admin/credit-packs', body).then(unwrap);
export const updateCreditPack = (id, body) => api.put(`/admin/credit-packs/${id}`, body).then(unwrap);
export const deleteCreditPack = (id) => api.delete(`/admin/credit-packs/${id}`).then(unwrap);

// Partner W12–W19
export const getMyWallet = () => api.get('/partner/wallet').then(unwrap);
export const setLowBalanceThreshold = (threshold) => api.put('/partner/wallet/low-balance-threshold', { threshold }).then(unwrap);
export const listMyWalletTransactions = (params) => api.get('/partner/wallet/transactions', { params: cleanParams(params) }).then(unwrap);
export const exportMyWalletTransactions = (params) =>
  downloadFile('/partner/wallet/transactions/export', params, `passbook.${params?.format || 'csv'}`);
export const listCreditPacks = () => api.get('/partner/credit-packs').then(unwrap);
export const createTopupRequest = (values) =>
  api.post('/partner/topup-requests', toFormData(values), { headers: { 'Content-Type': 'multipart/form-data' } }).then(unwrap);
export const listMyTopupRequests = (params) => api.get('/partner/topup-requests', { params: cleanParams(params) }).then(unwrap);
export const getMyTopupRequest = (id) => api.get(`/partner/topup-requests/${id}`).then(unwrap);
