import { Monitor, Moon, Sun } from 'lucide-react';
import { useTheme } from '../../hooks/useTheme';
import IconButton from '../ui/IconButton';

const NEXT = { LIGHT: 'DARK', DARK: 'SYSTEM', SYSTEM: 'LIGHT' };
const ICON = { LIGHT: Sun, DARK: Moon, SYSTEM: Monitor };
const LABEL = { LIGHT: 'Light theme (tap for dark)', DARK: 'Dark theme (tap for system)', SYSTEM: 'System theme (tap for light)' };

export default function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  return <IconButton icon={ICON[theme]} label={LABEL[theme]} onClick={() => setTheme(NEXT[theme])} />;
}
