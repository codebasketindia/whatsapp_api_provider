import axios from 'axios';
import { tokenStore } from '../auth/tokenStore';

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const api = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
});

// Separate instance without interceptors for the refresh call itself.
const bare = axios.create({ baseURL: API_BASE_URL, timeout: 30000 });

api.interceptors.request.use((config) => {
  const token = tokenStore.getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshPromise = null;

/** Rotates the refresh token once even if many requests fail at the same time. */
export function refreshSession() {
  if (!refreshPromise) {
    const refreshToken = tokenStore.getRefreshToken();
    if (!refreshToken) return Promise.reject(new Error('No session'));
    refreshPromise = bare
      .post('/auth/refresh', { refreshToken })
      .then((res) => {
        const data = res.data?.data;
        tokenStore.setTokens({ accessToken: data.accessToken, refreshToken: data.refreshToken });
        return data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function isAuthEndpoint(url = '') {
  return url.startsWith('/auth/') || url.includes('/auth/');
}

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const { config, response } = error;
    if (!response || !config) return Promise.reject(error);
    if (response.status === 403 && response.data?.code === 'AUTH_PASSWORD_CHANGE_REQUIRED') {
      if (window.location.pathname !== '/change-password') window.location.assign('/change-password');
      return Promise.reject(error);
    }
    if (response.status === 401 && !config._retried && !isAuthEndpoint(config.url)) {
      config._retried = true;
      try {
        await refreshSession();
        config.headers = config.headers || {};
        config.headers.Authorization = `Bearer ${tokenStore.getAccessToken()}`;
        return api(config);
      } catch (refreshError) {
        tokenStore.clear();
        if (!window.location.pathname.startsWith('/login')) {
          const next = encodeURIComponent(window.location.pathname + window.location.search);
          window.location.assign(`/login?next=${next}`);
        }
        return Promise.reject(error);
      }
    }
    return Promise.reject(error);
  },
);

/** Returns the `data` field of the standard envelope. */
export const unwrap = (res) => res.data?.data;

/** Error code from the envelope (e.g. WALLET_INSUFFICIENT_BALANCE). */
export function getErrorCode(err) {
  return err?.response?.data?.code || (err?.response ? `HTTP_${err.response.status}` : 'NETWORK_ERROR');
}

/** Field errors as { field: message } for react-hook-form setError. */
export function getFieldErrors(err) {
  const list = err?.response?.data?.errors;
  if (!Array.isArray(list)) return {};
  return list.reduce((acc, e) => {
    if (e.field) acc[e.field] = e.message;
    return acc;
  }, {});
}

/** Friendly message for toasts. */
export function getErrorMessage(err, fallback = 'Something went wrong. Please try again.') {
  if (!err) return fallback;
  if (err.response?.data instanceof Blob) return fallback;
  const body = err.response?.data;
  if (body?.message) {
    const first = Array.isArray(body.errors) && body.errors.length ? body.errors[0] : null;
    if (body.code === 'VALIDATION_FAILED' && first?.message) {
      return first.field ? `${body.message}: ${first.message}` : first.message;
    }
    return body.message;
  }
  if (err.code === 'ECONNABORTED') return 'The server took too long to respond. Please try again.';
  if (!err.response) return 'Cannot reach the server. Check your internet connection.';
  if (err.response.status === 403) return 'You do not have permission to do this.';
  if (err.response.status === 404) return 'Not found.';
  if (err.response.status >= 500) return 'Server error. Please try again in a moment.';
  return err.message || fallback;
}

/** Removes empty params so URLs stay clean. */
export function cleanParams(params = {}) {
  const out = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v === undefined || v === null || v === '') return;
    out[k] = v;
  });
  return out;
}

function filenameFromDisposition(header) {
  if (!header) return null;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (star) return decodeURIComponent(star[1]);
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}

/** Downloads a binary response (CSV/XLSX/PDF/image) and saves it in the browser. */
export async function downloadFile(url, params, filename) {
  try {
    const res = await api.get(url, { params: cleanParams(params), responseType: 'blob' });
    const name = filenameFromDisposition(res.headers['content-disposition']) || filename || 'download';
    const blobUrl = URL.createObjectURL(res.data);
    const a = document.createElement('a');
    a.href = blobUrl;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    return name;
  } catch (err) {
    // Error bodies come back as Blob for responseType blob: decode the envelope for a useful message.
    if (err.response?.data instanceof Blob) {
      try {
        const text = await err.response.data.text();
        err.response.data = JSON.parse(text);
      } catch {
        /* keep original */
      }
    }
    throw err;
  }
}

/** Fetches a protected file as an object URL (for <img>/<iframe> previews). Caller revokes it. */
export async function fetchObjectUrl(url, params) {
  const res = await api.get(url, { params: cleanParams(params), responseType: 'blob' });
  return URL.createObjectURL(res.data);
}

/** Builds multipart form data; File/Blob values kept, objects/arrays sent as JSON text fields. */
export function toFormData(values) {
  const fd = new FormData();
  Object.entries(values).forEach(([k, v]) => {
    if (v === undefined || v === null) return;
    if (v instanceof Blob) fd.append(k, v);
    else if (typeof v === 'object') fd.append(k, JSON.stringify(v));
    else fd.append(k, String(v));
  });
  return fd;
}
