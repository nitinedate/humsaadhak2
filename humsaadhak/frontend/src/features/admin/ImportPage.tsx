import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError, DEMO } from '../../shared/api/client';
import type { ImportJob, PageResponse } from '../../shared/api/types';
import { FilePicker } from '../../shared/ui/FilePicker';
import { ErrorNote } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const TYPES = { NEEDS: 'Causes and projects', PURPOSES: 'Purposes' } as const;
type ImportType = keyof typeof TYPES;

/** Bulk import from Excel: all-or-nothing, with every problem listed by row and column. */
export default function ImportPage() {
  const qc = useQueryClient();
  const [type, setType] = useState<ImportType>('NEEDS');
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<ImportJob | null>(null);
  const recent = useQuery({ queryKey: ['imports'], queryFn: () => api<PageResponse<ImportJob>>('/imports') });

  const run = useMutation({
    mutationFn: () => {
      const form = new FormData();
      form.append('file', file!);
      return api<ImportJob>(`/imports?type=${type}`, { method: 'POST', body: form });
    },
    onSuccess: (job) => { setResult(job); qc.invalidateQueries({ queryKey: ['imports'] }); },
  });

  function submit(e: FormEvent) {
    e.preventDefault();
    if (file) { setResult(null); run.mutate(); }
  }

  return (
    <div className="page">
      <div><span className="eyebrow">Up to 2,000 rows per file</span><h1 className="title">Bulk import</h1></div>
      <form className="card" onSubmit={submit} aria-labelledby="im-h">
        <h2 id="im-h" className="card-title">1. Choose what to import and download its template</h2>
        <div className="segmented" role="radiogroup" aria-label="What to import">
          {(Object.keys(TYPES) as ImportType[]).map((t) => (
            <button key={t} type="button" role="radio" aria-checked={type === t} className="chip-btn" onClick={() => setType(t)}>{TYPES[t]}</button>
          ))}
        </div>
        <a className="btn btn-outline-secondary" style={{ alignSelf: 'flex-start' }} href={DEMO ? '#' : `/api/v2/imports/templates/${type}`} download>Download the {TYPES[type].toLowerCase()} template (.xlsx)</a>
        <h2 className="card-title">2. Fill it in and upload it</h2>
        <FilePicker id="im-file" label="Excel file" accept={['.xlsx']} maxBytes={10 * 1024 * 1024} file={file}
                    onChange={(f) => { setFile(f); setResult(null); }} disabled={run.isPending} />
        <span className="hint">If any row has a problem nothing is saved, so you can fix the file and upload it again.</span>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" className="btn btn-primary" disabled={!file || run.isPending}>{run.isPending ? 'Checking…' : 'Check and import'}</button>
        </div>
        {run.error && <div className="alert alert-danger" role="alert">{run.error instanceof ApiError ? run.error.message : 'Import failed.'}</div>}
      </form>

      {result && (
        <section className="card" aria-labelledby="res-h" aria-live="polite">
          <h2 id="res-h" className="card-title">{result.status === 'COMPLETED' ? `Imported ${result.createdCount} rows` : `Nothing imported: ${result.errorCount} problems in ${result.fileName}`}</h2>
          {result.status === 'COMPLETED'
            ? <div className="alert alert-success">All {result.rowCount} rows were valid. They are saved as drafts waiting for approval.</div>
            : (
              <div className="table-responsive table-wrap">
                <table className="table table-hover align-middle mb-0">
                  <thead><tr><th>Row</th><th>Column</th><th>Problem</th></tr></thead>
                  <tbody>{result.errors.map((e, i) => <tr key={i}><td className="num">{e.row || '—'}</td><td>{e.column}</td><td>{e.message}</td></tr>)}</tbody>
                </table>
              </div>
            )}
        </section>
      )}

      <section className="card" aria-labelledby="rec-h">
        <h2 id="rec-h" className="card-title">Recent imports</h2>
        {recent.error && <ErrorNote error={recent.error} />}
        {recent.data?.items.length === 0 && <span className="muted">No imports yet.</span>}
        {recent.data?.items.map((j) => (
          <div key={j.id} style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center' }}>
            <span className={`pill ${j.status === 'COMPLETED' ? 'approved' : 'rejected'}`}>{j.status === 'COMPLETED' ? 'Imported' : 'Rejected'}</span>
            <strong style={{ flex: '1 1 200px' }}>{j.fileName}</strong>
            <span className="small muted">{TYPES[j.type]} · {j.createdCount || j.errorCount} {j.status === 'COMPLETED' ? 'rows' : 'problems'} · {j.createdBy ?? ''}</span>
            <AuditTrailButton type="IMPORT_JOB" id={j.id} name={j.fileName} />
          </div>
        ))}
      </section>
      <ActivityPanel categories={['import']} />
    </div>
  );
}
