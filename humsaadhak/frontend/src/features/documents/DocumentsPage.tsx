import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import { useDocumentTypes } from '../../shared/api/reference';
import type { DocRow, DocumentListRow, DocumentType, OrganisationType, PageResponse, ReviewRow, Role } from '../../shared/api/types';
import { FilePicker } from '../../shared/ui/FilePicker';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { StatusPill } from '../../shared/ui/StatusPill';
import { ActivityPanel, AuditTrailButton, when } from '../../shared/ui/AuditTrail';

const MAX_BYTES = 20 * 1024 * 1024;                    // same limit as the server's UploadPolicy
const ACCEPT = ['.pdf', '.jpg', '.jpeg', '.png', '.mp4'];

/**
 * Partners upload and see only their own organisation's documents. Reviewers work through the queue and can list every
 * organisation's documents by status. Downloads use 5-minute signed links.
 */
export default function DocumentsPage() {
  const { can } = useAuth();
  const reviewer = can('document.review');
  const uploader = can('document.upload');
  const [tab, setTab] = useState<'mine' | 'review' | 'all'>(reviewer && !uploader ? 'review' : 'mine');
  return (
    <div className="page">
      <h1 className="title">Documents</h1>
      {reviewer && (
        <div role="tablist" aria-label="View" className="segmented">
          {uploader && <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'mine'} onClick={() => setTab('mine')}>Our documents</button>}
          <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'review'} onClick={() => setTab('review')}>Review queue</button>
          <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'all'} onClick={() => setTab('all')}>All documents</button>
        </div>
      )}
      {tab === 'mine' ? <MyDocuments /> : tab === 'review' ? <ReviewQueue /> : <AllDocuments />}
      <ActivityPanel categories={['document']} />
    </div>
  );
}

// Compulsory documents follow the organisation type, which for these accounts is the role (AccountService.organisationTypeFor)
const ORGANISATION_TYPES: OrganisationType[] = ['DONOR_INDIVIDUAL', 'DONOR_CORPORATE', 'SCHOOL', 'NGO'];
const FOR_WHOM: Record<OrganisationType, string> = { SCHOOL: 'schools', NGO: 'NGOs', DONOR_CORPORATE: 'corporate donors', DONOR_INDIVIDUAL: 'individual donors' };
export const organisationTypeOf = (role: Role | undefined): OrganisationType | null =>
  role && (ORGANISATION_TYPES as string[]).includes(role) ? (role as OrganisationType) : null;
type Progress = 'missing' | DocRow['state'];
const PROGRESS: Record<Progress, { text: string; pill: string; mark: string }> = {
  missing: { text: 'not uploaded yet', pill: 'warn', mark: '!' },
  PENDING: { text: 'uploaded, being checked', pill: 'info', mark: '…' },
  REVIEWED: { text: 'accepted', pill: 'reviewed', mark: '✓' },
  REJECTED: { text: 'rejected, upload again', pill: 'rejected', mark: '✕' },
};

