/* Demo-mode audit trail: records every demo action the way the server does (AuditMethodInterceptor) and answers the
   /audit endpoints. Never bundled into production builds (only imported by demo.ts). */
import type { AuditEntry, AuditOutcome, AuditTargetType, HistoryChange, Me, PageResponse } from './types';

/** Same names as AuditActions.java. */
const LABELS: Record<string, string> = {
  'auth.sign-in': 'Signed in', 'auth.sign-out': 'Signed out', 'auth.register': 'Registered an account',
  'auth.reset-request': 'Asked for a password reset link', 'auth.set-password': 'Set a password', 'preferences.update': 'Changed personal settings',
  'user.create': 'Added the staff member', 'user.role-change': 'Changed the role', 'user.disable': 'Disabled the account', 'user.enable': 'Enabled the account', 'user.invite': 'Sent a new invitation',
  'role.create': 'Created the role', 'role.update': 'Changed the role', 'role.delete': 'Deleted the role',
  'permission.create': 'Created the permission', 'permission.update': 'Changed the permission', 'permission.delete': 'Deleted the permission',
  'organisation.profile-update': 'Updated the profile', 'organisation.contacts-update': 'Updated the contacts', 'organisation.bank-add': 'Added a bank account',
  'purpose.create': 'Created the purpose', 'purpose.update': 'Edited the purpose', 'purpose.submit': 'Submitted the purpose for approval',
  'need.create': 'Created the cause or project', 'need.update': 'Edited the cause or project', 'need.submit': 'Submitted for approval',
  'approval.approve': 'Approved', 'approval.reject': 'Rejected',
  'document.upload': 'Uploaded a document', 'document.download': 'Opened a download link', 'document.review': 'Accepted the document',
  'document.reject': 'Rejected the document', 'share.request': 'Asked to see documents', 'share.approve': 'Agreed to share documents',
  'share.reject': 'Declined to share documents', 'match.run': 'Ran the matching engine', 'match.confirm': 'Confirmed matches',
  'donation.record': 'Recorded a donation', 'import.upload': 'Imported a workbook', 'import.template': 'Downloaded an import template',
  'sector.create': 'Created the sector', 'sector.update': 'Changed the sector', 'document-type.create': 'Created the document type',
  'document-type.update': 'Changed the document type', 'report.run': 'Ran a report', 'report.export-csv': 'Exported data to CSV',
  'audit.export': 'Exported the audit log',
};
export const label = (action: string) => LABELS[action] ?? action;

type Row = AuditEntry & { orgIds: number[]; actorId: number | null };
const ago = (days: number) => new Date(Date.now() - days * 86_400_000).toISOString();
let nextId = 100;
const BROWSER = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0';
const seed = (id: number, at: string, actor: [number, string, string, string, number | null], action: string, type: AuditTargetType | null,
              target: string | null, orgIds: number[], outcome: AuditOutcome, detail: string | null, changes: HistoryChange[] = [], errorCode: string | null = null): Row =>
  ({ id, at, actorId: actor[0], actorName: actor[1], actorEmail: actor[2], actorRole: actor[3], action, label: label(action), category: action.split('.')[0]!,
     targetType: type, targetId: target, outcome, errorCode, detail, ip: '10.20.4.17', userAgent: BROWSER, requestId: `demo-${id}`, changes, orgIds });
const MEERA: [number, string, string, string, number] = [3, 'Meera Iyer', 'donor@demo.humsaadhak.org', 'Corporate donor', 11];
const PRIYA: [number, string, string, string, null] = [1, 'Priya Kulkarni', 'manager@demo.humsaadhak.org', 'Manager', null];
const SUNITA: [number, string, string, string, number] = [4, 'Sunita Pawar', 'school@demo.humsaadhak.org', 'Direct beneficiary (school)', 21];

const rows: Row[] = [
  seed(1, ago(21), MEERA, 'purpose.create', 'PURPOSE', '302', [11], 'SUCCESS', 'Digital classrooms for rural schools',
    [{ field: 'Name', before: null, after: 'Digital classrooms for rural schools' }, { field: 'Commitment amount', before: null, after: '1500000' }]),
  seed(2, ago(20.9), MEERA, 'purpose.submit', 'PURPOSE', '302', [11], 'SUCCESS', null),
  seed(3, ago(19), PRIYA, 'approval.approve', 'PURPOSE', '302', [11], 'SUCCESS', 'Digital classrooms for rural schools',
    [{ field: 'Approved', before: 'false', after: 'true' }]),
  seed(4, ago(12), MEERA, 'purpose.update', 'PURPOSE', '302', [11], 'SUCCESS', null,
    [{ field: 'Commitment amount', before: '1500000', after: '1800000' }, { field: 'Periodicity', before: 'YEARLY', after: 'HALF_YEARLY' }]),
  seed(5, ago(3), SUNITA, 'match.run', 'MATCH_RUN', null, [21], 'DENIED', null, [], 'denied'),
  seed(6, ago(0.2), PRIYA, 'auth.sign-in', 'USER', '1', [], 'SUCCESS', 'manager@demo.humsaadhak.org'),
];

