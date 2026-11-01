import type { ThemeName } from '../../app/theme';

/**
 * Chart colours per theme: soft, light tones that are easy on the eyes. The API sends colour keys; each theme maps them.
 * Colour is never the only way to read a chart: every legend row and tooltip shows the number, and segments are
 * separated by gaps, so the softer tones stay readable.
 */
const PALETTES: Record<ThemeName, Record<string, string>> = {
  INDIGO: { rust: '#F0A48A', 'rust-light': '#F8CDBD', indigo: '#8C8CEB', 'indigo-light': '#C9C9F4', grey: '#D8DCE8', green: '#86CFA6', amber: '#F4C977', teal: '#7CCDC6' },
  HUMSAADHAK: { rust: '#E3956A', 'rust-light': '#F3CCB0', indigo: '#86C1B8', 'indigo-light': '#C7E3DE', grey: '#E2D8CE', green: '#98C99A', amber: '#E5C489', teal: '#86C1B8' },
  OCEAN: { rust: '#F2A595', 'rust-light': '#F9D0C7', indigo: '#79C1D6', 'indigo-light': '#C2E4EE', grey: '#D5E2E6', green: '#8BCFA5', amber: '#F1CF86', teal: '#74C8B6' },
  NIGHT: { rust: '#F2B09A', 'rust-light': '#8E6C60', indigo: '#AAB4FF', 'indigo-light': '#626C9E', grey: '#4A5470', green: '#9ADBB7', amber: '#F2D38E', teal: '#8FD8D0' },
};
const SERIES: Record<ThemeName, string[]> = {
  INDIGO: ['#8C8CEB', '#F0A48A', '#7CCDC6', '#F4C977'],
  HUMSAADHAK: ['#E3956A', '#86C1B8', '#D9B77E', '#C9A0D0'],
  OCEAN: ['#74C8B6', '#F2A595', '#79C1D6', '#F1CF86'],
  NIGHT: ['#AAB4FF', '#F2B09A', '#8FD8D0', '#F2D38E'],
};
const MUTED: Record<ThemeName, string> = { INDIGO: '#E4E7F1', HUMSAADHAK: '#EDE3D8', OCEAN: '#DCE8EB', NIGHT: '#3F4862' };
const AXIS: Record<ThemeName, { tick: string; grid: string; label: string }> = {
  INDIGO: { tick: '#636A88', grid: '#F0F2F8', label: '#1E2340' },
  HUMSAADHAK: { tick: '#75665B', grid: '#F5EFE8', label: '#2E241D' },
  OCEAN: { tick: '#587178', grid: '#EBF4F4', label: '#15303A' },
  NIGHT: { tick: '#A2AAC2', grid: '#2D354B', label: '#E9ECF5' },
};

export function chartPalette(theme: ThemeName) {
  return { colors: PALETTES[theme], series: SERIES[theme], muted: MUTED[theme], axis: AXIS[theme] };
}

/** Exposed for the contrast tests. */
export const ALL_PALETTES = { PALETTES, SERIES, MUTED };
