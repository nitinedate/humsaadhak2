import type { BarChartDto } from '../api/types';

/**
 * Dashboard layout rules, shared by every role's dashboard (pure, unit-tested):
 *  - donuts sit side by side in as many columns as there are donuts (up to 3; 4 make a 2 x 2 grid), so no card is left alone;
 *  - bar charts pair up two per row; a long time series takes a full row; a chart left without a partner takes the full row;
 *  - bars run upright for time periods and short labels, and horizontally only when category names are long.
 */
export type Span = 'full' | 'half';

export function donutColumns(count: number): 1 | 2 | 3 {
  if (count <= 1) return 1;
  if (count === 2 || count === 4) return 2;
  return 3;
}

const LONG_LABEL = 18;

/** Horizontal bars only when names would not fit under upright columns. */
/**
 * Bootstrap column classes for a row of equal cards (headline figures, export cards) so no card is ever left alone on a
 * row: one per row on phones; two per row on tablets when the count is even; then all in one row on wide screens.
 */
export function cardColumns(count: number): string {
  if (count <= 1) return 'row-cols-1';
  if (count === 2) return 'row-cols-1 row-cols-sm-2';
  if (count === 3) return 'row-cols-1 row-cols-md-3';
  if (count === 4) return 'row-cols-1 row-cols-sm-2 row-cols-xl-4';
  return 'row-cols-1 row-cols-sm-2 row-cols-lg-3';
}

export function isHorizontal(chart: BarChartDto): boolean {
  return chart.bars.some((b) => b.label.length > LONG_LABEL);
}

/** A time series with many periods (months) reads best across the full width. */
export function isWide(chart: BarChartDto): boolean {
  return !isHorizontal(chart) && chart.bars.length > 6;
}

export function barSpans(charts: BarChartDto[]): Span[] {
  const spans: Span[] = charts.map((c) => (isWide(c) ? 'full' : 'half'));
  let waiting: number | null = null;            // a half-width chart still looking for a partner
  spans.forEach((s, i) => {
    if (s === 'full') {
      if (waiting !== null) spans[waiting] = 'full';
      waiting = null;
    } else if (waiting === null) {
      waiting = i;
    } else {
      waiting = null;
    }
  });
  if (waiting !== null) spans[waiting] = 'full';
  return spans;
}

/** ₹ in Indian units for axis ticks: ₹850, ₹45k, ₹4.5 L, ₹1.2 Cr. */
export function compactRupees(v: number): string {
  const a = Math.abs(v), sign = v < 0 ? '-' : '';
  const trim = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(1).replace(/\.0$/, ''));
  if (a >= 1e7) return `${sign}₹${trim(a / 1e7)} Cr`;
  if (a >= 1e5) return `${sign}₹${trim(a / 1e5)} L`;
  if (a >= 1e3) return `${sign}₹${trim(a / 1e3)}k`;
  return `${sign}₹${trim(a)}`;
}

/** Axis tick text for a chart's unit. */
export function tickText(unit: string, v: number): string {
  if (unit === '₹') return compactRupees(v);
  if (unit === '₹ lakh') return `₹${Number.isInteger(v) ? v : v.toFixed(1)} L`;
  return Number.isInteger(v) ? v.toLocaleString('en-IN') : v.toFixed(1);
}

/** Height that fits the bars (and the legend) without empty bands. */
export function chartHeight(chart: BarChartDto): number {
  const legend = chart.series.length > 1 ? 36 : 8;
  if (!isHorizontal(chart)) return 260 + legend;
  return Math.max(180, chart.bars.length * (chart.series.length > 1 ? 54 : 38)) + legend;
}

/** Width of the name column for horizontal bars: enough for the names, never more than 200px. */
export function nameAxisWidth(chart: BarChartDto): number {
  const longest = Math.max(...chart.bars.map((b) => b.label.length), 8);
  return Math.min(200, Math.max(96, Math.round(longest * 6.6)));
}

/** "Digital literacy lab for adolescent girls" -> "Digital literacy lab for a…" when it does not fit. */
export function shorten(label: string, maxChars: number): string {
  return label.length <= maxChars ? label : `${label.slice(0, Math.max(1, maxChars - 1)).trimEnd()}…`;
}
