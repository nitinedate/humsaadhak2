import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api, DEMO } from '../../shared/api/client';
import type { AuditEntry, AuditTargetType, PageResponse } from '../../shared/api/types';
import { downloadText, toCsv } from '../../shared/csv';
import { targetText, when, who } from '../../shared/ui/AuditTrail';
import { ErrorNote, Skeleton } from '../../shared/ui/States';

/** The areas of the app, as the audit log groups actions (the part of an action code before the dot). */
const AREAS: [string, string][] = [
  ['auth', 'Sign-in and passwords'], ['preferences', 'Personal settings'], ['user', 'Users'], ['role', 'Roles'], ['permission', 'Permissions'],
  ['organisation', 'Organisation profiles'], ['purpose', 'Purposes'], ['need', 'Causes and projects'], ['approval', 'Approvals'],
  ['document', 'Documents'], ['share', 'Document requests'], ['match', 'Matching'], ['donation', 'Donations'], ['import', 'Bulk import'],
  ['sector', 'Sectors'], ['document-type', 'Document types'], ['report', 'Reports and exports'], ['audit', 'Audit log'],
];
const TARGETS: AuditTargetType[] = ['USER', 'ORGANISATION', 'PURPOSE', 'NEED', 'DOCUMENT', 'SHARE_REQUEST', 'DONATION', 'MATCH_RUN', 'IMPORT_JOB',
  'ROLE', 'PERMISSION', 'SECTOR', 'DOCUMENT_TYPE'];
const OUTCOMES = { SUCCESS: ['Done', 'approved'], DENIED: ['Refused', 'pending'], FAILED: ['Failed', 'rejected'] } as const;

interface Filters { from: string; to: string; actor: string; category: string; outcome: string; targetType: string; targetId: string }
const EMPTY: Filters = { from: '', to: '', actor: '', category: '', outcome: '', targetType: '', targetId: '' };
const query = (f: Filters) => Object.entries(f).filter(([, v]) => v.trim()).map(([k, v]) => `${k}=${encodeURIComponent(v.trim())}`).join('&');

/** Every recorded action, refused and failed attempts included, for people with audit.view. */
export default function AuditLogPage() {
  const [draft, setDraft] = useState<Filters>(EMPTY);
  const [filters, setFilters] = useState<Filters>(EMPTY);
  const [page, setPage] = useState(0);
  const q = query(filters);
  const log = useQuery({
    queryKey: ['audit', 'events', q, page],
    queryFn: () => api<PageResponse<AuditEntry>>(`/audit/events?${q}${q ? '&' : ''}page=${page}&size=50`),
    placeholderData: keepPreviousData,
  });
  const set = (k: keyof Filters) => (e: { target: { value: string } }) => setDraft({ ...draft, [k]: e.target.value });

  async function demoCsv() {
    const all = await api<PageResponse<AuditEntry>>(`/audit/events?${q}${q ? '&' : ''}page=0&size=100000`);
    downloadText('audit-log.csv', toCsv([['When', 'Who', 'Email', 'Role', 'Action', 'Record', 'Outcome', 'Error', 'Details', 'IP address', 'Request id'],
      ...all.items.map((e) => [e.at, e.actorName, e.actorEmail, e.actorRole, e.label, targetText(e), e.outcome, e.errorCode, e.detail, e.ip, e.requestId])]));
  }

  const pages = log.data ? Math.max(1, Math.ceil(log.data.total / log.data.size)) : 1;
  return (
    <div className="page">
      <div className="page-head">
        <div><h1 className="title">Audit log</h1>
          <p className="muted">Every action in HumSaadhak: who did it, when, from where, and whether it was done, refused or failed. Records cannot be changed or deleted.</p></div>
        {DEMO
          ? <button type="button" className="btn btn-primary" onClick={() => void demoCsv()}>Download CSV</button>
          : <a className="btn btn-primary" href={`/api/v2/audit/events.csv${q ? `?${q}` : ''}`} download>Download CSV</a>}
      </div>

      <form className="card" onSubmit={(e) => { e.preventDefault(); setPage(0); setFilters(draft); }}
            style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12, alignItems: 'end' }}>
        <div className="field"><label htmlFor="al-from">From</label><input className="form-control" id="al-from" type="date" value={draft.from} onChange={set('from')} /></div>
        <div className="field"><label htmlFor="al-to">To</label><input className="form-control" id="al-to" type="date" value={draft.to} onChange={set('to')} /></div>
        <div className="field"><label htmlFor="al-actor">Person</label><input className="form-control" id="al-actor" placeholder="Name or email" value={draft.actor} onChange={set('actor')} /></div>
        <div className="field"><label htmlFor="al-area">Area</label>
          <select className="form-select" id="al-area" value={draft.category} onChange={set('category')}><option value="">All areas</option>{AREAS.map(([v, l]) => <option key={v} value={v}>{l}</option>)}</select></div>
        <div className="field"><label htmlFor="al-outcome">Outcome</label>
          <select className="form-select" id="al-outcome" value={draft.outcome} onChange={set('outcome')}><option value="">Any outcome</option>
            {Object.entries(OUTCOMES).map(([v, [l]]) => <option key={v} value={v}>{l}</option>)}</select></div>
        <div className="field"><label htmlFor="al-type">Record type</label>
          <select className="form-select" id="al-type" value={draft.targetType} onChange={set('targetType')}><option value="">Any record</option>
            {TARGETS.map((t) => <option key={t} value={t}>{t.replace(/_/g, ' ').toLowerCase()}</option>)}</select></div>
        <div className="field"><label htmlFor="al-id">Record id</label><input className="form-control" id="al-id" value={draft.targetId} onChange={set('targetId')} /></div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button type="submit" className="btn btn-primary">Show</button>
          <button type="button" className="btn btn-outline-secondary" onClick={() => { setDraft(EMPTY); setFilters(EMPTY); setPage(0); }}>Clear</button>
        </div>
      </form>

      {log.isLoading ? <Skeleton height={320} /> : log.error ? <ErrorNote error={log.error} /> : (
        <div className="card" style={{ padding: 0 }}>
          <div className="table-responsive table-wrap">
            <table className="table">
              <caption className="sr-only">Audit log</caption>
              <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Record</th><th>Outcome</th><th>Details</th><th>From</th></tr></thead>
              <tbody>
                {log.data!.items.map((e) => (
                  <tr key={e.id}>
                    <td style={{ whiteSpace: 'nowrap' }}><time dateTime={e.at}>{when(e.at)}</time></td>
                    <td>{who(e)}{e.actorEmail && e.actorName ? <div className="small muted">{e.actorEmail}</div> : null}</td>
                    <td>{e.label}</td>
                    <td>{targetText(e) || <span className="muted">—</span>}</td>
                    <td><span className={`pill ${OUTCOMES[e.outcome][1]}`}>{OUTCOMES[e.outcome][0]}</span>{e.errorCode && <div className="small muted">{e.errorCode}</div>}</td>
                    <td className="small">{e.detail}</td>
                    <td className="small muted">{e.ip}{e.userAgent ? <div title={e.userAgent}>{e.userAgent.slice(0, 32)}{e.userAgent.length > 32 ? '…' : ''}</div> : null}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!log.data!.items.length && <p className="muted" style={{ padding: 16 }}>Nothing matches these filters.</p>}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: 12 }}>
            <span className="small muted">{log.data!.total.toLocaleString('en-IN')} actions</span>
            <div className="segmented">
              <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</button>
              <span className="small">Page {page + 1} of {pages}</span>
              <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Older</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
