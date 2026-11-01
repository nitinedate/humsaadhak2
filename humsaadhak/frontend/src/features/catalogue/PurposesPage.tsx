import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { PurposeView } from '../../shared/api/types';
import { inr, PERIODICITY_LABEL } from '../../shared/format';
import { Drawer } from '../../shared/ui/Drawer';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { StatusPill } from '../../shared/ui/StatusPill';
import { CatalogueForm } from './CatalogueForm';
import { type CatalogueDraft, parseRupees } from './forms';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const EMPTY: CatalogueDraft = { name: '', description: '', sector: '', amount: '', periodicity: 'ONE_TIME' };

/** Donors (and associates on their behalf) describe what they want to fund. */
export default function PurposesPage() {
  const { me, can } = useAuth();
  const staff = can('dashboard.platform');
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['purposes'], queryFn: () => api<PurposeView[]>('/purposes') });
  const [editing, setEditing] = useState<PurposeView | 'new' | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: async ({ d, submit }: { d: CatalogueDraft; submit: boolean }) => {
      const body = { donorOrgId: staff ? d.orgId : null, name: d.name.trim(), description: d.description.trim(), sector: d.sector, commitmentAmount: parseRupees(d.amount), periodicity: d.periodicity };
      let id: number;
      if (editing && editing !== 'new') { await api(`/purposes/${editing.id}`, { method: 'PUT', json: body }); id = editing.id; }
      else id = (await api<{ id: number }>('/purposes', { method: 'POST', json: body })).id;
      if (submit) await api(`/purposes/${id}/submit`, { method: 'POST' });
      return submit;
    },
    onSuccess: (submitted) => {
      setNotice(submitted ? 'Saved and sent for approval. You will get an email when it is approved.' : 'Draft saved.');
      setEditing(null);
      qc.invalidateQueries({ queryKey: ['purposes'] });
    },
  });

  const toDraft = (p: PurposeView): CatalogueDraft =>
    ({ orgId: p.donorOrgId, name: p.name, description: p.description, sector: p.sector, amount: p.commitmentAmount ? String(p.commitmentAmount) : '', periodicity: p.periodicity });

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <span className="eyebrow">What {me?.role.startsWith('DONOR') ? 'you want' : 'donors want'} to fund</span>
          <h1 className="title">Purposes</h1>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => { save.reset(); setEditing('new'); }}>New purpose</button>
      </div>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {list.isLoading && <Skeleton height={200} />}
      {list.error && <ErrorNote error={list.error} />}
      {list.data && (list.data.length === 0
        ? <div className="card empty">No purposes yet. Describe what you want to fund and we will find matching causes and projects.</div>
        : (
          <div className="table-responsive table-wrap">
            <table className="table table-hover align-middle mb-0">
              <thead><tr><th>Purpose</th><th>Sector</th><th>Amount</th><th>How often</th><th>Status</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
              <tbody>
                {list.data.map((p) => (
                  <tr key={p.id}>
                    <td><strong>{p.name}</strong><div className="small muted">{p.refNo}</div></td>
                    <td>{p.sector}</td>
                    <td className="num">{inr(p.commitmentAmount)}</td>
                    <td>{PERIODICITY_LABEL[p.periodicity]}</td>
                    <td><StatusPill status={p.approved ? 'APPROVED' : 'DRAFT'} /></td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { save.reset(); setEditing(p); }}>Edit</button>{' '}
                      <AuditTrailButton type="PURPOSE" id={p.id} name={p.name} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ))}

      {editing && (
        <Drawer title={editing === 'new' ? 'New purpose' : 'Edit purpose'} onClose={() => setEditing(null)}>
          {editing !== 'new' && editing.approved && <div className="alert alert-warning">This purpose is approved. Saving changes sends it back for approval.</div>}
          <CatalogueForm initial={editing === 'new' ? EMPTY : toDraft(editing)} amountLabel="Amount each time (₹, optional)" amountRequired={false} orgGroup={staff ? 'DONORS' : undefined}
                         busy={save.isPending} serverError={save.error instanceof ApiError ? save.error.message : save.error ? 'Could not save. Try again.' : null}
                         onSave={(d, submit) => save.mutate({ d, submit })} />
        </Drawer>
      )}
      <ActivityPanel categories={['purpose']} />
    </div>
  );
}
