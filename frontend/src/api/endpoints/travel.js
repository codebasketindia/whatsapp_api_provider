import { api, cleanParams, unwrap } from '../axios';

// TP1–TP4 / TB1–TB4 (partner) — contract §4.22
export const listPackages = (params) => api.get('/partner/travel/packages', { params: cleanParams(params) }).then(unwrap);
export const createPackage = (body) => api.post('/partner/travel/packages', body).then(unwrap);
export const updatePackage = (id, body) => api.put(`/partner/travel/packages/${id}`, body).then(unwrap);
export const deletePackage = (id) => api.delete(`/partner/travel/packages/${id}`).then(unwrap);

export const listBookings = (params) => api.get('/partner/travel/bookings', { params: cleanParams(params) }).then(unwrap);
export const createBooking = (body) => api.post('/partner/travel/bookings', body).then(unwrap);
export const updateBooking = (uid, body) => api.put(`/partner/travel/bookings/${uid}`, body).then(unwrap);
export const deleteBooking = (uid) => api.delete(`/partner/travel/bookings/${uid}`).then(unwrap);
