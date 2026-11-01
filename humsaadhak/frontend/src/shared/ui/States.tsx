import { ApiError } from '../api/client';

export function Skeleton({ height = 120 }: { height?: number }) {
  return <div className="skeleton" style={{ height }} aria-hidden="true" />;
}

export function ErrorNote({ error }: { error: unknown }) {
  const msg = error instanceof ApiError ? error.message : 'Could not load this. Check your connection and try again.';
  return <div className="alert alert-danger" role="alert">{msg}</div>;
}
