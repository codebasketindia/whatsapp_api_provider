import { api, unwrap } from '../axios';

// A1–A7
export const login = (body) => api.post('/auth/login', body).then(unwrap);
export const loginTwoFactor = (body) => api.post('/auth/login/2fa', body).then(unwrap);
export const resendOtp = (challengeId) => api.post('/auth/otp/resend', { challengeId }).then(unwrap);
export const refresh = (refreshToken) => api.post('/auth/refresh', { refreshToken }).then(unwrap);
export const logout = (refreshToken) => api.post('/auth/logout', { refreshToken }).then(unwrap);
export const forgotPassword = (email) => api.post('/auth/forgot-password', { email }).then(unwrap);
export const resetPassword = (body) => api.post('/auth/reset-password', body).then(unwrap);
// ST6
export const getBranding = () => api.get('/public/branding').then(unwrap);
