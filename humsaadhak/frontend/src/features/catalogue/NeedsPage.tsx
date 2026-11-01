import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { NeedView, SuggestedBenefactor } from '../../shared/api/types';
import { inr, PERIODICITY_LABEL } from '../../shared/format';
import { Drawer } from '../../shared/ui/Drawer';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { StatusPill } from '../../shared/ui/StatusPill';
import { CatalogueForm } from './CatalogueForm';
import { type CatalogueDraft, parseRupees } from './forms';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

/** Schools post causes, NGOs post projects; both see funding progress and donors whose purposes fit. */
export default function NeedsPage() {
  const { me, can } = useAuth();
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['needs'], queryFn: () => api<NeedView[]>('/needs') });
  const [editing, setEditing] = useState<NeedView | 'new' | null>(null);
  const [selected, setSelected] = useState<NeedView | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const defaultKind = me?.role === 'NGO' ? 'PROJECT' : 'CAUSE';
  const staff = can('dashboard.platform');

  const suggestions = useQuery({
    queryKey: ['suggested', selected?.id], enabled: !!selected?.approved,
    queryFn: () => api<SuggestedBenefactor[]>(`/needs/${selected!.id}/suggested-benefactors`),
  });

  const save = useMutation({
    mutationFn: async ({ d, submit }: { d: CatalogueDraft; submit: boolean }) => {
      const body = { orgId: staff ? d.orgId : null, kind: d.kind, name: d.name.trim(), description: d.description.trim(), sector: d.sector, targetAmount: parseRupees(d.amount), periodicity: d.periodicity };
      let id: number;
      if (editing && editing !== 'new') { await api(`/needs/${editing.id}`, { method: 'PUT', json: body }); id = editing.id; }
      else id = (await api<{ id: number }>('/needs', { method: 'POST', json: body })).id;
      if (submit) await api(`/needs/${id}/submit`, { method: 'POST' });
      return submit;
    },
    onSuccess: (submitted) => {
      setNotice(submitted ? 'Saved and sent for approval.' : 'Draft saved.');
      setEditing(null);
      qc.invalidateQueries({ queryKey: ['needs'] });
    },
  });

  const toDraft = (n: NeedView): CatalogueDraft =>
    ({ orgId: n.orgId, kind: n.kind, name: n.name, description: n.description, sector: n.sector, amount: String(n.targetAmount), periodicity: n.periodicity });
  const pct = (n: NeedView) => Math.min(100, Math.round((100 * n.raisedAmount) / n.targetAmount));
  const title = me?.role === 'SCHOOL' ? 'Our causes' : me?.role === 'NGO' ? 'Our projects' : 'Causes and projects';

  return (
    <div className="page">
      <div className="page-head">
        <div><span className="eyebrow">What needs funding</span><h1 className="title">{title}</h1></div>
        <button type="button" className="btn btn-primary" onClick={() => { save.reset(); setEditing('new'); }}>
          {me?.role === 'NGO' ? 'New project' : me?.role === 'SCHOOL' ? 'New cause' : 'New cause or project'}
        </button>
      </div>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {list.isLoading && <Skeleton height={200} />}
      {list.error && <ErrorNote error={list.error} />}
      <div className="split">
        <section className="list" aria-label={title}>
          {list.data?.length === 0 && <div className="card empty">Nothing yet. Describe what you need and HumSaadhak will bring it to donors whose purposes fit.</div>}
          {list.data?.map((n) => (
            <button key={n.id} type="button" className="row-btn" aria-current={selected?.id === n.id} onClick={() => setSelected(n)}>
              <span className="grow">
                <strong>{n.name}</strong>
                <span className="sub">{n.refNo} · {n.kind === 'CAUSE' ? 'Cause' : 'Project'} · {n.sector}</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 6 }}>
                  <span className="progress flex-grow-1" role="progressbar" aria-label={`Raised for ${n.name}`} aria-valuenow={Math.round(pct(n))} aria-valuemin={0} aria-valuemax={100}><span className="progress-bar" style={{ width: `${pct(n)}%` }} /></span>
                  <span className="small num">{inr(n.raisedAmount)} of {inr(n.targetAmount)}</span>
                </span>
              </span>
              <StatusPill status={n.completed ? 'COMPLETED' : n.approved ? 'APPROVED' : 'DRAFT'} />
            </button>
          ))}
        </section>
        {selected && (
          <aside className="side card" aria-label="Selected">
            <h2 className="title" style={{ fontSize: 22 }}>{selected.name}</h2>
            <span className="muted small">{PERIODICITY_LABEL[selected.periodicity]} · {pct(selected)}% funded</span>
            <p style={{ margin: 0, lineHeight: 1.55 }}>{selected.description}</p>
            <div className="segmented">
              <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { save.reset(); setEditing(selected); }}>Edit</button>
              <AuditTrailButton type="NEED" id={selected.id} name={selected.name} />
            </div>
            <h3 className="card-title" style={{ fontSize: 15 }}>Donors whose purposes fit</h3>
            {!selected.approved && <span className="muted small">Shown once this is approved.</span>}
            {suggestions.isLoading && selected.approved && <Skeleton height={80} />}
            {suggestions.data?.length === 0 && <span className="muted small">No close fits yet. New purposes are checked every day.</span>}
            {suggestions.data?.map((s) => (
              <div key={s.purposeId} style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                <span>{s.purposeName}</span><strong className="num">{s.score}</strong>
              </div>
            ))}
          </aside>
        )}
      </div>

      {editing && (
        <Drawer title={editing === 'new' ? 'New cause or project' : 'Edit'} onClose={() => setEditing(null)}>
          {editing !== 'new' && editing.approved && <div className="alert alert-warning">This is approved. Saving changes sends it back for approval.</div>}
          <CatalogueForm initial={editing === 'new' ? { kind: defaultKind, name: '', description: '', sector: '', amount: '', periodicity: 'ONE_TIME' } : toDraft(editing)}
                         kind={staff} orgGroup={staff ? 'BENEFICIARIES' : undefined} amountLabel="Target amount (₹)" amountRequired busy={save.isPending}
                         serverError={save.error instanceof ApiError ? save.error.message : save.error ? 'Could not save. Try again.' : null}
                         onSave={(d, submit) => save.mutate({ d, submit })} />
        </Drawer>
      )}
      <ActivityPanel categories={['need']} />
    </div>
  );
}
