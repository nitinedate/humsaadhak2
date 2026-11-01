import { useEffect, useRef, useState, type DragEvent } from 'react';

/** 1536 -> "2 KB", 5_400_000 -> "5.1 MB". */
export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${Math.round(n / 1024)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/** ['.pdf', '.jpg', '.png'] -> "PDF, JPG or PNG". */
export function describeTypes(accept: string[]): string {
  const names = [...new Set(accept.map((a) => a.replace('.', '').toUpperCase().replace('JPEG', 'JPG')))];
  return names.length > 1 ? `${names.slice(0, -1).join(', ')} or ${names[names.length - 1]}` : names[0] ?? '';
}

/** Returns why a file cannot be used, or null when it is fine. Same limits as the server's UploadPolicy. */
export function checkFile(file: File, accept: string[], maxBytes: number): string | null {
  const ext = file.name.includes('.') ? `.${file.name.split('.').pop()!.toLowerCase()}` : '';
  if (accept.length > 0 && !accept.includes(ext)) return `“${file.name}” can’t be uploaded here. Use a ${describeTypes(accept)} file.`;
  if (file.size === 0) return `“${file.name}” is empty.`;
  if (file.size > maxBytes) return `“${file.name}” is ${formatBytes(file.size)}; the limit is ${formatBytes(maxBytes)}.`;
  return null;
}

const KIND_TONE: Record<string, string> = { PDF: 'danger', JPG: 'info', PNG: 'info', MP4: 'warn', XLSX: 'success' };

interface Props {
  id: string;
  label: string;
  accept: string[];
  maxBytes: number;
  file: File | null;
  onChange: (file: File | null) => void;
  disabled?: boolean;
}

/**
 * File chooser with a drop area and a clear "Browse files" button instead of the browser's default control.
 * The real <input type="file"> stays in the page (visually hidden) so labels, keyboard and screen readers work.
 */
export function FilePicker({ id, label, accept, maxBytes, file, onChange, disabled = false }: Props) {
  const input = useRef<HTMLInputElement>(null);
  const [over, setOver] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // When the parent clears the file (for example after a successful upload), clear the native input too.
  useEffect(() => { if (!file && input.current) input.current.value = ''; }, [file]);

  function take(candidate: File | null | undefined) {
    if (!candidate) return;
    const problem = checkFile(candidate, accept, maxBytes);
    setError(problem);
    onChange(problem ? null : candidate);
  }
  const browse = () => { if (!disabled) input.current?.click(); };
  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setOver(false);
    if (!disabled) take(e.dataTransfer.files?.[0]);
  };
  const kind = file ? (file.name.split('.').pop() ?? '').toUpperCase().replace('JPEG', 'JPG') : '';
  const hint = `${describeTypes(accept)}, up to ${formatBytes(maxBytes)}`;

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input id={id} ref={input} type="file" className="visually-hidden" accept={accept.join(',')} disabled={disabled}
             aria-describedby={`${id}-hint${error ? ` ${id}-error` : ''}`} onChange={(e) => take(e.target.files?.[0])} />

      {file ? (
        <div className="file-chosen">
          <span className={`file-kind ${KIND_TONE[kind] ?? 'neutral'}`} aria-hidden="true">{kind || 'FILE'}</span>
          <span className="file-meta">
            <strong title={file.name}>{file.name}</strong>
            <span className="small muted">{formatBytes(file.size)} · ready to upload</span>
          </span>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={browse} disabled={disabled}>Replace</button>
          <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { setError(null); onChange(null); }} disabled={disabled}
                  aria-label={`Remove ${file.name}`}>Remove</button>
        </div>
      ) : (
        <div className={`dropzone${over ? ' over' : ''}${error ? ' invalid' : ''}`}
             onDragEnter={(e) => { e.preventDefault(); setOver(true); }}
             onDragOver={(e) => { e.preventDefault(); setOver(true); }}
             onDragLeave={() => setOver(false)}
             onDrop={onDrop}
             onClick={browse}>
          <span className="dropzone-icon" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 16V4M7 9l5-5 5 5M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3" />
            </svg>
          </span>
          <span className="dropzone-text">
            <strong>{over ? 'Drop the file to add it' : 'Drag a file here, or browse your computer'}</strong>
            <span className="small muted" id={`${id}-hint`}>{hint}</span>
          </span>
          <button type="button" className="btn btn-primary btn-sm" disabled={disabled} onClick={(e) => { e.stopPropagation(); browse(); }}>
            Browse files
          </button>
        </div>
      )}
      {file && <span className="visually-hidden" id={`${id}-hint`}>{hint}</span>}
      {error && <span className="error" role="alert" id={`${id}-error`}>{error}</span>}
    </div>
  );
}
