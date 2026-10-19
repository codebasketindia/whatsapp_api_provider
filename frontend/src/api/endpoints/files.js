import { API_BASE_URL, downloadFile, fetchObjectUrl } from '../axios';

// A16 — files are protected, so they are fetched with the bearer token.
export const filePath = (uid) => `/files/${uid}`;
export const downloadStoredFile = (uid, filename) => downloadFile(filePath(uid), undefined, filename);
export const fileObjectUrl = (uid) => fetchObjectUrl(filePath(uid));

/** Accepts either a file uid or a URL returned by the API ("/api/files/xyz" or absolute). */
export function toApiPath(urlOrUid) {
  if (!urlOrUid) return null;
  if (/^https?:\/\//.test(urlOrUid)) return urlOrUid;
  if (urlOrUid.startsWith(API_BASE_URL)) return urlOrUid.slice(API_BASE_URL.length);
  if (urlOrUid.startsWith('/')) return urlOrUid;
  return filePath(urlOrUid);
}
