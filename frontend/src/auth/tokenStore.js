import { local, session } from '../lib/storage';

// Access token lives in memory only; refresh token persists so a reload can resume the session.
const REFRESH_KEY = 'cbwa.refreshToken';
const ADMIN_BACKUP_KEY = 'cbwa.adminSessionBackup';

let accessToken = null;
const listeners = new Set();

export const tokenStore = {
  getAccessToken: () => accessToken,
  getRefreshToken: () => local.get(REFRESH_KEY),
  setTokens({ accessToken: a, refreshToken: r }) {
    accessToken = a || null;
    if (r !== undefined) local.set(REFRESH_KEY, r || null);
  },
  clear() {
    accessToken = null;
    local.remove(REFRESH_KEY);
    listeners.forEach((fn) => fn());
  },
  /** Called when the session cannot be recovered (refresh failed). */
  onCleared(fn) {
    listeners.add(fn);
    return () => listeners.delete(fn);
  },

  // Impersonation: keep the admin's own refresh token in sessionStorage (this tab only).
  saveAdminBackup() {
    const r = local.get(REFRESH_KEY);
    if (r) session.set(ADMIN_BACKUP_KEY, r);
  },
  getAdminBackup: () => session.get(ADMIN_BACKUP_KEY),
  clearAdminBackup: () => session.remove(ADMIN_BACKUP_KEY),
};
