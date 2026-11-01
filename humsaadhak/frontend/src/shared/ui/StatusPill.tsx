const LABELS: Record<string, [string, string]> = {
  DRAFT: ['Draft', 'draft'], SUBMITTED: ['Waiting for approval', 'submitted'], APPROVED: ['Approved', 'approved'],
  REJECTED: ['Rejected', 'rejected'], PENDING: ['Waiting', 'pending'], REVIEWED: ['Approved', 'reviewed'],
  COMPLETED: ['Completed', 'success'], ACTIVE: ['Active', 'success'], DISABLED: ['Disabled', 'neutral'],
};

/** One status vocabulary across the app, so the same state always looks and reads the same. */
export function StatusPill({ status }: { status: string }) {
  const [label, tone] = LABELS[status] ?? [status.toLowerCase(), 'neutral'];
  return <span className={`pill ${tone}`}>{label}</span>;
}
