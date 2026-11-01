import type { Kpi } from '../api/types';

export function KpiCard({ kpi, dark = false }: { kpi: Kpi; dark?: boolean }) {
  return (
    <div className={`kpi${dark ? ' dark' : ''}`}>
      <span className="kpi-label">{kpi.label}</span>
      <span className="kpi-value">{kpi.value}</span>
      {kpi.note && <span className="kpi-note">{kpi.note}</span>}
    </div>
  );
}
