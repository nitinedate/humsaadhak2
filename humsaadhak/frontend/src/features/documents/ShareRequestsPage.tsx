import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { ShareRow } from '../../shared/api/types';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const LABEL = { REQUESTED: ['Waiting for your answer', 'pending'], APPROVED: ['Shared', 'approved'], REJECTED: ['Declined', 'rejected'] } as const;

/** Donors ask to see a partner's shareable documents; the partner decides. */
export default function ShareRequestsPage() {
  const { can } = useAuth();
  const owner = can('share.decide');
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['share-requests'], queryFn: () => api<ShareRow[]>('/share-requests') });
  const [declining, setDeclining] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const decide = useMutation({
    mutationFn: ({ id, ok }: { id: number; ok: boolean }) =>
      api(`/share-requests/${id}/${ok ? 'approve' : 'reject'}`, { method: 'POST', json: ok ? undefined : { reason } }),
    onSuccess: (_, v) => {
      setNotice(v.ok ? 'Shared. The donor can now open your shareable documents for this need.' : 'Declined. The donor has been told.');
      setDeclining(null); setReason('');
      qc.invalidateQueries({ queryKey: ['share-requests'] });
      qc.invalidateQueries({ queryKey: ['counters'] });
    },
  });

  return (
    <div className="page">
      <div>
        <span className="eyebrow">{owner ? 'PAN and bank details are never shared, whatever you decide here' : 'Requests you have made'}</span>
        <h1 className="title">Document requests</h1>
      </div>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {decide.error && <div className="alert alert-danger" role="alert">{decide.error instanceof ApiError ? decide.error.message : 'Could not save.'}</div>}
      {list.isLoading && <Skeleton height={160} />}
      {list.error && <ErrorNote error={list.error} />}
      {list.data?.length === 0 && <div className="card empty">{owner ? 'No donor has asked for documents yet.' : 'You have not requested any documents. Use “Request documents” on My giving.'}</div>}
      {list.data?.map((r) => (
        <div key={r.id} className="card" style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center' }}>
          <div style={{ flex: '1 1 260px' }}>
            <strong>{owner ? r.donorName : r.ownerName}</strong>
            <div className="small muted">{owner ? 'asks to see your documents for' : 'documents for'} “{r.needName}”</div>
          </div>
          <span className={`pill ${LABEL[r.status][1]}`}>{LABEL[r.status][0]}</span>
          <AuditTrailButton type="SHARE_REQUEST" id={r.id} name={`documents for “${r.needName}”`} />
          {owner && r.status === 'REQUESTED' && (declining === r.id ? (
            <div className="field-row" style={{ flex: '1 1 100%' }}>
              <div className="field"><label htmlFor={`dr-${r.id}`}>Reason (sent to the donor)</label>
                <input id={`dr-${r.id}`} className="form-control" value={reason} onChange={(e) => setReason(e.target.value)} /></div>
              <button type="button" className="btn btn-outline-danger reject" aria-pressed="true" disabled={!reason.trim()} onClick={() => decide.mutate({ id: r.id, ok: false })}>Decline</button>
              <button type="button" className="btn btn-outline-secondary" onClick={() => setDeclining(null)}>Cancel</button>
            </div>
          ) : (
            <div className="segmented">
              <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { setDeclining(r.id); setReason(''); }}>Decline…</button>
              <button type="button" className="btn btn-primary btn-sm" disabled={decide.isPending} onClick={() => decide.mutate({ id: r.id, ok: true })}>Share documents</button>
            </div>
          ))}
        </div>
      ))}
      <ActivityPanel categories={['share']} />
    </div>
  );
}
