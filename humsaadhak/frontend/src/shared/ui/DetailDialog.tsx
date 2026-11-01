import { Fragment, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { DetailColumn, DetailTable, DetailValues, Drill } from '../api/types';
import { downloadText, toCsv } from '../csv';
import { inr } from '../format';
import { Dialog } from './Dialog';
import { ErrorNote, Skeleton } from './States';

export interface DrillTarget { chart: string; label: string; drill: Drill }

/** "?set=DONATIONS&donorType=DONOR_CORPORATE&from=2026-04-01" */
export function detailPath(d: Drill): string {
  const q = new URLSearchParams({ set: d.set, ...d.filters });
  return `/dashboards/details?${q.toString()}`;
}

/** How one value is shown, by column type. */
export function display(value: DetailValues[string], type: DetailColumn['type']): string {
  if (value == null || value === '') return '—';
  switch (type) {
    case 'money': return inr(Number(value));
    case 'date': return new Date(String(value)).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
    case 'yesno': return value === true || value === 'true' ? 'Yes' : 'No';
    case 'count': return Number(value).toLocaleString('en-IN');
    default: return String(value);
  }
}

/** The records behind a chart segment, in a dialog; rows open into their related records, as in the original project. */
export function DetailDialog({ target, onClose }: { target: DrillTarget; onClose: () => void }) {
  const { data, isLoading, error } = useQuery({ queryKey: ['details', target.drill], queryFn: () => api<DetailTable>(detailPath(target.drill)) });
  const [open, setOpen] = useState<Set<number>>(new Set());
  const toggle = (i: number) => setOpen((s) => { const n = new Set(s); if (n.has(i)) n.delete(i); else n.add(i); return n; });
  const expandable = !!data?.rows.some((r) => r.children);

  const download = () => {
    if (!data) return;
    const rows = [data.columns.map((c) => c.label), ...data.rows.map((r) => data.columns.map((c) => display(r.values[c.key], c.type)))];
    downloadText(`${target.label.toLowerCase().replace(/\W+/g, '-')}.csv`, toCsv(rows));
  };

  return (
    <Dialog title={target.label} subtitle={`${target.chart}${data ? ` · ${data.total.toLocaleString('en-IN')} ${data.total === 1 ? 'record' : 'records'}` : ''}`}
            onClose={onClose}
            actions={data && data.rows.length > 0 ? <button type="button" className="btn btn-outline-secondary btn-sm" onClick={download}>Download as CSV</button> : null}>
      {isLoading && <Skeleton height={220} />}
      {error && <ErrorNote error={error} />}
      {data && data.rows.length === 0 && <p className="empty">Nothing to show here yet.</p>}
      {data && data.rows.length > 0 && (
        <>
          {data.truncated && <div className="alert alert-warning">Showing the first {data.rows.length} of {data.total.toLocaleString('en-IN')}. Use Reports for the full list.</div>}
          <div className="table-responsive table-wrap">
            <table className="table table-hover align-middle mb-0">
              <thead>
                <tr>
                  {expandable && <th><span className="visually-hidden">Open</span></th>}
                  {data.columns.map((c) => <th key={c.key} style={{ textAlign: c.type === 'money' || c.type === 'count' ? 'right' : 'left' }}>{c.label}</th>)}
                </tr>
              </thead>
              <tbody>
                {data.rows.map((r, i) => (
                  <Fragment key={String(r.values.id ?? i)}>
                    <tr aria-selected={open.has(i)}>
                      {expandable && (
                        <td style={{ width: 44 }}>
                          {r.children && (
                            <button type="button" className="expander" aria-expanded={open.has(i)} onClick={() => toggle(i)}
                                    aria-label={`${open.has(i) ? 'Hide' : 'Show'} ${r.children.title.toLowerCase()} for ${r.values.name ?? r.values.ref ?? 'this row'}`}>
                              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" aria-hidden="true"
                                   style={{ transform: open.has(i) ? 'rotate(90deg)' : 'none', transition: 'transform 0.15s' }}><path d="M9 6l6 6-6 6" /></svg>
                            </button>
                          )}
                        </td>
                      )}
                      {data.columns.map((c) => (
                        <td key={c.key} className={c.type === 'money' || c.type === 'count' ? 'num' : undefined}
                            style={{ textAlign: c.type === 'money' || c.type === 'count' ? 'right' : 'left', whiteSpace: c.type === 'date' ? 'nowrap' : undefined }}>
                          {display(r.values[c.key], c.type)}
                        </td>
                      ))}
                    </tr>
                    {r.children && open.has(i) && (
                      <tr className="child-row">
                        <td colSpan={data.columns.length + 1}>
                          <div className="child-table">
                            <strong className="small">{r.children.title} ({r.children.rows.length})</strong>
                            {r.children.rows.length === 0 ? <span className="small muted">None yet.</span> : (
                              <table className="table table-hover align-middle mb-0">
                                <thead><tr>{r.children.columns.map((c) => <th key={c.key}>{c.label}</th>)}</tr></thead>
                                <tbody>
                                  {r.children.rows.map((cr, j) => (
                                    <tr key={j}>{r.children!.columns.map((c) => <td key={c.key} className={c.type === 'money' ? 'num' : undefined}>{display(cr[c.key], c.type)}</td>)}</tr>
                                  ))}
                                </tbody>
                              </table>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </Dialog>
  );
}
