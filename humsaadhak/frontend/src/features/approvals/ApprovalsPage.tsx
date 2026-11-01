import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { ApprovalRow, PageResponse, StepRow, SubjectType } from '../../shared/api/types';
import { waited } from '../../shared/format';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const TABS: { label: string; type: SubjectType | null }[] = [
  { label: 'All', type: null }, { label: 'Users', type: 'USER' }, { label: 'Organisations', type: 'ORGANISATION' },
  { label: 'Purposes', type: 'PURPOSE' }, { label: 'Causes and projects', type: 'NEED' }, { label: 'Documents', type: 'DOCUMENT' },
];
const KIND: Record<SubjectType, string> = { USER: 'User', ORGANISATION: 'Organisation', PURPOSE: 'Purpose', NEED: 'Cause or project', DOCUMENT: 'Document' };
const ABBR: Record<SubjectType, string> = { USER: 'USR', ORGANISATION: 'ORG', PURPOSE: 'PUR', NEED: 'NED', DOCUMENT: 'DOC' };

export default function ApprovalsPage() {
  const qc = useQueryClient();
  const [type, setType] = useState<SubjectType | null>(null);
  const [selected, setSelected] = useState<number | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const list = useQuery({
    queryKey: ['approvals', type],
    queryFn: () => api<PageResponse<ApprovalRow>>(`/approvals?status=SUBMITTED${type ? `&type=${type}` : ''}`),
  });
  const rows = list.data?.items ?? [];
  const current = rows.find((r) => r.id === selected) ?? null;

  useEffect(() => { if (!current && rows.length) setSelected(rows[0]!.id); }, [rows, current]);

  const history = useQuery({
    queryKey: ['approval-history', selected], enabled: selected != null,
    queryFn: () => api<StepRow[]>(`/approvals/${selected}/history`),
  });

  const decide = useMutation({
    mutationFn: ({ id, approve }: { id: number; approve: boolean }) =>
      api(`/approvals/${id}/${approve ? 'approve' : 'reject'}`, { method: 'POST', json: { comment: approve ? null : reason } }),
    onSuccess: (_, v) => {
      setNotice(`${v.approve ? 'Approved' : 'Rejected'} · the maker has been notified`);
      setRejecting(false); setReason(''); setSelected(null);
      qc.invalidateQueries({ queryKey: ['approvals'] });
      qc.invalidateQueries({ queryKey: ['counters'] });
    },
  });

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h1 className="title">Approvals</h1>
          <span className="muted">Oldest first · you cannot approve your own submissions</span>
        </div>
      </div>
      <div role="tablist" aria-label="Record type" className="segmented">
        {TABS.map((t) => (
          <button key={t.label} role="tab" type="button" className="chip-btn" aria-selected={type === t.type}
                  onClick={() => { setType(t.type); setSelected(null); }}>{t.label}</button>
        ))}
      </div>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {list.error && <ErrorNote error={list.error} />}
      <div className="split">
        <section className="list" aria-label="Queue">
          {list.isLoading && <><Skeleton height={68} /><Skeleton height={68} /><Skeleton height={68} /></>}
          {!list.isLoading && rows.length === 0 && <div className="card empty">Nothing is waiting for approval. New submissions appear here.</div>}
          {rows.map((r) => (
            <button key={r.id} type="button" className="row-btn" aria-current={r.id === selected}
                    onClick={() => { setSelected(r.id); setRejecting(false); setNotice(null); }}>
              <span className="icon-badge">{ABBR[r.subjectType]}</span>
              <span className="grow">
                <strong>{r.title ?? `${KIND[r.subjectType]} #${r.subjectId}`}</strong>
                <span className="sub">{KIND[r.subjectType]} · revision {r.revision}</span>
              </span>
              <span className="num" style={{ fontSize: 12, fontWeight: 700, color: 'var(--body)' }}>{waited(r.submittedAt)}</span>
            </button>
          ))}
        </section>

        {current && (
          <aside className="side card" aria-label="Selected record">
            <div className="segmented" style={{ alignItems: 'center' }}>
              <span className="pill neutral">{KIND[current.subjectType]}</span>
              <span className="pill submitted">Submitted</span>
              <AuditTrailButton type={current.subjectType} id={current.subjectId} name={current.title ?? `${KIND[current.subjectType]} #${current.subjectId}`} />
            </div>
            <h2 className="title" style={{ fontSize: 24 }}>{current.title ?? `#${current.subjectId}`}</h2>
            <span className="muted">Waiting {waited(current.submittedAt)} · revision {current.revision}</span>
            {current.lastComment && <div className="alert alert-warning">{current.lastComment}</div>}

            <div>
              <h3 className="eyebrow" style={{ margin: '0 0 8px' }}>History</h3>
              {history.data?.map((s, i) => (
                <div key={i} style={{ fontSize: 13, color: 'var(--body)', padding: '2px 0' }}>
                  {s.from ? `${s.from.toLowerCase()} → ` : ''}{s.to.toLowerCase()} · {new Date(s.at).toLocaleString('en-IN')}{s.comment ? ` · “${s.comment}”` : ''}
                </div>
              ))}
            </div>

            {decide.error && <div className="alert alert-danger" role="alert">{decide.error instanceof ApiError ? decide.error.message : 'Could not save. Try again.'}</div>}

            {!rejecting ? (
              <div className="segmented">
                <button type="button" className="btn btn-primary" style={{ flex: 1 }} disabled={decide.isPending}
                        onClick={() => decide.mutate({ id: current.id, approve: true })}>Approve</button>
                <button type="button" className="btn btn-outline-danger reject" style={{ flex: 1 }} onClick={() => setRejecting(true)}>Reject…</button>
              </div>
            ) : (
              <div className="field">
                <label htmlFor="reason">Reason for rejection (required, sent to the maker)</label>
                <textarea id="reason" className="form-control" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} />
                <div className="segmented">
                  <button type="button" className="btn btn-outline-danger reject" aria-pressed="true" disabled={!reason.trim() || decide.isPending}
                          onClick={() => decide.mutate({ id: current.id, approve: false })}>Confirm rejection</button>
                  <button type="button" className="btn btn-outline-secondary" onClick={() => { setRejecting(false); setReason(''); }}>Cancel</button>
                </div>
              </div>
            )}
          </aside>
        )}
      </div>
      <ActivityPanel categories={['approval']} />
    </div>
  );
}
