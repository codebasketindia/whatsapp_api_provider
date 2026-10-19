import { createContext, useCallback, useEffect, useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { refreshSession } from '../api/axios';
import * as authApi from '../api/endpoints/auth';
import * as meApi from '../api/endpoints/me';
import * as partnersApi from '../api/endpoints/partners';
import { tokenStore } from './tokenStore';
import { hasPermission as checkPermission } from './permissions';

export const AuthContext = createContext(null);

export function homePathFor(user) {
  if (!user) return '/login';
  if (user.mustChangePassword) return '/change-password';
  return user.userType === 'ADMIN' ? '/admin' : '/partner';
}

export function AuthProvider({ children }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading'); // loading | authenticated | anonymous

  const loadMe = useCallback(async () => {
    const me = await meApi.getMe();
    setUser(me);
    setStatus('authenticated');
    return me;
  }, []);

  // Bootstrap: a stored refresh token silently restores the session after reload.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (!tokenStore.getRefreshToken()) {
        setStatus('anonymous');
        return;
      }
      try {
        const data = await refreshSession();
        if (cancelled) return;
        if (data?.user) {
          setUser(data.user);
          setStatus('authenticated');
        } else {
          await loadMe();
        }
      } catch {
        if (!cancelled) {
          tokenStore.clear();
          setStatus('anonymous');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [loadMe]);

  useEffect(
    () =>
      tokenStore.onCleared(() => {
        setUser(null);
        setStatus('anonymous');
      }),
    [],
  );

  /** Stores tokens from a TokenDto (login, 2FA, refresh, login-as). */
  const acceptTokens = useCallback(
    (tokenDto) => {
      tokenStore.setTokens({ accessToken: tokenDto.accessToken, refreshToken: tokenDto.refreshToken });
      queryClient.clear();
      setUser(tokenDto.user);
      setStatus('authenticated');
      return tokenDto.user;
    },
    [queryClient],
  );

  /** Step 1. Returns { user } or { twoFactorRequired, challengeId, maskedEmail }. */
  const login = useCallback(
    async (email, password) => {
      const data = await authApi.login({ email, password });
      if (data?.twoFactorRequired) return data;
      return { user: acceptTokens(data) };
    },
    [acceptTokens],
  );

  const verifyTwoFactor = useCallback(
    async (challengeId, otp) => {
      const data = await authApi.loginTwoFactor({ challengeId, otp });
      return acceptTokens(data);
    },
    [acceptTokens],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    try {
      if (refreshToken) await authApi.logout(refreshToken);
    } catch {
      /* session ends locally even if the server call fails */
    }
    tokenStore.clearAdminBackup();
    tokenStore.clear();
    queryClient.clear();
  }, [queryClient]);

  /** Admin → partner impersonation; admin's refresh token is kept for this tab only. */
  const loginAsPartner = useCallback(
    async (partnerId, reason) => {
      const data = await partnersApi.loginAsPartner(partnerId, reason);
      tokenStore.saveAdminBackup();
      return acceptTokens(data);
    },
    [acceptTokens],
  );

  const exitImpersonation = useCallback(async () => {
    const backup = tokenStore.getAdminBackup();
    const partnerRefresh = tokenStore.getRefreshToken();
    try {
      if (partnerRefresh) await authApi.logout(partnerRefresh);
    } catch {
      /* ignore */
    }
    tokenStore.clearAdminBackup();
    if (!backup) {
      tokenStore.clear();
      return null;
    }
    tokenStore.setTokens({ accessToken: null, refreshToken: backup });
    try {
      const data = await refreshSession();
      queryClient.clear();
      setUser(data.user);
      setStatus('authenticated');
      return data.user;
    } catch {
      tokenStore.clear();
      return null;
    }
  }, [queryClient]);

  const hasPermission = useCallback((perm) => checkPermission(user, perm), [user]);

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      isAdmin: user?.userType === 'ADMIN',
      isPartner: user?.userType === 'PARTNER',
      isImpersonating: Boolean(user?.impersonatedBy),
      login,
      verifyTwoFactor,
      logout,
      loginAsPartner,
      exitImpersonation,
      refreshUser: loadMe,
      setUser,
      hasPermission,
      acceptTokens,
    }),
    [user, status, login, verifyTwoFactor, logout, loginAsPartner, exitImpersonation, loadMe, hasPermission, acceptTokens],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