interface Ctx { m: RegExpMatchArray; body: Record<string, unknown>; result: Record<string, unknown> | undefined; me: Me | null }
interface Rule { method: string; re: RegExp; action: (c: Ctx) => string; type: AuditTargetType | null; id?: (c: Ctx) => unknown; detail?: (c: Ctx) => unknown }
const P = (c: Ctx) => c.m[1];
const R = (k: string) => (c: Ctx) => c.result?.[k];
const RULES: Rule[] = [
  { method: 'POST', re: /^\/auth\/login$/, action: () => 'auth.sign-in', type: 'USER', id: (c) => c.me?.id, detail: (c) => c.body.email },
  { method: 'POST', re: /^\/auth\/logout$/, action: () => 'auth.sign-out', type: 'USER', id: (c) => c.me?.id },
  { method: 'POST', re: /^\/registrations$/, action: () => 'auth.register', type: 'USER', detail: (c) => c.body.email },
  { method: 'POST', re: /^\/auth\/password-reset$/, action: () => 'auth.reset-request', type: 'USER', detail: (c) => c.body.email },
  { method: 'POST', re: /^\/auth\/set-password$/, action: () => 'auth.set-password', type: 'USER' },
  { method: 'PUT', re: /^\/me\/preferences$/, action: () => 'preferences.update', type: 'USER', id: (c) => c.me?.id, detail: (c) => `Theme ${c.body.theme}` },
  { method: 'POST', re: /^\/(purposes|needs)$/, action: (c) => `${c.m[1] === 'purposes' ? 'purpose' : 'need'}.create`, type: null, id: R('id'), detail: (c) => c.body.name },
  { method: 'PUT', re: /^\/(purposes|needs)\/(\d+)$/, action: (c) => `${c.m[1] === 'purposes' ? 'purpose' : 'need'}.update`, type: null, id: (c) => c.m[2] },
  { method: 'POST', re: /^\/(purposes|needs)\/(\d+)\/submit$/, action: (c) => `${c.m[1] === 'purposes' ? 'purpose' : 'need'}.submit`, type: null, id: (c) => c.m[2] },
  { method: 'POST', re: /^\/approvals\/\d+\/(approve|reject)$/, action: (c) => `approval.${c.m[1]}`, type: null, detail: (c) => c.body.comment },
  { method: 'POST', re: /^\/documents$/, action: () => 'document.upload', type: 'DOCUMENT', id: R('id') },
  { method: 'GET', re: /^\/documents\/(\d+)\/download-url$/, action: () => 'document.download', type: 'DOCUMENT', id: P },
  { method: 'POST', re: /^\/documents\/(\d+)\/(review|reject)$/, action: (c) => `document.${c.m[2]}`, type: 'DOCUMENT', id: P, detail: (c) => c.body.reason },
  { method: 'POST', re: /^\/share-requests$/, action: () => 'share.request', type: 'SHARE_REQUEST', id: R('id') },
  { method: 'POST', re: /^\/share-requests\/(\d+)\/(approve|reject)$/, action: (c) => `share.${c.m[2]}`, type: 'SHARE_REQUEST', id: P, detail: (c) => c.body.reason },
  { method: 'POST', re: /^\/match-runs$/, action: () => 'match.run', type: 'MATCH_RUN', id: R('id'), detail: (c) => `${(c.body.purposeIds as unknown[] | undefined)?.length ?? 0} purposes` },
  { method: 'POST', re: /^\/purposes\/(\d+)\/candidates\/decisions$/, action: () => 'match.confirm', type: 'PURPOSE', id: P, detail: (c) => {
    const d = (c.body.decisions as { approve: boolean }[] | undefined) ?? [];
    return `${d.filter((x) => x.approve).length} approved, ${d.filter((x) => !x.approve).length} rejected`;
  } },
  { method: 'POST', re: /^\/donations$/, action: () => 'donation.record', type: 'DONATION', id: R('donationId'), detail: (c) => `Amount ${c.body.paidAmount}` },
  { method: 'POST', re: /^\/imports$/, action: () => 'import.upload', type: 'IMPORT_JOB', id: R('id') },
  { method: 'GET', re: /^\/imports\/templates\/(\w+)$/, action: () => 'import.template', type: null, detail: P },
  { method: 'PUT', re: /^\/organisations\/(\d+)\/profile$/, action: () => 'organisation.profile-update', type: 'ORGANISATION', id: P },
  { method: 'PUT', re: /^\/organisations\/(\d+)\/contacts$/, action: () => 'organisation.contacts-update', type: 'ORGANISATION', id: P },
  { method: 'POST', re: /^\/organisations\/(\d+)\/bank-accounts$/, action: () => 'organisation.bank-add', type: 'ORGANISATION', id: P },
  { method: 'POST', re: /^\/admin\/users$/, action: () => 'user.create', type: 'USER', id: R('id'), detail: (c) => `${c.body.email} as ${c.body.roleCode}` },
  { method: 'PUT', re: /^\/admin\/users\/(\d+)\/role$/, action: () => 'user.role-change', type: 'USER', id: P, detail: (c) => `New role ${c.body.roleCode}` },
  { method: 'POST', re: /^\/admin\/users\/(\d+)\/(disable|enable|invite)$/, action: (c) => `user.${c.m[2]}`, type: 'USER', id: P },
  { method: 'POST', re: /^\/admin\/access\/(roles|permissions)$/, action: (c) => `${c.m[1] === 'roles' ? 'role' : 'permission'}.create`, type: null, id: R('code'), detail: (c) => c.body.name },
  { method: 'PUT', re: /^\/admin\/access\/(roles|permissions)\/([\w.-]+)$/, action: (c) => `${c.m[1] === 'roles' ? 'role' : 'permission'}.update`, type: null, id: (c) => c.m[2] },
  { method: 'DELETE', re: /^\/admin\/access\/(roles|permissions)\/([\w.-]+)$/, action: (c) => `${c.m[1] === 'roles' ? 'role' : 'permission'}.delete`, type: null, id: (c) => c.m[2] },
  { method: 'POST', re: /^\/admin\/sectors$/, action: () => 'sector.create', type: 'SECTOR', id: R('code'), detail: (c) => c.body.name },
  { method: 'PUT', re: /^\/admin\/sectors\/([\w-]+)$/, action: () => 'sector.update', type: 'SECTOR', id: P },
  { method: 'POST', re: /^\/admin\/document-types$/, action: () => 'document-type.create', type: 'DOCUMENT_TYPE', id: R('id'), detail: (c) => c.body.name },
  { method: 'PUT', re: /^\/admin\/document-types\/(\d+)$/, action: () => 'document-type.update', type: 'DOCUMENT_TYPE', id: P },
  { method: 'POST', re: /^\/beneficiary-reports\/(\w+)$/, action: () => 'report.run', type: null, detail: P },
];
const TYPE_OF: Record<string, AuditTargetType> = { purpose: 'PURPOSE', need: 'NEED', role: 'ROLE', permission: 'PERMISSION' };

