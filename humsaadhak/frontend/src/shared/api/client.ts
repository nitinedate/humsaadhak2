export const DEMO = import.meta.env.VITE_DEMO === 'true';

/** An RFC 9457 problem detail from the API, with the business code when there is one. */
export class ApiError extends Error {
  constructor(public status: number, message: string, public code?: string, public fields?: { field: string; message: string }[]) {
    super(message);
  }
}

function csrfToken(): string | undefined {
  return document.cookie.split('; ').find((c) => c.startsWith('XSRF-TOKEN='))?.split('=')[1];
}

/** Announced after every request that changes something (or tries to): each one is recorded, so open audit trails refresh. */
export const AUDITED = 'humsaadhak:audited';

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  try {
    return await request<T>(path, init);
  } finally {
    if ((init.method ?? 'GET').toUpperCase() !== 'GET' && typeof window !== 'undefined') window.dispatchEvent(new Event(AUDITED));
  }
}

async function request<T>(path: string, init: RequestInit & { json?: unknown }): Promise<T> {
  if (DEMO) return (await import('./demo')).demoApi<T>(path, init); // never bundled into production builds
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.json !== undefined) headers.set('Content-Type', 'application/json');
  // FormData bodies (file uploads) set their own multipart boundary, so no Content-Type here.
  const token = csrfToken();
  if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  const res = await fetch(`/api/v2${path}`, {
    ...init,
    headers,
    credentials: 'same-origin',
    body: init.json !== undefined ? JSON.stringify(init.json) : init.body,
  });
  if (res.status === 204 || res.headers.get('content-length') === '0') return undefined as T;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(res.status, body.detail ?? (res.status === 401 ? 'Please sign in.' : 'Something went wrong. Try again.'), body.code, body.fields);
  }
  return body as T;
}
