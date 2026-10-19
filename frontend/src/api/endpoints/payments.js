import { api, cleanParams, downloadFile, unwrap } from '../axios';

// Partner PG1–PG8
export const getPaymentConfig = () => api.get('/partner/payments/config').then(unwrap);
export const createPaymentOrder = (body) => api.post('/partner/payments/orders', body).then(unwrap);
export const verifyPayment = (body) => api.post('/partner/payments/verify', body).then(unwrap);
export const reportPaymentFailed = (uid, body) => api.post(`/partner/payments/orders/${uid}/failed`, body).then(unwrap);
export const listMyPayments = (params) => api.get('/partner/payments', { params: cleanParams(params) }).then(unwrap);
export const getMyPayment = (uid) => api.get(`/partner/payments/${uid}`).then(unwrap);
export const listMyInvoices = (params) => api.get('/partner/invoices', { params: cleanParams(params) }).then(unwrap);
export const downloadMyInvoice = (id, invoiceNo) =>
  downloadFile(`/partner/invoices/${id}/pdf`, undefined, `${(invoiceNo || `invoice-${id}`).replace(/\//g, '-')}.pdf`);

// Admin PG9–PG14
export const listPayments = (params) => api.get('/admin/payments', { params: cleanParams(params) }).then(unwrap);
export const getPayment = (uid) => api.get(`/admin/payments/${uid}`).then(unwrap);
export const reconcilePayment = (uid) => api.post(`/admin/payments/${uid}/reconcile`).then(unwrap);
export const reconcilePendingPayments = () => api.post('/admin/payments/reconcile-pending').then(unwrap);
export const listInvoices = (params) => api.get('/admin/invoices', { params: cleanParams(params) }).then(unwrap);
export const downloadInvoice = (id, invoiceNo) =>
  downloadFile(`/admin/invoices/${id}/pdf`, undefined, `${(invoiceNo || `invoice-${id}`).replace(/\//g, '-')}.pdf`);