/** What the wrapper in demo.ts knows about a request once it has run. */
export interface Recorded {
  method: string; route: string; body: Record<string, unknown>; result?: unknown; error?: { status: number; code?: string; message: string };
  actor: Me | null; subject?: { type: AuditTargetType; id: number; title: string | null }; changes?: HistoryChange[]; owners?: number[];
}

/** Records one demo request if it is an audited action. Returns true when it was. */
export function record(r: Recorded): boolean {
  for (const rule of RULES) {
    if (rule.method !== r.method) continue;
    const m = r.route.match(rule.re);
    if (!m) continue;
    const ctx: Ctx = { m, body: r.body, result: (r.result ?? undefined) as Record<string, unknown> | undefined, me: r.actor };
    const action = rule.action(ctx);
    const type = r.subject?.type ?? rule.type ?? TYPE_OF[action.split('.')[0]!] ?? null;
    const id = r.subject?.id ?? rule.id?.(ctx);              // a failed create has no result, so no id
    const detail = [rule.detail?.(ctx), r.subject?.title, r.error?.status === 422 ? r.error.message : null].filter((x) => x != null && x !== '').join(' · ');
    const outcome: AuditOutcome = !r.error ? 'SUCCESS' : r.error.status === 401 || r.error.status === 403 ? 'DENIED' : 'FAILED';
    const n = nextId++;
    rows.unshift({
      id: n, at: new Date().toISOString(), actorId: r.actor?.id ?? null, actorName: r.actor?.fullName ?? null, actorEmail: r.actor?.email ?? null,
      actorRole: r.actor?.roleName ?? null, action, label: label(action), category: action.split('.')[0]!, targetType: type,
      targetId: id == null ? (type === 'USER' && r.actor ? String(r.actor.id) : null) : String(id), outcome,
      errorCode: r.error ? (r.error.code ?? (outcome === 'DENIED' ? 'denied' : `http-${r.error.status}`)) : null, detail: detail || null,
      ip: '10.20.4.17', userAgent: typeof navigator === 'undefined' ? BROWSER : navigator.userAgent, requestId: `demo-${n}`, changes: r.changes ?? [],
      orgIds: [...new Set([...(r.owners ?? []), ...(r.actor?.orgId != null ? [r.actor.orgId] : [])])],
    });
    return true;
  }
  return false;
}

