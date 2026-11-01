import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { DetailDialog, type DrillTarget } from '../../shared/ui/DetailDialog';
import { barSpans, cardColumns, donutColumns } from '../../shared/ui/chartLayout';
import { useAuth } from '../../app/auth';
import { api } from '../../shared/api/client';
import { ROLE_LABEL } from '../../shared/format';
import { dashboardRows, downloadText, toCsv } from '../../shared/csv';
import type { DashboardDto } from '../../shared/api/types';
import { BarCard } from '../../shared/ui/BarCard';
import { DonutCard } from '../../shared/ui/DonutCard';
import { KpiCard } from '../../shared/ui/Kpi';
import { ErrorNote, Skeleton } from '../../shared/ui/States';

/** Indian financial year label: 24 Oct 2026 -> "Financial year 2026–27". */
export function financialYear(d: Date): string {
  const start = d.getMonth() >= 3 ? d.getFullYear() : d.getFullYear() - 1;
  return `Financial year ${start}–${String((start + 1) % 100).padStart(2, '0')}`;
}

/** One page for every role: the server decides which dashboard the caller gets. */
export default function DashboardPage() {
  const { me } = useAuth();
  const { data, isLoading, error } = useQuery({ queryKey: ['dashboard', me?.id], queryFn: () => api<DashboardDto>('/dashboards/me') });
  const heading = `${me ? `${me.roleName ?? ROLE_LABEL[me.role]} · ` : ''}${financialYear(new Date())}`;
  const [target, setTarget] = useState<DrillTarget | null>(null);

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <span className="eyebrow">{heading}</span>
          <h1 className="title">{data?.title ?? 'Dashboard'}</h1>
        </div>
        {data && (
          <button type="button" className="btn btn-outline-secondary"
                  onClick={() => downloadText(`${data.title.toLowerCase().replace(/\W+/g, '-')}-${new Date().toISOString().slice(0, 10)}.csv`, toCsv(dashboardRows(data, heading)))}>
            Download as CSV
          </button>
        )}
      </div>
      {isLoading && <><Skeleton height={110} /><div className="dash-grid cols-2"><Skeleton height={280} /><Skeleton height={280} /></div></>}
      {error && <ErrorNote error={error} />}
      {data && (
        <>
          <div className={`row g-3 ${cardColumns(data.kpis.length)}`}>
            {data.kpis.map((k, i) => <div className="col" key={k.label}><KpiCard kpi={k} dark={i === 0} /></div>)}
          </div>
          {data.donuts.length > 0 && (
            <div className={`dash-grid cols-${donutColumns(data.donuts.length)}`}>
              {data.donuts.map((d) => <DonutCard key={d.title} donut={d} onDrill={(s) => setTarget({ chart: d.title, label: s.label, drill: s.drill! })} />)}
            </div>
          )}
          {data.bars.length > 0 && (
            <div className="dash-grid cols-2">
              {data.bars.map((c, n) => (
                <BarCard key={c.title} chart={c} className={barSpans(data.bars)[n] === 'full' ? 'span-full' : undefined} onDrill={(b, i) => setTarget({
                  chart: c.title, label: c.series.length > 1 ? `${b.label}: ${c.series[i]}` : b.label, drill: b.drills![i]!,
                })} />
              ))}
            </div>
          )}
        </>
      )}
      {target && <DetailDialog target={target} onClose={() => setTarget(null)} />}
      {data && <p className="small muted" style={{ margin: 0 }}>Select any part of a chart to see the records behind it.</p>}
    </div>
  );
}
