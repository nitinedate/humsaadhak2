import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api } from '../shared/api/client';
import { chartPalette } from '../shared/ui/colors';

export type ThemeName = 'INDIGO' | 'HUMSAADHAK' | 'OCEAN' | 'NIGHT';

export const THEMES: { name: ThemeName; label: string; description: string; preview: { rail: string; page: string; accent: string; text: string } }[] = [
  { name: 'INDIGO', label: 'Lavender', description: 'Soft lavender panels with gentle indigo accents. The standard look.', preview: { rail: '#EEF0FA', page: '#F6F7FC', accent: '#5151CC', text: '#1E2340' } },
  { name: 'HUMSAADHAK', label: 'HumSaadhak sand', description: 'Warm sand and soft terracotta, taken from the HumSaadhak logo.', preview: { rail: '#F6EDE4', page: '#FAF7F3', accent: '#AD5A27', text: '#2E241D' } },
  { name: 'OCEAN', label: 'Ocean mint', description: 'Fresh mint and sky tones with a calm teal accent.', preview: { rail: '#E6F3F2', page: '#F3F8F8', accent: '#1B7A72', text: '#15303A' } },
  { name: 'NIGHT', label: 'Night', description: 'Gentle dark slate with pastel accents, for low light.', preview: { rail: '#212739', page: '#1C2131', accent: '#AAB4FF', text: '#E9ECF5' } },
];

const STORAGE_KEY = 'humsaadhak.theme';
const isTheme = (v: unknown): v is ThemeName => THEMES.some((t) => t.name === v);

/** Applies a theme to the whole page; also used before React starts so there is no flash of the wrong theme. */
export function applyTheme(theme: ThemeName) {
  document.documentElement.dataset.theme = theme;
  // Bootstrap's own colour mode: dark controls and icons for the Night theme.
  document.documentElement.dataset.bsTheme = theme === 'NIGHT' ? 'dark' : 'light';
}

export function storedTheme(): ThemeName {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return isTheme(v) ? v : 'INDIGO';
  } catch {
    return 'INDIGO';
  }
}

interface ThemeState {
  theme: ThemeName;
  palette: ReturnType<typeof chartPalette>;
  /** Changes the theme now and saves it to the account (and this browser). */
  choose: (theme: ThemeName) => Promise<void>;
  /** Adopts the theme saved on the account after sign-in, without saving again. */
  adopt: (theme: ThemeName | undefined) => void;
}

const ThemeContext = createContext<ThemeState | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<ThemeName>(storedTheme);

  useEffect(() => {
    applyTheme(theme);
    try { localStorage.setItem(STORAGE_KEY, theme); } catch { /* private mode: theme still applies for this visit */ }
  }, [theme]);

  const choose = useCallback(async (next: ThemeName) => {
    const previous = theme;
    setTheme(next);
    try {
      await api('/me/preferences', { method: 'PUT', json: { theme: next } });
    } catch (e) {
      setTheme(previous);
      throw e;
    }
  }, [theme]);

  const adopt = useCallback((t: ThemeName | undefined) => { if (isTheme(t)) setTheme(t); }, []);
  const value = useMemo(() => ({ theme, palette: chartPalette(theme), choose, adopt }), [theme, choose, adopt]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeState {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme outside ThemeProvider');
  return ctx;
}
