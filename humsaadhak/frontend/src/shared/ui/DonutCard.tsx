import { useState } from 'react';
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts';
import type { DonutDto, Slice } from '../api/types';
import { inr } from '../format';
import { useTheme } from '../../app/theme';

/**
 * Donut with an accessible legend: hovering or tabbing to a legend row highlights its slice and shows its value in the centre;
 * clicking a slice or legend row opens the records behind it (onDrill), as in the original project.
 */
export function DonutCard({ donut, onDrill }: { donut: DonutDto; onDrill?: (slice: Slice) => void }) {
  const [active, setActive] = useState<number | null>(null);
  const CHART_COLORS = useTheme().palette.colors;
  const total = donut.slices.reduce((a, s) => a + s.value, 0);
  const money = total >= 10000;
  const fmt = (v: number) => (money ? inr(v) : v.toLocaleString('en-IN'));
  const cur = active == null ? null : donut.slices[active];

  return (
    <section className="card donut-card" aria-label={donut.title}>
      <h2 className="card-title">{donut.title}</h2>
      <div className="donut" onMouseLeave={() => setActive(null)}>
        <div className="donut-chart">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={donut.slices} dataKey="value" nameKey="label" innerRadius={56} outerRadius={84} paddingAngle={2}
                   stroke="none" startAngle={90} endAngle={-270} isAnimationActive={false}
                   onMouseEnter={(_, i) => setActive(i)} onClick={(_, i) => { const s = donut.slices[i]; if (s?.drill && onDrill) onDrill(s); }}>
                {donut.slices.map((s, i) => (
                  <Cell key={s.label} fill={CHART_COLORS[s.colorKey] ?? 'var(--line)'} className={s.drill && onDrill ? 'clickable' : undefined}
                        opacity={active == null || active === i ? 1 : 0.25} />
                ))}
              </Pie>
            </PieChart>
          </ResponsiveContainer>
          <div className="donut-center" aria-live="polite">
            <strong>{cur ? fmt(cur.value) : fmt(total)}</strong>
            <span>{cur ? cur.label : donut.totalLabel}</span>
          </div>
        </div>
        <div className="legend">
          {donut.slices.map((s, i) => (
            <button key={s.label} type="button" aria-pressed={active === i} aria-haspopup={s.drill && onDrill ? 'dialog' : undefined}
                    title={s.drill && onDrill ? `Show the records: ${s.label}` : undefined}
                    onMouseEnter={() => setActive(i)} onFocus={() => setActive(i)}
                    onClick={() => (s.drill && onDrill ? onDrill(s) : setActive(i))}>
              <span className="swatch" style={{ background: CHART_COLORS[s.colorKey] ?? 'var(--line)' }} aria-hidden="true" />
              <span className="lbl">{s.label}</span>
              <span className="val">{fmt(s.value)}</span>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
