import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { BeneficiaryReportType, ReportOptions, ReportRequest, ReportResult } from '../../shared/api/types';
import { buildExcel, buildPdf, exportFileName, formatValue, loadLogo } from '../../shared/reportExport';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import logo from '../../assets/logo.png';

const NEED = 'NEED', PENDING = 'PENDING_NEED';

/** Ticking one of these two clears the other, as in the original Customize report. */
export function toggleColumn(selected: string[], key: string): string[] {
  if (selected.includes(key)) return selected.filter((k) => k !== key);
  const exclusive = key === NEED ? PENDING : key === PENDING ? NEED : null;
  return [...selected.filter((k) => k !== exclusive), key];
}

function saveBytes(name: string, bytes: ArrayBuffer, type: string) {
  const url = URL.createObjectURL(new Blob([bytes], { type }));
  const a = document.createElement('a');
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * The original project's reports for one school or NGO: Donation, Match, Project/Cause and Customize reports,
 * with the same filters (cause or project, financial year, approved start and end dates) and Excel/PDF export.
 */
export function BeneficiaryReports({ type, orgId }: { type: BeneficiaryReportType; orgId: number | null }) {
  const options = useQuery({
    queryKey: ['report-options', orgId],
    queryFn: () => api<ReportOptions>(`/beneficiary-reports/options${orgId ? `?orgId=${orgId}` : ''}`),
  });
  const school = options.data?.organisation.type === 'SCHOOL';
  const needWord = school ? 'Cause' : 'Project';

  const [needId, setNeedId] = useState('');
  const [year, setYear] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [header, setHeader] = useState('');
  const [columns, setColumns] = useState<string[]>([NEED]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [submitted, setSubmitted] = useState<ReportRequest | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  // a new report type or organisation starts from a clean form
  useEffect(() => { setSubmitted(null); setPage(0); setNeedId(''); }, [type, orgId]);

  const pending = type === 'custom' && columns.includes(PENDING);
  const request = (): ReportRequest => ({
    orgId, needId: needId ? Number(needId) : null, year: year || null, approvedFrom: from || null, approvedTo: to || null,
    ...(type === 'custom' ? { columns, header: header.trim() || null } : {}),
  });
  const problem = from && to && to < from ? 'The end date is before the start date.'
    : (from && !to) || (!from && to) ? 'Give both the approved start and end dates.'
    : type === 'custom' && !columns.includes(NEED) && !columns.includes(PENDING) ? `Choose “${needWord}” or “${needWord} (yet to be approved)”.`
    : null;

  const result = useQuery({
    queryKey: ['beneficiary-report', type, submitted, page, size],
    enabled: !!submitted,
    queryFn: () => api<ReportResult>(`/beneficiary-reports/${type}`, { method: 'POST', json: { ...submitted, page, size } }),
  });

  async function exportAs(kind: 'xlsx' | 'pdf') {
    if (!submitted) return;
    setExporting(kind); setExportError(null);
    try {
      const all = await api<ReportResult>(`/beneficiary-reports/${type}`, { method: 'POST', json: { ...submitted, all: true } });
      const logoFile = await loadLogo(logo);
      if (kind === 'xlsx') saveBytes(exportFileName(all, 'xlsx'), await buildExcel(all, logoFile?.png), 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      else saveBytes(exportFileName(all, 'pdf'), await buildPdf(all, logoFile?.dataUrl), 'application/pdf');
    } catch (e) {
      setExportError(e instanceof ApiError ? e.message : 'Could not create the file. Try again.');
    } finally {
      setExporting(null);
    }
  }

  const needs = useMemo(() => (options.data?.needs ?? []).filter((n) => (pending ? !n.approved : n.approved)), [options.data, pending]);
  if (options.isLoading) return <Skeleton height={220} />;
  if (options.error) return <ErrorNote error={options.error} />;
  const o = options.data!;
  const r = result.data;
  const pages = r ? Math.max(1, Math.ceil(r.total / r.size)) : 1;

  return (
    <>
      <form className="card" aria-label="Report filters" onSubmit={(e) => { e.preventDefault(); if (!problem) { setPage(0); setSubmitted(request()); } }}>
        <div className="field-row">
          {type === 'custom' && (
            <div className="field"><label htmlFor="r-header">Header</label>
              <input id="r-header" className="form-control" placeholder={`Customize ${needWord} Report`} value={header} onChange={(e) => setHeader(e.target.value)} /></div>
          )}
          <div className="field"><label htmlFor="r-name">Name</label>
            <input id="r-name" className="form-control" value={`${o.organisation.name} [${o.organisation.refNo}]`} readOnly /></div>
          <div className="field"><label htmlFor="r-need">{needWord}</label>
            <select id="r-need" className="form-select" value={needId} onChange={(e) => setNeedId(e.target.value)}>
              <option value="">All {school ? 'causes' : 'projects'}</option>
              {needs.map((n) => <option key={n.id} value={n.id}>{n.name} ({n.refNo})</option>)}
            </select></div>
        </div>
        <div className="field-row">
          <div className="field"><label htmlFor="r-year">Year</label>
            <select id="r-year" className="form-select" value={year} disabled={pending}
                    onChange={(e) => { setYear(e.target.value); if (e.target.value) { setFrom(''); setTo(''); } }}>
              <option value="">Any</option>
              {o.years.map((y) => <option key={y} value={y}>{y}</option>)}
            </select></div>
          <div className="field"><label htmlFor="r-from">Approved Start Date</label>
            <input id="r-from" type="date" className="form-control" value={from} disabled={pending || !!year} max={to || undefined} onChange={(e) => setFrom(e.target.value)} /></div>
          <div className="field"><label htmlFor="r-to">Approved End Date</label>
            <input id="r-to" type="date" className="form-control" value={to} disabled={pending || !!year} min={from || undefined} onChange={(e) => setTo(e.target.value)} /></div>
        </div>
        {type === 'custom' && (
          <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 8 }}>
            <legend className="label" style={{ fontWeight: 700, marginBottom: 8 }}>Columns</legend>
            <div className="segmented">
              {o.customColumns.map((c) => (
                <button key={c.key} type="button" className="chip-btn" aria-pressed={columns.includes(c.key)}
                        onClick={() => { setColumns((s) => toggleColumn(s, c.key)); if (c.key === PENDING) { setYear(''); setFrom(''); setTo(''); } }}>
                  {c.label}
                </button>
              ))}
            </div>
          </fieldset>
        )}
        {problem && <span className="small" role="alert" style={{ color: 'var(--danger)' }}>{problem}</span>}
        <div className="segmented">
          <button type="submit" className="btn btn-primary" disabled={!!problem}>Show report</button>
          <button type="button" className="btn btn-outline-secondary" onClick={() => { setNeedId(''); setYear(''); setFrom(''); setTo(''); setHeader(''); setColumns([NEED]); setSubmitted(null); }}>Clear</button>
        </div>
      </form>

      {result.isLoading && <Skeleton height={260} />}
      {result.error && <ErrorNote error={result.error} />}
      {r && (
        <section className="card" aria-labelledby="rep-title">
          <div className="page-head" style={{ alignItems: 'flex-start' }}>
            <div>
              <h2 id="rep-title" className="card-title" style={{ fontSize: 18 }}>{r.title}</h2>
              <div className="small muted">{r.entityLine}</div>
              <div className="small muted">{r.filterLine} · {r.total.toLocaleString('en-IN')} {r.total === 1 ? 'row' : 'rows'}</div>
            </div>
            <div className="segmented">
              <button type="button" className="btn btn-outline-secondary btn-sm" disabled={!r.total || !!exporting} onClick={() => exportAs('xlsx')}>{exporting === 'xlsx' ? 'Preparing…' : 'Export as Excel'}</button>
              <button type="button" className="btn btn-outline-secondary btn-sm" disabled={!r.total || !!exporting} onClick={() => exportAs('pdf')}>{exporting === 'pdf' ? 'Preparing…' : 'Export as PDF'}</button>
            </div>
          </div>
          {exportError && <div className="alert alert-danger" role="alert">{exportError}</div>}
          {r.total === 0 ? <p className="empty">No rows for these filters.</p> : (
            <>
              <div className="table-responsive table-wrap">
                <table className="table table-hover align-middle mb-0">
                  <thead><tr>{r.columns.map((c) => <th key={c.key} style={{ textAlign: c.type === 'money' ? 'right' : 'left' }}>{c.label}</th>)}</tr></thead>
                  <tbody>
                    {r.rows.map((row, i) => (
                      <tr key={i}>
                        {r.columns.map((c) => (
                          <td key={c.key} className={c.type === 'money' ? 'num' : undefined} style={{ textAlign: c.type === 'money' ? 'right' : 'left', whiteSpace: c.type === 'date' ? 'nowrap' : undefined }}>
                            {c.key === 'docStatus' || c.key === 'matchedFlag'
                              ? <span className={`pill ${row[c.key] === 'Approved' || row[c.key] === 'Yes' ? 'approved' : 'neutral'}`}>{formatValue(row[c.key], c.type)}</span>
                              : formatValue(row[c.key], c.type)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <nav className="field-row" aria-label="Pages" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
                <div className="field" style={{ flex: '0 0 140px' }}>
                  <label htmlFor="r-size" className="small">Rows per page</label>
                  <select id="r-size" className="form-select" value={size} onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}>
                    {[10, 25, 50, 100].map((n) => <option key={n} value={n}>{n}</option>)}
                  </select>
                </div>
                <div className="segmented" style={{ alignItems: 'center' }}>
                  <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
                  <span className="small">Page {page + 1} of {pages}</span>
                  <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button>
                </div>
              </nav>
            </>
          )}
        </section>
      )}
    </>
  );
}

