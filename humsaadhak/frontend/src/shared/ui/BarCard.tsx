import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { BarChartDto, BarPoint } from '../api/types';
import { inr } from '../format';
import { useTheme } from '../../app/theme';
import { chartHeight, isHorizontal, nameAxisWidth, shorten, tickText } from './chartLayout';

/** A category name on a horizontal chart, shortened to fit; the full name shows on hover. */
function NameTick({ x, y, payload, fill, maxChars }: { x?: number; y?: number; payload?: { value: string }; fill: string; maxChars: number }) {
  const full = String(payload?.value ?? '');
  return (
    <g transform={`translate(${x ?? 0},${y ?? 0})`}>
      <title>{full}</title>
      <text x={-8} y={0} dy={4} textAnchor="end" fontSize={12} fill={fill}>{shorten(full, maxChars)}</text>
    </g>
  );
}

/**
 * Upright columns for time periods and short names; horizontal bars when names are long (chartLayout.isHorizontal).
 * Rupee axes read in Indian units (₹4.5 L); a "Target" or "Pledged" series is drawn muted so real money stands out.
 */
export function BarCard({ chart, onDrill, className }: { chart: BarChartDto; onDrill?: (bar: BarPoint, series: number) => void; className?: string }) {
  const { series: SERIES_COLORS, muted: MUTED_SERIES, axis } = useTheme().palette;
  const rows = chart.bars.map((b) => Object.fromEntries([['label', b.label], ...chart.series.map((s, i) => [s, b.values[i] ?? 0])]));
  const horizontal = isHorizontal(chart);
  const money = chart.unit === '₹';
  const fmt = (v: number) => (money ? inr(v) : chart.unit === '₹ lakh' ? `₹${v.toFixed(1)} L` : v.toLocaleString('en-IN'));
  const color = (s: string, i: number) => (/target|pledged/i.test(s) ? MUTED_SERIES : SERIES_COLORS[i % SERIES_COLORS.length]);
  const clickable = !!onDrill && chart.bars.some((b) => b.drills?.some(Boolean));
  const nameWidth = nameAxisWidth(chart);
  const tick = { fontSize: 12, fill: axis.tick };

  return (
    <section className={`card chart-card${className ? ` ${className}` : ''}`} aria-label={chart.title}>
      <div className="chart-head">
        <h2 className="card-title">{chart.title}</h2>
        {chart.unit !== '₹' && chart.unit !== 'count' && <span className="small muted">{chart.unit}</span>}
      </div>
      <div className="chart-body" style={{ height: chartHeight(chart) }}>
        <ResponsiveContainer>
          <BarChart data={rows} layout={horizontal ? 'vertical' : 'horizontal'} barGap={4} barCategoryGap={horizontal ? '22%' : '28%'}
                    margin={{ top: 6, right: 18, bottom: 0, left: horizontal ? 4 : 0 }}>
            <CartesianGrid stroke={axis.grid} vertical={horizontal} horizontal={!horizontal} />
            {horizontal ? (
              <>
                <XAxis type="number" tick={tick} tickFormatter={(v: number) => tickText(chart.unit, v)} axisLine={false} tickLine={false} />
                <YAxis type="category" dataKey="label" width={nameWidth} axisLine={false} tickLine={false} interval={0}
                       tick={(p: object) => <NameTick {...p} fill={axis.label} maxChars={Math.floor(nameWidth / 6.6)} />} />
              </>
            ) : (
              <>
                <XAxis dataKey="label" tick={tick} axisLine={{ stroke: axis.grid }} tickLine={false} interval={0} />
                <YAxis tick={tick} width={money ? 60 : 44} axisLine={false} tickLine={false} allowDecimals={chart.unit !== 'count'}
                       tickFormatter={(v: number) => tickText(chart.unit, v)} />
              </>
            )}
            <Tooltip formatter={(v) => fmt(Number(v))} cursor={{ fill: axis.grid }}
                     contentStyle={{ background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 10, color: 'var(--text)' }} />
            {chart.series.length > 1 && <Legend iconType="circle" iconSize={9} wrapperStyle={{ fontSize: 12, paddingTop: 10 }} />}
            {chart.series.map((s, i) => (
              <Bar key={s} dataKey={s} fill={color(s, i)} radius={horizontal ? [0, 6, 6, 0] : [6, 6, 0, 0]} maxBarSize={horizontal ? 22 : 34}
                   isAnimationActive={false} className={clickable ? 'clickable' : undefined}
                   onClick={(_: unknown, index: number) => { const b = chart.bars[index]; if (b?.drills?.[i] && onDrill) onDrill(b, i); }} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      {clickable && (
        <div className="drill-keys" aria-label={`Show the records behind ${chart.title}`}>
          {chart.bars.flatMap((b) => chart.series.map((s, i) => b.drills?.[i] ? (
            <button key={`${b.label}-${s}`} type="button" className="btn btn-outline-secondary btn-sm" aria-haspopup="dialog" onClick={() => onDrill!(b, i)}>
              {chart.series.length > 1 ? `${b.label}: ${s}` : b.label}
            </button>
          ) : null))}
        </div>
      )}
    </section>
  );
}
