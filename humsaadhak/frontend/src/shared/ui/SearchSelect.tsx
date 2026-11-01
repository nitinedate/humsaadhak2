import { useId, useMemo, useState } from 'react';

export interface Option<T> { value: T; label: string; detail?: string }

/**
 * Searchable picker over options loaded from the API (organisations, causes and projects). A text filter plus a native
 * listbox-style <select>, so it works with keyboard and screen readers without extra ARIA machinery.
 */
export function SearchSelect<T extends string | number>({ label, options, value, onChange, placeholder, hint, loading, error }: {
  label: string; options: Option<T>[]; value: T | null; onChange: (v: T | null) => void;
  placeholder?: string; hint?: string; loading?: boolean; error?: string | null;
}) {
  const id = useId();
  const [q, setQ] = useState('');
  const shown = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const list = needle ? options.filter((o) => `${o.label} ${o.detail ?? ''}`.toLowerCase().includes(needle)) : options;
    // keep the chosen option visible even when the filter would hide it
    const chosen = options.find((o) => o.value === value);
    return chosen && !list.includes(chosen) ? [chosen, ...list] : list;
  }, [options, q, value]);

  return (
    <div className="field">
      <label htmlFor={`${id}-select`}>{label}</label>
      <input className="form-control" type="search" aria-label={`Search ${label.toLowerCase()}`} placeholder={placeholder ?? 'Type to search'}
             value={q} onChange={(e) => setQ(e.target.value)} />
      <select id={`${id}-select`} className="form-select" value={value ?? ''} aria-describedby={hint ? `${id}-hint` : undefined}
              onChange={(e) => {
                const raw = e.target.value;
                const match = options.find((o) => String(o.value) === raw);
                onChange(match ? match.value : null);
              }}>
        <option value="">{loading ? 'Loading…' : shown.length ? 'Choose…' : 'No matches'}</option>
        {shown.map((o) => <option key={String(o.value)} value={String(o.value)}>{o.detail ? `${o.label} — ${o.detail}` : o.label}</option>)}
      </select>
      {hint && <span className="hint" id={`${id}-hint`}>{hint}</span>}
      {error && <span className="error">{error}</span>}
    </div>
  );
}
