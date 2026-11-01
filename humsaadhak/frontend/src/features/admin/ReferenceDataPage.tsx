import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { DocumentCategory, DocumentTypeAdminRow, OrganisationType, SectorAdminRow } from '../../shared/api/types';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const GROUPS: { type: OrganisationType; label: string }[] = [
  { type: 'DONOR_INDIVIDUAL', label: 'Individual donors' }, { type: 'DONOR_CORPORATE', label: 'Corporate donors' },
  { type: 'SCHOOL', label: 'Direct beneficiaries (schools)' }, { type: 'NGO', label: 'Indirect beneficiaries (NGOs)' },
];
const CATEGORIES: Record<DocumentCategory, string> = {
  REGISTRATION: 'Registration', NEED: 'Cause or project', DONATION: 'Donation', ACKNOWLEDGEMENT: 'Acknowledgement', MEDIA: 'Photo or video',
};

/** Super admins: sectors, document types, and which documents each group must provide to be verified. */
export default function ReferenceDataPage() {
  const [tab, setTab] = useState<'documents' | 'sectors'>('documents');
  return (
    <div className="page">
      <div><span className="eyebrow">Changes apply immediately, without a release</span><h1 className="title">Reference data</h1></div>
      <div role="tablist" aria-label="What to manage" className="segmented">
        <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'documents'} onClick={() => setTab('documents')}>Document types and mandatory documents</button>
        <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'sectors'} onClick={() => setTab('sectors')}>Sectors</button>
      </div>
      {tab === 'documents' ? <DocumentTypes /> : <Sectors />}
      <ActivityPanel categories={['sector', 'document-type']} />
    </div>
  );
}

function useNotice() {
  const [msg, setMsg] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const fail = (e: unknown) => setMsg({ tone: 'error', text: e instanceof ApiError ? e.message : 'Could not save. Try again.' });
  const node = msg && <div className={`alert ${msg.tone}`} role={msg.tone === 'error' ? 'alert' : 'status'}>{msg.text}</div>;
  return { ok: (text: string) => setMsg({ tone: 'success', text }), fail, node };
}

