import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { CandidateRow, PurposeView } from '../../shared/api/types';
import { bestSentence, inr, matchLabel, PERIODICITY_LABEL, whyChips } from '../../shared/format';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { buildDecisions, type Choice } from './decisions';
import { ActivityPanel, AuditTrailButton, when } from '../../shared/ui/AuditTrail';

export default function MatchStudioPage() {
  const { can } = useAuth();
  const qc = useQueryClient();
  const purposes = useQuery({ queryKey: ['purposes'], queryFn: () => api<PurposeView[]>('/purposes') });
  const approved = (purposes.data ?? []).filter((p) => p.approved);
  const [pid, setPid] = useState<number | null>(null);
  const purpose = approved.find((p) => p.id === pid) ?? approved[0];

  const candidates = useQuery({
    queryKey: ['candidates', purpose?.id], enabled: !!purpose,
    queryFn: () => api<CandidateRow[]>(`/purposes/${purpose!.id}/candidates`),
  });
  const decided = useQuery({
    queryKey: ['candidates', purpose?.id, 'decided'], enabled: !!purpose,
    queryFn: () => api<CandidateRow[]>(`/purposes/${purpose!.id}/candidates/decided`),
  });
  const [order, setOrder] = useState<number[]>([]);
  const [choices, setChoices] = useState<Record<number, Choice>>({});
  const [remarks, setRemarks] = useState<Record<number, string>>({});
  const [done, setDone] = useState<string | null>(null);

  // Only candidates still waiting for review: one approved or rejected earlier cannot be decided again (the server
  // sends only waiting ones; this keeps a decided one off the screen in any case)
  const waiting = useMemo(() => (candidates.data ?? []).filter((c) => c.decision === 'PENDING'), [candidates.data]);
  useEffect(() => {
    setOrder(waiting.map((c) => c.id));
    setChoices({}); setRemarks({}); setDone(null);
  }, [waiting]);

  const byId = useMemo(() => new Map(waiting.map((c) => [c.id, c])), [waiting]);
  const { decisions, problem } = buildDecisions(order, choices, remarks);
  const approvedCount = decisions.filter((d) => d.approve && choices[d.candidateId]).length;
  const rejectedCount = Object.values(choices).filter((c) => c === 'R').length;

  const move = (i: number, dir: -1 | 1) => setOrder((o) => {
    const j = i + dir;
    if (j < 0 || j >= o.length) return o;
    const n = o.slice(); [n[i], n[j]] = [n[j]!, n[i]!]; return n;
  });

  const run = useMutation({
    mutationFn: () => api<{ id: number }>('/match-runs', { method: 'POST', json: { purposeIds: [purpose!.id] } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['candidates', purpose?.id] }),
  });
  const confirm = useMutation({
    mutationFn: () => api<{ id: number }>(`/purposes/${purpose!.id}/candidates/decisions`, { method: 'POST', json: { decisions } }),
    onSuccess: () => {
      setDone(`Batch confirmed · ${approvedCount} matches sent to the donor in this order`);
      qc.invalidateQueries({ queryKey: ['candidates', purpose?.id] });
    },
  });

  if (purposes.isLoading) return <div className="page"><Skeleton height={300} /></div>;
  if (purposes.error) return <div className="page"><ErrorNote error={purposes.error} /></div>;

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <span className="eyebrow">Matches of 50% and above · matched by meaning</span>
          <h1 className="title">Match Studio</h1>
        </div>
        {can('match.run') && purpose && (
          <button type="button" className="btn btn-dark" disabled={run.isPending} onClick={() => run.mutate()}>
            {run.isPending ? 'Running…' : 'Run engine for this purpose'}
          </button>
        )}
      </div>

      <div className="split">
        <section className="list" style={{ flex: '1 1 260px' }} aria-label="Approved purposes">
          {approved.length === 0 && <div className="card empty">No approved purposes yet.</div>}
          {approved.map((p) => (
            <button key={p.id} type="button" className="row-btn" aria-current={p.id === purpose?.id} onClick={() => setPid(p.id)}>
              <span className="grow">
                <strong>{p.name}</strong>
                <span className="sub">{p.refNo} · {inr(p.commitmentAmount)} · {PERIODICITY_LABEL[p.periodicity]}</span>
              </span>
            </button>
          ))}
        </section>

        {purpose && (
          <section style={{ flex: '999 1 560px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 14 }} aria-label="Candidates">
            <div className="purpose-banner">
              <span className="pill" style={{ alignSelf: 'flex-start', background: 'var(--marigold)', color: 'var(--ink)' }}>Purpose {purpose.refNo}</span>
              <h2 className="title" style={{ fontSize: 24, color: 'var(--hero-text)' }}>{purpose.name}</h2>
              <div><AuditTrailButton type="PURPOSE" id={purpose.id} name={purpose.name} className="btn btn-outline-secondary btn-sm" /></div>
              <p>{purpose.description}</p>
            </div>

            {done && <div className="alert alert-success" role="status">{done}</div>}
            {(confirm.error || run.error) && <div className="alert alert-danger" role="alert">
              {((confirm.error ?? run.error) as ApiError).message}</div>}
            {candidates.isLoading && <Skeleton height={160} />}
            {!candidates.isLoading && order.length === 0 && !done &&
              <div className="card empty">No candidates waiting. Run the engine to look for causes and projects that fit this purpose.</div>}

            {order.map((id, i) => {
              const c = byId.get(id);
              if (!c) return null;
              const choice = choices[id];
              const sentence = bestSentence(c.explanation);
              return (
                <article key={id} className={`candidate${choice === 'A' ? ' is-approved' : choice === 'R' ? ' is-rejected' : ''}`}>
                  <div className="rank">
                    <button type="button" aria-label={`Move ${c.needName} up`} disabled={i === 0} onClick={() => move(i, -1)}>▲</button>
                    <strong>{i + 1}</strong>
                    <button type="button" aria-label={`Move ${c.needName} down`} disabled={i === order.length - 1} onClick={() => move(i, 1)}>▼</button>
                  </div>
                  <div style={{ flex: '1 1 320px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <div className="segmented" style={{ alignItems: 'center' }}>
                      <h3 style={{ margin: 0, fontSize: 16 }}>{c.needName}</h3>
                      <span className="pill neutral">{c.kind === 'CAUSE' ? 'Cause' : 'Project'}</span>
                    </div>
                    <span className="muted" style={{ fontSize: 13 }}>{c.sector} · still needs {inr(c.remaining)}</span>
                    <div className="score-row">
                      <strong className="num">{c.score}%</strong>
                      <span className="match-label">{matchLabel(c.score)}</span>
                      <div className="score-track" role="img" aria-label={`${c.score}% match: ${matchLabel(c.score).toLowerCase()}`}>
                        <div className="score-fill" style={{ width: `${c.score}%` }} />
                        <div className="score-cut" />
                      </div>
                    </div>
                    <div className="why">{whyChips(c.explanation).map((w) => <span key={w}>{w}</span>)}</div>
                    {sentence && <p style={{ margin: 0, fontSize: 13, color: 'var(--body)' }}>“{sentence}”</p>}
                  </div>
                  <div style={{ flex: '0 1 210px', display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <div className="segmented" role="group" aria-label={`Decision for ${c.needName}`}>
                      <button type="button" className="btn btn-outline-success approve" style={{ flex: 1 }} aria-pressed={choice === 'A'}
                              onClick={() => setChoices((s) => ({ ...s, [id]: 'A' }))}>Approve</button>
                      <button type="button" className="btn btn-outline-danger reject" style={{ flex: 1 }} aria-pressed={choice === 'R'}
                              onClick={() => setChoices((s) => ({ ...s, [id]: 'R' }))}>Reject</button>
                    </div>
                    {choice === 'R' && (
                      <div className="field">
                        <label htmlFor={`r-${id}`} style={{ fontSize: 12 }}>Reason (required)</label>
                        <input id={`r-${id}`} className="form-control" style={{ minHeight: 40 }} value={remarks[id] ?? ''}
                               onChange={(e) => setRemarks((s) => ({ ...s, [id]: e.target.value }))} />
                      </div>
                    )}
                  </div>
                </article>
              );
            })}

            {order.length > 0 && (
              <div className="card" style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center' }}>
                <div style={{ flex: '1 1 280px' }}>
                  <strong><span style={{ color: 'var(--leaf)' }}>{approvedCount} approved</span> · <span style={{ color: 'var(--rose)' }}>{rejectedCount} rejected</span> · {order.length - approvedCount - rejectedCount} undecided</strong>
                  <div className="muted" style={{ fontSize: 13 }}>{problem ?? 'Ready. Approved candidates reach the donor in this order; every decision trains the model.'}</div>
                </div>
                <button type="button" className="btn btn-primary" disabled={!!problem || confirm.isPending} onClick={() => confirm.mutate()}>
                  Confirm batch and notify donor
                </button>
              </div>
            )}
            <Decided title="Approved" note="Confirmed matches, in the order the donor sees them"
                     rows={(decided.data ?? []).filter((c) => c.decision === 'APPROVED')} />
            <Decided title="Rejected" note="With the reason given; every rejection teaches the engine"
                     rows={(decided.data ?? []).filter((c) => c.decision === 'REJECTED')} />
          </section>
        )}
      </div>
      <ActivityPanel categories={['match']} />
    </div>
  );
}

/** Candidates already decided for the purpose: approved ones (with their priority) or rejected ones (with the reason). */
function Decided({ title, note, rows }: { title: 'Approved' | 'Rejected'; note: string; rows: CandidateRow[] }) {
  const approved = title === 'Approved';
  return (
    <section className="card" aria-label={`${title} candidates`}>
      <div><h2 className="h6 mb-0">{title} <span className="muted">({rows.length})</span></h2><div className="small muted">{note}</div></div>
      {rows.length === 0 ? <p className="small muted mb-0">None yet.</p> : (
        <div className="table-responsive table-wrap">
          <table className="table align-middle mb-0">
            <thead><tr>{approved && <th>Priority</th>}<th>Cause or project</th><th>Match</th>{!approved && <th>Reason</th>}<th>Decided</th></tr></thead>
            <tbody>
              {rows.map((c) => (
                <tr key={c.id}>
                  {approved && <td className="num"><strong>{c.priority ?? '–'}</strong></td>}
                  <td><strong>{c.needName}</strong><div className="small muted">{c.kind === 'CAUSE' ? 'School cause' : 'NGO project'} · {c.sector}</div></td>
                  <td className="num">{c.score}%<div className="small muted">{matchLabel(c.score)}</div></td>
                  {!approved && <td>{c.remark || <span className="muted">–</span>}</td>}
                  <td className="small">{c.decidedAt ? when(c.decidedAt) : '–'}{c.decidedBy && <div className="muted">{c.decidedBy}</div>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