const STAFF = ['SUPER_ADMIN', 'MANAGER', 'ASSOCIATE'];
const strip = ({ orgIds: _o, actorId: _a, ...e }: Row): AuditEntry => e;
const redact = (e: AuditEntry, all: boolean): AuditEntry => (all ? e : { ...e, ip: null, userAgent: null });
const pageOf = (items: AuditEntry[], p: URLSearchParams): PageResponse<AuditEntry> => {
  const page = Number(p.get('page') ?? 0), size = Math.min(Number(p.get('size') ?? 20), 100_000);
  return { items: items.slice(page * size, page * size + size), page, size, total: items.length };
};

/** GET /audit/trail, /audit/activity and /audit/events, with the server's visibility rules. */
export function read(route: string, p: URLSearchParams, me: Me | null): PageResponse<AuditEntry> {
  if (!me) throw Object.assign(new Error('Please sign in.'), { status: 401, detail: 'Please sign in.' });
  const staff = STAFF.includes(me.role);
  const all = me.permissions.includes('audit.view');
  const seen = (r: Row) => all || r.outcome === 'SUCCESS';
  if (route === '/audit/trail') {
    const type = p.get('type'), id = p.get('id');
    const list = rows.filter((r) => r.targetType === type && r.targetId === id && seen(r));
    if (!staff && list.length && !list.some((r) => r.orgIds.includes(me.orgId ?? -1)))
      throw Object.assign(new Error('You do not have permission to do this.'), { status: 403, detail: 'You do not have permission to do this.' });
    return pageOf(list.map(strip).map((e) => redact(e, all)), p);
  }
  if (route === '/audit/activity') {
    const cats = p.getAll('category');
    const mine = p.get('mine') === 'true';
    const list = rows.filter((r) => cats.includes(r.category) && seen(r)
      && (mine ? r.actorId === me.id : staff || r.orgIds.includes(me.orgId ?? -1)));
    return pageOf(list.map(strip).map((e) => redact(e, all)), p);
  }
  if (route === '/audit/events') {
    if (!all) throw Object.assign(new Error('You do not have permission to do this.'), { status: 403, detail: 'You do not have permission to do this.' });
    const day = (s: string | null, end = false) => (s ? new Date(`${s}T${end ? '23:59:59' : '00:00:00'}+05:30`).getTime() : null);
    const from = day(p.get('from')), to = day(p.get('to'), true), actor = (p.get('actor') ?? '').toLowerCase();
    const list = rows.filter((r) => (from == null || Date.parse(r.at) >= from) && (to == null || Date.parse(r.at) <= to)
      && (!actor || `${r.actorName ?? ''} ${r.actorEmail ?? ''}`.toLowerCase().includes(actor))
      && (!p.get('category') || r.category === p.get('category')) && (!p.get('outcome') || r.outcome === p.get('outcome'))
      && (!p.get('targetType') || r.targetType === p.get('targetType')) && (!p.get('targetId') || r.targetId === p.get('targetId')));
    return pageOf(list.map(strip), p);
  }
  throw Object.assign(new Error('Not found'), { status: 404, detail: `Demo mode has no data for ${route}` });
}

/** Field-level changes between two versions of a demo record, labelled like HistoryDiff.java. */
export function diff(before: Record<string, unknown> | undefined, after: Record<string, unknown> | undefined, fields: string[]): HistoryChange[] {
  if (!before || !after) return [];
  const text = (v: unknown) => (v == null || v === '' ? null : String(v));
  const name = (f: string) => f.replace(/([A-Z])/g, ' $1').toLowerCase().replace(/^./, (c) => c.toUpperCase());
  return fields.filter((f) => text(before[f]) !== text(after[f])).map((f) => ({ field: name(f), before: text(before[f]), after: text(after[f]) }));
}
