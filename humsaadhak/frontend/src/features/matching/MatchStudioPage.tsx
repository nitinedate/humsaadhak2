import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { CandidateRow, PurposeView } from '../../shared/api/types';
import { bestSentence, inr, matchLabel, PERIODICITY_LABEL, whyChips } from '../../shared/format';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { buildDecisions, type Choice } from './decisions';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

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
  const [order, setOrder] = useState<number[]>([]);
  const [choices, setChoices] = useState<Record<number, Choice>>({});
  const [remarks, setRemarks] = useState<Record<number, string>>({});
  const [done, setDone] = useState<string | null>(null);

  useEffect(() => {
    setOrder((candidates.data ?? []).map((c) => c.id));
    setChoices({}); setRemarks({}); setDone(null);
  }, [candidates.data]);

  const byId = useMemo(() => new Map((candidates.data ?? []).map((c) => [c.id, c])), [candidates.data]);
  // A purpose switch briefly leaves the previous order in local state while the next query loads.
  // Do not render or submit that old order against the newly selected purpose.
  const hasCandidateSnapshot = candidates.data !== undefined;
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
    onError: () => {
      // Another reviewer may have changed these candidates since this list was loaded.
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
            {hasCandidateSnapshot && order.length === 0 && !done &&
              <div className="card empty">No candidates waiting. Run the engine to look for causes and projects that fit this purpose.</div>}

            {hasCandidateSnapshot && order.map((id, i) => {
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

            {hasCandidateSnapshot && order.length > 0 && (
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
          </section>
        )}
      </div>
      <ActivityPanel categories={['match']} />
    </div>
  );
}
