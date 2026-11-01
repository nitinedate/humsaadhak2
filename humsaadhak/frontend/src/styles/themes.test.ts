import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { ALL_PALETTES } from '../shared/ui/colors';

/** Parses themes.css into { INDIGO: { '--text': '#1e2340', … }, … }. */
function themes(): Record<string, Record<string, string>> {
  const css = readFileSync('src/styles/themes.css', 'utf8');
  const out: Record<string, Record<string, string>> = {};
  for (const m of css.matchAll(/\[data-theme='(\w+)'\]\s*\{([^}]*)\}/g)) {
    out[m[1]!] = Object.fromEntries([...m[2]!.matchAll(/(--[\w-]+):\s*(#[0-9a-fA-F]{6})/g)].map((v) => [v[1]!, v[2]!.toLowerCase()]));
  }
  return out;
}
const lum = (hex: string) => {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255).map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
};
export const contrast = (a: string, b: string) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x! + 0.05) / (y! + 0.05); };

/** Text/background pairs that must be readable (WCAG AA, 4.5:1). */
const PAIRS: [string, string][] = [
  ['--text', '--page'], ['--text', '--surface'], ['--text-2', '--surface'], ['--muted', '--surface'], ['--muted', '--page'],
  ['--on-accent', '--accent'], ['--accent', '--surface'], ['--accent', '--page'], ['--accent-ink', '--accent-soft'],
  ['--rail-text', '--rail-bg'], ['--rail-text', '--rail-2'], ['--rail-muted', '--rail-bg'], ['--rail-text-strong', '--rail-active'],
  ['--hero-text', '--hero-bg'], ['--hero-muted', '--hero-bg'], ['--hero-chip-text', '--hero-chip'],
  ['--success', '--success-soft'], ['--danger', '--danger-soft'], ['--warn', '--warn-soft'], ['--info', '--info-soft'],
  ['--success', '--surface'], ['--danger', '--surface'],
];

describe('themes', () => {
  const all = themes();

  it('defines the same four themes as Settings', () => {
    expect(Object.keys(all).sort()).toEqual(['HUMSAADHAK', 'INDIGO', 'NIGHT', 'OCEAN']);
  });

  it.each(['INDIGO', 'HUMSAADHAK', 'OCEAN', 'NIGHT'])('%s: every text colour is readable on its background', (name) => {
    const t = all[name]!;
    const failures = PAIRS.filter(([fg, bg]) => contrast(t[fg]!, t[bg]!) < 4.5)
      .map(([fg, bg]) => `${fg} on ${bg} = ${contrast(t[fg]!, t[bg]!).toFixed(2)}`);
    expect(failures).toEqual([]);
  });

  it.each(['INDIGO', 'HUMSAADHAK', 'OCEAN'])('%s: side panel, highlight card and page are light', (name) => {
    const t = all[name]!;
    for (const v of ['--page', '--rail-bg', '--rail-2', '--hero-bg', '--accent-soft']) expect(lum(t[v]!), `${name} ${v}`).toBeGreaterThan(0.75);
  });

  it('Night is a soft slate rather than near-black', () => {
    expect(lum(all.NIGHT!['--page']!)).toBeGreaterThan(0.012);
  });

  it.each(['INDIGO', 'HUMSAADHAK', 'OCEAN', 'NIGHT'] as const)('%s: chart colours are soft but still visible against cards', (name) => {
    const surface = all[name]!['--surface']!;
    const colours = [...Object.values(ALL_PALETTES.PALETTES[name]), ...ALL_PALETTES.SERIES[name]];
    for (const c of colours) {
      const ratio = contrast(c, surface);
      expect(ratio, `${name} ${c}`).toBeGreaterThan(1.3);                      // visible
      if (name !== 'NIGHT') expect(ratio, `${name} ${c}`).toBeLessThan(3.2);    // soft, not harsh
    }
    expect(new Set(ALL_PALETTES.SERIES[name]).size).toBe(4);                     // series never share a colour
  });
});