function DocumentTypes() {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'document-types'], queryFn: () => api<DocumentTypeAdminRow[]>('/admin/document-types') });
  const [edits, setEdits] = useState<Record<number, DocumentTypeAdminRow>>({});
  const [draft, setDraft] = useState({ name: '', category: 'REGISTRATION' as DocumentCategory, shareable: false, mandatoryFor: [] as OrganisationType[] });
  const notice = useNotice();
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin', 'document-types'] }); qc.invalidateQueries({ queryKey: ['ref'] }); };

  const save = useMutation({
    mutationFn: (t: DocumentTypeAdminRow) => api<DocumentTypeAdminRow>(`/admin/document-types/${t.id}`, { method: 'PUT',
      json: { name: t.name, shareable: t.shareable, active: t.active, mandatoryFor: t.mandatoryFor } }),
    onSuccess: (t) => {
      // Show the saved row straight away (no flicker back to the old ticks while the list reloads).
      qc.setQueryData<DocumentTypeAdminRow[]>(['admin', 'document-types'], (rows) => rows?.map((r) => (r.id === t.id ? t : r)));
      setEdits((e) => { const n = { ...e }; delete n[t.id]; return n; });
      notice.ok(`Saved “${t.name}”. Verification of the affected organisations is being re-checked.`);
      refresh();
    },
    onError: notice.fail,
  });
  const create = useMutation({
    mutationFn: () => api<DocumentTypeAdminRow>('/admin/document-types', { method: 'POST', json: draft }),
    onSuccess: (t) => { notice.ok(`Added “${t.name}”.`); setDraft({ name: '', category: 'REGISTRATION', shareable: false, mandatoryFor: [] }); refresh(); },
    onError: notice.fail,
  });

  if (list.isLoading) return <Skeleton height={300} />;
  if (list.error) return <ErrorNote error={list.error} />;
  const row = (t: DocumentTypeAdminRow) => edits[t.id] ?? t;
  const change = (t: DocumentTypeAdminRow, patch: Partial<DocumentTypeAdminRow>) => setEdits((e) => ({ ...e, [t.id]: { ...row(t), ...patch } }));
  const toggle = (set: OrganisationType[], v: OrganisationType) => (set.includes(v) ? set.filter((x) => x !== v) : [...set, v]);

  return (
    <>
      {notice.node}
      <p className="muted small" style={{ margin: 0 }}>An organisation is verified when every document marked mandatory for its group has been approved. Changing a tick re-checks every organisation in that group.</p>
      <div className="table-responsive table-wrap">
        <table className="table table-hover align-middle mb-0">
          <thead>
            <tr><th>Document type</th><th>Shared with donors</th>{GROUPS.map((g) => <th key={g.type}>Mandatory for {g.label.toLowerCase()}</th>)}<th>In use</th><th><span className="visually-hidden">Save</span></th></tr>
          </thead>
          <tbody>
            {list.data!.map((t0) => {
              const t = row(t0);
              const dirty = !!edits[t0.id];
              return (
                <tr key={t.id}>
                  <td>
                    <label className="visually-hidden" htmlFor={`dt-${t.id}`}>Name of document type {t0.name}</label>
                    <input id={`dt-${t.id}`} className="form-control" style={{ minHeight: 36, padding: '4px 10px', minWidth: 200 }} value={t.name} onChange={(e) => change(t0, { name: e.target.value })} />
                    <div className="small muted">{CATEGORIES[t.category]}</div>
                  </td>
                  <td><input className="form-check-input" type="checkbox" aria-label={`${t0.name} is shared with donors`} checked={t.shareable} onChange={(e) => change(t0, { shareable: e.target.checked })} style={{ width: 18, height: 18 }} /></td>
                  {GROUPS.map((g) => (
                    <td key={g.type}>
                      <input className="form-check-input" type="checkbox" aria-label={`${t0.name} mandatory for ${g.label.toLowerCase()}`} checked={t.mandatoryFor.includes(g.type)} disabled={!t.active}
                             onChange={() => change(t0, { mandatoryFor: toggle(t.mandatoryFor, g.type) })} style={{ width: 18, height: 18 }} />
                    </td>
                  ))}
                  <td><input className="form-check-input" type="checkbox" aria-label={`${t0.name} in use`} checked={t.active}
                             onChange={(e) => change(t0, { active: e.target.checked, mandatoryFor: e.target.checked ? t.mandatoryFor : [] })} style={{ width: 18, height: 18 }} /></td>
                  <td><button type="button" className="btn btn-primary btn-sm" disabled={!dirty || !t.name.trim() || save.isPending} onClick={() => save.mutate(t)}>Save</button></td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <form className="card" aria-labelledby="new-dt" onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
        <h2 id="new-dt" className="card-title">Add a document type</h2>
        <div className="field-row">
          <div className="field"><label htmlFor="ndt-name">Name</label><input id="ndt-name" className="form-control" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} /></div>
          <div className="field"><label htmlFor="ndt-cat">Used for</label>
            <select id="ndt-cat" className="form-select" value={draft.category} onChange={(e) => setDraft({ ...draft, category: e.target.value as DocumentCategory })}>
              {Object.entries(CATEGORIES).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
            </select></div>
        </div>
        <label style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
          <input className="form-check-input" type="checkbox" checked={draft.shareable} onChange={(e) => setDraft({ ...draft, shareable: e.target.checked })} style={{ width: 18, height: 18 }} />
          Can be shared with donors when the organisation agrees
        </label>
        <fieldset style={{ border: 0, padding: 0, margin: 0, display: 'flex', flexWrap: 'wrap', gap: 16 }}>
          <legend className="label" style={{ fontWeight: 700, marginBottom: 6 }}>Mandatory for</legend>
          {GROUPS.map((g) => (
            <label key={g.type} style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <input className="form-check-input" type="checkbox" checked={draft.mandatoryFor.includes(g.type)} onChange={() => setDraft({ ...draft, mandatoryFor: toggle(draft.mandatoryFor, g.type) })} style={{ width: 18, height: 18 }} />
              {g.label}
            </label>
          ))}
        </fieldset>
        <button type="submit" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} disabled={!draft.name.trim() || create.isPending}>Add document type</button>
      </form>
    </>
  );
}

function Sectors() {
  const qc = useQueryClient();
  const list = useQuery({ queryKey: ['admin', 'sectors'], queryFn: () => api<SectorAdminRow[]>('/admin/sectors') });
  const [name, setName] = useState('');
  const [renaming, setRenaming] = useState<Record<string, string>>({});
  const notice = useNotice();
  const refresh = () => { qc.invalidateQueries({ queryKey: ['admin', 'sectors'] }); qc.invalidateQueries({ queryKey: ['ref'] }); };

  const create = useMutation({
    mutationFn: () => api<SectorAdminRow>('/admin/sectors', { method: 'POST', json: { name } }),
    onSuccess: (s) => { notice.ok(`Added “${s.name}”. It is offered on purpose and need forms now.`); setName(''); refresh(); },
    onError: notice.fail,
  });
  const update = useMutation({
    mutationFn: (s: SectorAdminRow) => api<SectorAdminRow>(`/admin/sectors/${s.code}`, { method: 'PUT', json: { name: s.name, active: s.active } }),
    onSuccess: (s) => {
      qc.setQueryData<SectorAdminRow[]>(['admin', 'sectors'], (rows) => rows?.map((r) => (r.code === s.code ? s : r)));
      setRenaming((r) => { const n = { ...r }; delete n[s.code]; return n; });
      notice.ok(s.active ? `Saved “${s.name}”.` : `“${s.name}” is switched off: hidden from new forms, kept on existing records.`);
      refresh();
    },
    onError: notice.fail,
  });

  if (list.isLoading) return <Skeleton height={240} />;
  if (list.error) return <ErrorNote error={list.error} />;
  return (
    <>
      {notice.node}
      <form className="card" onSubmit={(e) => { e.preventDefault(); create.mutate(); }} aria-labelledby="ns-h">
        <h2 id="ns-h" className="card-title">Add a sector</h2>
        <div className="field-row">
          <div className="field"><label htmlFor="ns-name">Name</label><input id="ns-name" className="form-control" value={name} onChange={(e) => setName(e.target.value)} /></div>
          <button type="submit" className="btn btn-primary" disabled={!name.trim() || create.isPending}>Add sector</button>
        </div>
      </form>
      <div className="table-responsive table-wrap">
        <table className="table table-hover align-middle mb-0">
          <thead><tr><th>Sector</th><th>Used by</th><th>Status</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            {list.data!.map((s) => {
              const editing = renaming[s.code];
              return (
                <tr key={s.code}>
                  <td>
                    {editing !== undefined ? (
                      <>
                        <label htmlFor={`sec-${s.code}`} className="visually-hidden">New name for {s.name}</label>
                        <input id={`sec-${s.code}`} className="form-control" style={{ minHeight: 36, padding: '4px 10px' }} value={editing}
                               onChange={(e) => setRenaming({ ...renaming, [s.code]: e.target.value })} />
                      </>
                    ) : <strong>{s.name}</strong>}
                  </td>
                  <td className="num">{s.usedBy} {s.usedBy === 1 ? 'record' : 'records'}</td>
                  <td><span className={`pill ${s.active ? 'approved' : 'neutral'}`}>{s.active ? 'Offered on forms' : 'Switched off'}</span></td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                    {editing !== undefined ? (
                      <>
                        <button type="button" className="btn btn-primary btn-sm" disabled={!editing.trim() || update.isPending} onClick={() => update.mutate({ ...s, name: editing })}>Save</button>{' '}
                        <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setRenaming((r) => { const n = { ...r }; delete n[s.code]; return n; })}>Cancel</button>
                      </>
                    ) : (
                      <>
                        {s.usedBy === 0 && <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setRenaming({ ...renaming, [s.code]: s.name })}>Rename</button>}{' '}
                        <button type="button" className="btn btn-outline-secondary btn-sm" disabled={update.isPending} onClick={() => update.mutate({ ...s, active: !s.active })}>
                          {s.active ? 'Switch off' : 'Switch on'}
                        </button>{' '}
                        <AuditTrailButton type="SECTOR" id={s.code} name={s.name} />
                      </>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <span className="small muted">Sectors already used by purposes, causes or projects cannot be renamed (records store the name); switch them off and add a new one instead.</span>
    </>
  );
}
