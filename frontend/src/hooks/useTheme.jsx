import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { local } from '../lib/storage';

const KEY = 'cbwa.theme';
const ThemeContext = createContext(null);

function prefersDark() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches;
}

function apply(theme) {
  const dark = theme === 'DARK' || (theme === 'SYSTEM' && prefersDark());
  document.documentElement.classList.toggle('dark', dark);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', dark ? '#0b1014' : '#075E54');
  return dark;
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => {
    const t = local.get(KEY);
    return t === 'LIGHT' || t === 'DARK' || t === 'SYSTEM' ? t : 'SYSTEM';
  });
  const [isDark, setIsDark] = useState(() => (typeof document !== 'undefined' ? document.documentElement.classList.contains('dark') : false));

  useEffect(() => {
    setIsDark(apply(theme));
    if (theme !== 'SYSTEM' || !window.matchMedia) return undefined;
    const mql = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = () => setIsDark(apply('SYSTEM'));
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, [theme]);

  const setTheme = useCallback((t) => {
    local.set(KEY, t);
    setThemeState(t);
  }, []);

  const toggle = useCallback(() => setTheme(isDark ? 'LIGHT' : 'DARK'), [isDark, setTheme]);

  const value = useMemo(() => ({ theme, isDark, setTheme, toggle }), [theme, isDark, setTheme, toggle]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme must be used inside ThemeProvider');
  return ctx;
}
