import { useState } from 'react';
import { DEMO } from '../../shared/api/client';
import { downloadText, toCsv } from '../../shared/csv';
import { financialYear } from '../dashboards/DashboardPage';
import { cardColumns } from '../../shared/ui/chartLayout';

/** First day of the current Indian financial year, as yyyy-mm-dd. */
export function fyStartIso(d: Date): string {
  const y = d.getMonth() >= 3 ? d.getFullYear() : d.getFullYear() - 1;
  return `${y}-04-01`;
}
const isoToday = () => new Date().toISOString().slice(0, 10);

const REPORTS = [
  { key: 'donations', title: 'Donations', dated: true,
    what: 'Every payment in the period: donor and donor type, cause or project, beneficiary and beneficiary type, amounts and payment reference.' },
  { key: 'organisations', title: 'Organisations', dated: false,
    what: 'All individual and corporate donors, direct beneficiaries (schools) and indirect beneficiaries (NGOs), with verification.' },
  { key: 'needs', title: 'Causes and projects', dated: false,
    what: 'Every cause and project with its beneficiary, target, money raised and what is still needed.' },
] as const;

/** Staff downloads of whole tables. Files are CSV and open directly in Excel. Bank details are never included. */
export function DataExports() {
  const [from, setFrom] = useState(fyStartIso(new Date()));
  const [to, setTo] = useState(isoToday());
  const badRange = to < from;

  async function demoDownload(key: string) {
    const { demoReport } = await import('../../shared/api/demo');
    downloadText(`${key}-${isoToday()}.csv`, toCsv(demoReport(key)));
  }

  return (
    <>
      <section className="card" aria-labelledby="period-h">
        <h2 id="period-h" className="card-title">Period for donations</h2>
        <div className="field-row">
          <div className="field"><label htmlFor="r-from">From</label><input id="r-from" type="date" className="form-control" value={from} max={to} onChange={(e) => setFrom(e.target.value)} /></div>
          <div className="field"><label htmlFor="r-to">To</label><input id="r-to" type="date" className="form-control" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></div>
          <button type="button" className="btn btn-outline-secondary" onClick={() => { setFrom(fyStartIso(new Date())); setTo(isoToday()); }}>{financialYear(new Date())}</button>
        </div>
        {badRange && <span className="small" style={{ color: 'var(--danger)' }}>The end date is before the start date.</span>}
      </section>
      <div className={`row g-3 ${cardColumns(REPORTS.length)}`}>
        {REPORTS.map((r) => {
          const href = `/api/v2/reports/${r.key}.csv${r.dated ? `?from=${from}&to=${to}` : ''}`;
          const disabled = r.dated && badRange;
          return (
            <div className="col" key={r.key}><section className="card h-100" aria-labelledby={`rep-${r.key}`}>
              <h2 id={`rep-${r.key}`} className="card-title">{r.title}</h2>
              <p className="small muted" style={{ margin: 0, lineHeight: 1.5, flex: 1 }}>{r.what}</p>
              {DEMO
                ? <button type="button" className="btn btn-primary" disabled={disabled} onClick={() => demoDownload(r.key)}>Download {r.title.toLowerCase()}</button>
                : <a className="btn btn-primary" href={disabled ? undefined : href} aria-disabled={disabled} download>Download {r.title.toLowerCase()}</a>}
            </section></div>
          );
        })}
      </div>
    </>
  );
}
