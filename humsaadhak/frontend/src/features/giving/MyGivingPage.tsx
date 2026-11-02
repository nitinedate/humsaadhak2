import { Fragment, useState } from 'react';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../../shared/api/client';
import type { MatchRow, PurposeView, ShareRow } from '../../shared/api/types';
import { inr, PERIODICITY_LABEL } from '../../shared/format';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { SharedDocuments } from '../documents/SharedDocuments';
import { ActivityPanel } from '../../shared/ui/AuditTrail';

/** The donor's home for action: each purpose with its confirmed matches, document requests and a way to give. */
export default function MyGivingPage() {
  const qc = useQueryClient();
  const purposes = useQuery({ queryKey: ['purposes'], queryFn: () => api<PurposeView[]>('/purposes') });
  const approved = (purposes.data ?? []).filter((p) => p.approved);
  const matches = useQueries({
    queries: approved.map((p) => ({ queryKey: ['matches', p.id], queryFn: () => api<MatchRow[]>(`/purposes/${p.id}/matches`) })),
  });
  const shares = useQuery({ queryKey: ['share-requests'], queryFn: () => api<ShareRow[]>('/share-requests') });
  const [openShare, setOpenShare] = useState<number | null>(null);   // the match whose shared documents are open
  const [notice, setNotice] = useState<string | null>(null);

  const request = useMutation({
    mutationFn: (needId: number) => api(`/share-requests?needId=${needId}`, { method: 'POST' }),
    onSuccess: () => {
      setNotice('Request sent. The school or NGO decides whether to share; you will get an email either way.');
      qc.invalidateQueries({ queryKey: ['share-requests'] });
    },
  });
  const shareFor = (needId: number) => shares.data?.find((s) => s.needId === needId);

  if (purposes.isLoading) return <div className="page"><Skeleton height={300} /></div>;
  if (purposes.error) return <div className="page"><ErrorNote error={purposes.error} /></div>;

  return (
    <div className="page">
      <div className="page-head">
        <div><span className="eyebrow">Matches confirmed by HumSaadhak, in priority order</span><h1 className="title">My giving</h1></div>
        <Link to="/purposes" className="btn btn-outline-secondary">Manage purposes</Link>
      </div>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {request.error && <div className="alert alert-danger" role="alert">{request.error instanceof ApiError ? request.error.message : 'Could not send the request.'}</div>}
      {approved.length === 0 && <div className="card empty">Once a purpose is approved and matched, its causes and projects appear here.</div>}

      {approved.map((p, i) => {
        const q = matches[i]!;
        return (
          <section key={p.id} className="card" aria-labelledby={`p-${p.id}`}>
            <div className="page-head" style={{ alignItems: 'center' }}>
              <div>
                <h2 id={`p-${p.id}`} className="card-title" style={{ fontSize: 18 }}>{p.name}</h2>
                <span className="small muted">{p.refNo} · {inr(p.commitmentAmount)} · {PERIODICITY_LABEL[p.periodicity]}</span>
              </div>
            </div>
            {q.isLoading && <Skeleton height={80} />}
            {q.data?.length === 0 && <span className="muted">No confirmed matches yet. HumSaadhak staff review the engine’s suggestions first.</span>}
            {q.data && q.data.length > 0 && (
              <div className="table-responsive table-wrap">
                <table className="table table-hover align-middle mb-0">
                  <thead><tr><th>#</th><th>Cause or project</th><th>Still needed</th><th>Documents</th><th><span className="visually-hidden">Give</span></th></tr></thead>
                  <tbody>
                    {q.data.map((m) => {
                      const share = shareFor(m.needId);
                      const shared = share?.status === 'APPROVED';
                      return (
                        <Fragment key={m.id}>
                        <tr>
                          <td className="num"><strong>{m.priority}</strong></td>
                          <td><strong>{m.needName}</strong><div className="small muted">{m.refNo} · {m.kind === 'CAUSE' ? 'School cause' : 'NGO project'}</div></td>
                          <td className="num">{inr(m.remaining)}</td>
                          <td>
                            {shared ? <button type="button" className="btn btn-outline-success btn-sm" aria-expanded={openShare === m.id}
                                              onClick={() => setOpenShare(openShare === m.id ? null : m.id)}>
                                        {openShare === m.id ? 'Hide documents' : 'View shared documents'}</button>
                             : share ? <span className={`pill ${share.status === 'REJECTED' ? 'rejected' : 'pending'}`}>
                                       {share.status === 'REJECTED' ? 'Declined' : 'Requested'}</span>
                                   : <button type="button" className="btn btn-outline-secondary btn-sm" disabled={request.isPending} onClick={() => request.mutate(m.needId)}>Request documents</button>}
                          </td>
                          <td style={{ textAlign: 'right' }}>
                            {m.remaining > 0 ? <Link className="btn btn-primary btn-sm" to={`/donate?needId=${m.needId}&matchId=${m.id}`}>Give</Link> : <span className="pill success">Fully funded</span>}
                          </td>
                        </tr>
                        {shared && openShare === m.id && (
                          <tr className="shared-docs-row"><td colSpan={5}><SharedDocuments requestId={share!.id} ownerName={share!.ownerName} /></td></tr>
                        )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        );
      })}
      <ActivityPanel categories={['donation', 'share']} />
    </div>
  );
}
