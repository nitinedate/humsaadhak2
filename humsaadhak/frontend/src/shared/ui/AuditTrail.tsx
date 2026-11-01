import { useEffect, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api, AUDITED } from '../api/client';
import type { AuditEntry, AuditTargetType, PageResponse } from '../api/types';
import { Drawer } from './Drawer';
import { ErrorNote, Skeleton } from './States';

const OUTCOME = { SUCCESS: 'Done', DENIED: 'Refused', FAILED: 'Failed' } as const;
const TARGET: Record<AuditTargetType, string> = {
  USER: 'User', ORGANISATION: 'Organisation', PURPOSE: 'Purpose', NEED: 'Cause or project', DOCUMENT: 'Document', SHARE_REQUEST: 'Document request',
  DONATION: 'Donation', MATCH_RUN: 'Matching run', IMPORT_JOB: 'Import', ROLE: 'Role', PERMISSION: 'Permission', SECTOR: 'Sector', DOCUMENT_TYPE: 'Document type',
};

export const when = (at: string) => new Date(at).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
export const who = (e: AuditEntry) =>
  (e.actorName ?? e.actorEmail ?? 'Someone not signed in') + (e.actorRole ? ` (${e.actorRole})` : '');
export const targetText = (e: AuditEntry) => (e.targetType ? `${TARGET[e.targetType] ?? e.targetType} ${e.targetId ?? ''}`.trim() : '');

/** Refetches when anything is changed anywhere in the app, so an open trail shows the action just taken. */
function useRefreshOnChange(refetch: () => unknown) {
  useEffect(() => {
    const on = () => { void refetch(); };
    window.addEventListener(AUDITED, on);
    return () => window.removeEventListener(AUDITED, on);
  }, [refetch]);
}

/** Who did what, when, how it ended and what it changed: newest first. */
export function AuditList({ entries, showTarget = false }: { entries: AuditEntry[]; showTarget?: boolean }) {
  if (!entries.length) return <p className="muted">Nothing recorded yet.</p>;
  return (
    <ol className="timeline audit" aria-label="Audit trail">
      {entries.map((e) => (
        <li key={e.id} className={e.outcome === 'SUCCESS' ? undefined : 'refused'}>
          <span className="dot" aria-hidden="true" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
            <span>
              <strong>{e.label}</strong>
              {e.outcome !== 'SUCCESS' && (
                <span className={`pill ${e.outcome === 'DENIED' ? 'warn' : 'danger'}`} style={{ marginLeft: 8 }}>
                  {OUTCOME[e.outcome]}{e.errorCode ? `: ${e.errorCode}` : ''}
                </span>
              )}
            </span>
            <span className="small">by {who(e)}{showTarget && targetText(e) ? <> · {targetText(e)}</> : null}</span>
            <span className="small muted">
              <time dateTime={e.at}>{when(e.at)}</time>
              {e.ip ? <> · {e.ip}</> : null}
              {e.userAgent ? <> · <span title={e.userAgent}>{e.userAgent.slice(0, 40)}{e.userAgent.length > 40 ? '…' : ''}</span></> : null}
            </span>
            {e.detail && <span className="what">{e.detail}</span>}
            {e.changes.map((c, i) => (
              <span key={`${c.field}-${i}`} className="what">
                {c.field}: {c.before != null ? <><s>{c.before}</s> → </> : null}{c.after ?? '(removed)'}
              </span>
            ))}
          </div>
        </li>
      ))}
    </ol>
  );
}

function usePaged(key: unknown[], path: (page: number) => string, enabled = true) {
  const q = useInfiniteQuery({
    queryKey: key,
    enabled,
    initialPageParam: 0,
    queryFn: ({ pageParam }) => api<PageResponse<AuditEntry>>(path(pageParam)),
    getNextPageParam: (last) => ((last.page + 1) * last.size < last.total ? last.page + 1 : undefined),
  });
  useRefreshOnChange(q.refetch);
  return q;
}

function Paged({ q, showTarget }: { q: ReturnType<typeof usePaged>; showTarget?: boolean }) {
  if (q.isLoading) return <Skeleton height={140} />;
  if (q.error) return <ErrorNote error={q.error} />;
  const entries = q.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <>
      <AuditList entries={entries} showTarget={showTarget} />
      {q.hasNextPage && (
        <button type="button" className="btn btn-outline-secondary btn-sm" disabled={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
          {q.isFetchingNextPage ? 'Loading…' : 'Show more'}
        </button>
      )}
    </>
  );
}

/** One record's audit trail. */
export function RecordTrail({ type, id }: { type: AuditTargetType; id: number | string }) {
  const q = usePaged(['audit', 'trail', type, String(id)], (page) => `/audit/trail?type=${type}&id=${encodeURIComponent(String(id))}&page=${page}&size=20`);
  return <Paged q={q} />;
}

/** "Audit trail" button for a row or a record; opens the trail beside the page. */
export function AuditTrailButton({ type, id, name, className = 'btn btn-outline-secondary btn-sm' }: { type: AuditTargetType; id: number | string; name: string; className?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" className={className} onClick={() => setOpen(true)} aria-label={`Audit trail of ${name}`}>Audit trail</button>
      {open && <Drawer title={`Audit trail: ${name}`} onClose={() => setOpen(false)}><RecordTrail type={type} id={id} /></Drawer>}
    </>
  );
}

/**
 * The page's own audit trail: recent actions in its area, newest first. Staff see everyone's; others see what concerns
 * their organisation, or only their own actions with mine. Loads when opened.
 */
export function ActivityPanel({ categories, mine = false, title = 'Audit trail for this page' }: { categories: string[]; mine?: boolean; title?: string }) {
  const [open, setOpen] = useState(false);
  const query = categories.map((c) => `category=${encodeURIComponent(c)}`).join('&');
  const q = usePaged(['audit', 'activity', categories.join(','), mine], (page) => `/audit/activity?${query}&mine=${mine}&page=${page}&size=20`, open);
  return (
    <details className="card activity" open={open}>
      <summary onClick={(e) => { e.preventDefault(); setOpen(!open); }}>
        <strong>{title}</strong> <span className="small muted">who did what here, and when</span>
      </summary>
      {open && <div style={{ marginTop: 12 }}><Paged q={q} showTarget /></div>}
    </details>
  );
}