function MyDocuments() {
  const qc = useQueryClient();
  const types = useDocumentTypes();
  const docs = useQuery({ queryKey: ['documents'], queryFn: () => api<DocRow[]>('/documents') });
  const { me } = useAuth();
  const orgType = organisationTypeOf(me?.role);
  const compulsory = orgType ? (types.data ?? []).filter((t) => t.mandatoryFor?.includes(orgType)) : [];
  const others = (types.data ?? []).filter((t) => !compulsory.includes(t));
  const progress = (t: DocumentType): Progress => docs.data?.find((d) => d.typeId === t.id)?.state ?? 'missing';
  const done = compulsory.filter((t) => ['PENDING', 'REVIEWED'].includes(progress(t))).length;
  const label = (t: DocumentType) => `${t.name}${t.shareable ? '' : ' (never shared with donors)'}`;
  const [typeId, setTypeId] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const typeName = (id: number) => types.data?.find((t) => t.id === id)?.name ?? `Type ${id}`;
  const replaces = typeId ? docs.data?.find((d) => d.typeId === Number(typeId)) : undefined;

  const upload = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append('file', file!);
      return api<{ id: number }>(`/documents?typeId=${typeId}`, { method: 'POST', body: form });
    },
    onSuccess: () => {
      setNotice('Uploaded. A HumSaadhak reviewer will check it.');
      setFile(null); setTypeId('');
      qc.invalidateQueries({ queryKey: ['documents'] });
    },
  });
  const download = useMutation({
    mutationFn: (id: number) => api<{ url: string }>(`/documents/${id}/download-url`),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    setNotice(null);
    if (!typeId) return setProblem('Choose what kind of document this is.');
    if (!file) return setProblem('Choose a file to upload.');
    setProblem(null);
    upload.mutate();
  }

  return (
    <>
      <form className="card" onSubmit={submit} noValidate aria-labelledby="up-h">
        <h2 id="up-h" className="card-title">Upload a document</h2>
        {(problem || upload.error) && <div className="alert alert-danger" role="alert">{problem ?? (upload.error instanceof ApiError ? upload.error.message : 'Upload failed.')}</div>}
        {notice && <div className="alert alert-success" role="status">{notice}</div>}
        <div className="upload-grid">
          <div className="field">
            <label htmlFor="d-type">Document</label>
            <select id="d-type" className="form-select" value={typeId} onChange={(e) => setTypeId(e.target.value)}>
              <option value="">Choose…</option>
              {compulsory.length > 0 && orgType ? (
                <>
                  <optgroup label={`★ Compulsory for ${FOR_WHOM[orgType]} (${done} of ${compulsory.length} uploaded)`}>
                    {compulsory.map((t) => (
                      <option key={t.id} value={t.id} className="opt-compulsory">★ {label(t)}: compulsory, {PROGRESS[progress(t)].text}</option>
                    ))}
                  </optgroup>
                  <optgroup label="Other documents">
                    {others.map((t) => <option key={t.id} value={t.id}>{label(t)}</option>)}
                  </optgroup>
                </>
              ) : types.data?.map((t) => <option key={t.id} value={t.id}>{label(t)}</option>)}
            </select>
            {compulsory.length > 0 && orgType && (
              <div className="compulsory-list" aria-label={`Compulsory documents for ${FOR_WHOM[orgType]}`}>
                <span className="hint">Compulsory for {FOR_WHOM[orgType]}:</span>
                {compulsory.map((t) => {
                  const p = PROGRESS[progress(t)];
                  return (
                    <button key={t.id} type="button" className={`pill ${p.pill} pill-button`} title={`${t.name}: ${p.text}. Choose it.`}
                            onClick={() => { setTypeId(String(t.id)); setProblem(null); }}>
                      <span aria-hidden="true">{p.mark}</span> {t.name}<span className="visually-hidden">: {p.text}</span>
                    </button>
                  );
                })}
              </div>
            )}
            {replaces && <span className="hint">Replaces version {replaces.version} ({replaces.name}). Older versions are kept in the history.</span>}
          </div>
          <FilePicker id="d-file" label="File" accept={ACCEPT} maxBytes={MAX_BYTES} file={file}
                      onChange={(f) => { setFile(f); setProblem(null); setNotice(null); }} disabled={upload.isPending} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" className="btn btn-primary" disabled={upload.isPending}>{upload.isPending ? 'Uploading…' : 'Upload'}</button>
        </div>
      </form>

      {docs.isLoading && <Skeleton height={160} />}
      {docs.error && <ErrorNote error={docs.error} />}
      {docs.data && (
        <div className="table-responsive table-wrap">
          <table className="table table-hover align-middle mb-0">
            <thead><tr><th>Document</th><th>File</th><th>Version</th><th>Status</th><th><span className="visually-hidden">Open</span></th></tr></thead>
            <tbody>
              {docs.data.length === 0 && <tr><td colSpan={5} className="empty">No documents yet.</td></tr>}
              {docs.data.map((d) => (
                <tr key={d.id}>
                  <td><strong>{typeName(d.typeId)}</strong>{d.rejectionReason && <div className="small" style={{ color: 'var(--danger)' }}>{d.rejectionReason}</div>}</td>
                  <td>{d.name}</td>
                  <td className="num">{d.version}</td>
                  <td><StatusPill status={d.state} /></td>
                  <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}><button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => download.mutate(d.id)}>Open</button>{' '}<AuditTrailButton type="DOCUMENT" id={d.id} name={d.name} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function ReviewQueue() {
  const qc = useQueryClient();
  const queue = useQuery({ queryKey: ['documents', 'pending'], queryFn: () => api<ReviewRow[]>('/documents/pending') });
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [notice, setNotice] = useState<string | null>(null);

  const decide = useMutation({
    mutationFn: ({ id, ok }: { id: number; ok: boolean }) =>
      api(`/documents/${id}/${ok ? 'review' : 'reject'}`, { method: 'POST', json: ok ? undefined : { reason } }),
    onSuccess: (_, v) => {
      setNotice(v.ok ? 'Approved. The organisation’s verification was updated.' : 'Rejected. The organisation was told what to fix.');
      setRejecting(null); setReason('');
      qc.invalidateQueries({ queryKey: ['documents', 'pending'] });
      qc.invalidateQueries({ queryKey: ['counters'] });
    },
  });
  const open = useMutation({
    mutationFn: (id: number) => api<{ url: string }>(`/documents/${id}/download-url`),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
  });

  if (queue.isLoading) return <Skeleton height={200} />;
  if (queue.error) return <ErrorNote error={queue.error} />;
  return (
    <>
      {notice && <div className="alert alert-success" role="status">{notice}</div>}
      {decide.error && <div className="alert alert-danger" role="alert">{decide.error instanceof ApiError ? decide.error.message : 'Could not save.'}</div>}
      <div className="table-responsive table-wrap">
        <table className="table table-hover align-middle mb-0">
          <thead><tr><th>Document</th><th>Organisation</th><th>Uploaded by</th><th>Version</th><th><span className="visually-hidden">Decision</span></th></tr></thead>
          <tbody>
            {queue.data?.length === 0 && <tr><td colSpan={5} className="empty">Nothing to review.</td></tr>}
            {queue.data?.map((d) => (
              <tr key={d.id}>
                <td><strong>{d.typeName}</strong><div className="small muted">{d.name}</div></td>
                <td>{d.ownerName}</td>
                <td>{d.createdBy ?? '—'}</td>
                <td className="num">{d.version}</td>
                <td style={{ textAlign: 'right', minWidth: 300 }}>
                  {rejecting === d.id ? (
                    <div className="field-row" style={{ justifyContent: 'flex-end' }}>
                      <label htmlFor={`rr-${d.id}`} className="visually-hidden">Reason</label>
                      <input id={`rr-${d.id}`} className="form-control" placeholder="What should they fix?" value={reason} onChange={(e) => setReason(e.target.value)} />
                      <button type="button" className="btn btn-outline-danger reject btn-sm" aria-pressed="true" disabled={!reason.trim()} onClick={() => decide.mutate({ id: d.id, ok: false })}>Reject</button>
                      <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setRejecting(null)}>Cancel</button>
                    </div>
                  ) : (
                    <div className="segmented" style={{ justifyContent: 'flex-end' }}>
                      <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => open.mutate(d.id)}>Open</button>
                      <AuditTrailButton type="DOCUMENT" id={d.id} name={d.name} />
                      <button type="button" className="btn btn-outline-danger reject btn-sm" onClick={() => { setRejecting(d.id); setReason(''); }}>Reject…</button>
                      <button type="button" className="btn btn-primary btn-sm" disabled={decide.isPending} onClick={() => decide.mutate({ id: d.id, ok: true })}>Approve</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

const STATUS: Record<DocumentListRow['state'], { label: string; pill: string }> = {
  PENDING: { label: 'Waiting for review', pill: 'pending' },
  REVIEWED: { label: 'Approved', pill: 'approved' },
  REJECTED: { label: 'Rejected', pill: 'rejected' },
};
type Filters = { state: string; typeId: string; organisation: string };
const NO_FILTERS: Filters = { state: '', typeId: '', organisation: '' };
const PAGE_SIZE = 25;

/** Reviewers: the current version of every organisation's documents, filtered by status, type and organisation. */
function AllDocuments() {
  const types = useDocumentTypes();
  const [draft, setDraft] = useState<Filters>(NO_FILTERS);
  const [filters, setFilters] = useState<Filters>(NO_FILTERS);
  const [page, setPage] = useState(0);
  const query = new URLSearchParams(Object.entries(filters).filter(([, v]) => v.trim() !== '')).toString();
  const list = useQuery({
    queryKey: ['documents', 'all', query, page],
    queryFn: () => api<PageResponse<DocumentListRow>>(`/documents/all?${query}${query ? '&' : ''}page=${page}&size=${PAGE_SIZE}`),
    placeholderData: keepPreviousData,
  });
  const download = useMutation({
    mutationFn: (id: number) => api<{ url: string }>(`/documents/${id}/download-url`),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
  });
  const pages = list.data ? Math.max(1, Math.ceil(list.data.total / PAGE_SIZE)) : 1;
  const apply = (next: Filters) => { setDraft(next); setFilters(next); setPage(0); };

  return (
    <>
      <form className="card" aria-label="Filter documents" onSubmit={(e) => { e.preventDefault(); apply(draft); }}>
        <div className="filter-row">
          <div className="field">
            <label htmlFor="all-state">Status</label>
            <select id="all-state" className="form-select" value={draft.state} onChange={(e) => apply({ ...draft, state: e.target.value })}>
              <option value="">All statuses</option>
              <option value="PENDING">Waiting for review</option>
              <option value="REVIEWED">Approved</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="all-type">Document</label>
            <select id="all-type" className="form-select" value={draft.typeId} onChange={(e) => apply({ ...draft, typeId: e.target.value })}>
              <option value="">All documents</option>
              {types.data?.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </div>
          <div className="field">
            <label htmlFor="all-org">Organisation</label>
            <input id="all-org" className="form-control" placeholder="Part of the name" value={draft.organisation}
                   onChange={(e) => setDraft({ ...draft, organisation: e.target.value })} />
          </div>
          <div className="filter-actions">
            <button type="submit" className="btn btn-primary">Search</button>
            <button type="button" className="btn btn-outline-secondary" onClick={() => apply(NO_FILTERS)}>Clear</button>
          </div>
        </div>
      </form>

      {list.isLoading && <Skeleton height={200} />}
      {list.error && <ErrorNote error={list.error} />}
      {list.data && (
        <div className="table-responsive table-wrap">
          <table className="table table-hover align-middle mb-0">
            <caption className="visually-hidden">All documents</caption>
            <thead>
              <tr><th>Document</th><th>Organisation</th><th>Status</th><th>Version</th><th>Uploaded</th><th>Reviewed</th><th /></tr>
            </thead>
            <tbody>
              {list.data.items.length === 0 && <tr><td colSpan={7} className="text-center text-muted">No documents match these filters.</td></tr>}
              {list.data.items.map((d) => (
                <tr key={d.id}>
                  <td><strong>{d.typeName}</strong><div className="small text-muted">{d.name}</div></td>
                  <td>{d.ownerName}</td>
                  <td>
                    <span className={`pill ${STATUS[d.state].pill}`}>{STATUS[d.state].label}</span>
                    {d.state === 'REJECTED' && d.rejectionReason && <div className="small text-muted mt-1">{d.rejectionReason}</div>}
                  </td>
                  <td>{d.version}</td>
                  <td className="small">{when(d.uploadedAt)}{d.uploadedBy && <div className="text-muted">{d.uploadedBy}</div>}</td>
                  <td className="small">{d.reviewedAt ? <>{when(d.reviewedAt)}<div className="text-muted">{d.reviewedBy}</div></> : <span className="text-muted">Not yet</span>}</td>
                  <td className="text-end">
                    <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => download.mutate(d.id)}
                            aria-label={`Download ${d.typeName} of ${d.ownerName}`}>Download</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="pager">
            <span className="small text-muted">{list.data.total} document{list.data.total === 1 ? '' : 's'}</span>
            <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</button>
            <span className="small">Page {page + 1} of {pages}</span>
            <button type="button" className="btn btn-outline-secondary btn-sm" disabled={page + 1 >= pages} onClick={() => setPage(page + 1)}>Next</button>
          </div>
        </div>
      )}
    </>
  );
}

