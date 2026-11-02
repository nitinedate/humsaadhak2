import { useMutation, useQuery } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import type { SharedDocumentRow } from '../../shared/api/types';
import { when } from '../../shared/ui/AuditTrail';
import { ErrorNote, Skeleton } from '../../shared/ui/States';

/**
 * The documents a school or NGO shares with a donor through an approved request: the current version of each
 * shareable document, once HumSaadhak has approved it. Each opens through a 5-minute signed link.
 */
export function SharedDocuments({ requestId, ownerName }: { requestId: number; ownerName: string }) {
  const list = useQuery({ queryKey: ['shared-documents', requestId], queryFn: () => api<SharedDocumentRow[]>(`/share-requests/${requestId}/documents`) });
  const open = useMutation({
    mutationFn: (id: number) => api<{ url: string }>(`/documents/${id}/download-url`),
    onSuccess: (r) => window.open(r.url, '_blank', 'noopener'),
  });
  if (list.isLoading) return <Skeleton height={60} />;
  if (list.error) return <ErrorNote error={list.error} />;
  if (!list.data?.length)
    return <p className="small muted mb-0">{ownerName} has no approved documents to show yet. They appear here once HumSaadhak approves them.</p>;
  return (
    <ul className="shared-docs" aria-label={`Documents shared by ${ownerName}`}>
      {list.data.map((d) => (
        <li key={d.id}>
          <div>
            <strong>{d.typeName}</strong>
            <div className="small muted">{d.name} · version {d.version}{d.approvedAt ? ` · approved ${when(d.approvedAt)}` : ''}</div>
          </div>
          <button type="button" className="btn btn-outline-secondary btn-sm" disabled={open.isPending} onClick={() => open.mutate(d.id)}
                  aria-label={`Download ${d.typeName}`}>Download</button>
        </li>
      ))}
    </ul>
  );
}
