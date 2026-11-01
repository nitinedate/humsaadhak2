/**
 * Demo mode only: roles and permissions held in memory, seeded exactly like V6__roles_permissions.sql and applying the
 * same rules as RolePolicy.java (codes, account types, lock-out protection, built-in items, roles in use).
 */
import type { AccessChange, AccessPermission, AccessRole, Role, RoleKindOption } from './types';

class AccessProblem extends Error { constructor(public status: number, message: string, public code?: string) { super(message); } }
export { AccessProblem };

const P = (code: string, name: string, category: string, description: string): AccessPermission => ({ code, name, category, description, system: true, roles: [] });
let permissions: AccessPermission[] = [
  P('admin.users', 'Manage users', 'Administration', 'Search users, change their role, disable or enable accounts, resend invitations.'),
  P('admin.roles', 'Manage roles and permissions', 'Administration', 'Create roles and permissions and decide which permissions each role grants.'),
  P('reference.manage', 'Manage reference data', 'Administration', 'Sectors, document types and the documents each organisation type must provide.'),
  P('audit.view', 'View the audit log', 'Administration', 'See every action in the audit log, including refused and failed attempts, with IP addresses and browsers.'),
  P('import.run', 'Bulk import', 'Administration', 'Import purposes, causes and projects from Excel.'),
  P('dashboard.platform', 'See platform-wide data', 'Reporting', 'Platform dashboards, chart details for every organisation, reports and data exports.'),
  P('user.approve', 'Approve users', 'Approvals', 'Approve or reject new user accounts.'),
  P('org.approve', 'Approve organisations', 'Approvals', 'Approve or reject organisations.'),
  P('record.approve', 'Approve records', 'Approvals', 'Approve or reject purposes, causes, projects and other submitted records.'),
  P('document.review', 'Review documents', 'Documents', 'Approve or reject uploaded documents.'),
  P('document.upload', 'Upload documents', 'Documents', 'Upload documents for the person’s organisation (or any, for staff).'),
  P('share.request', 'Request documents', 'Documents', 'Ask a school or NGO to share its documents (donors).'),
  P('share.decide', 'Share documents', 'Documents', 'Accept or decline a donor’s request to see documents (schools and NGOs).'),
  P('purpose.edit', 'Create and edit purposes', 'Giving', 'Describe what a donor wants to fund.'),
  P('need.edit', 'Create and edit causes and projects', 'Giving', 'Describe what a school or NGO needs.'),
  P('donation.record', 'Record donations', 'Giving', 'Record payments made by donors.'),
  P('match.run', 'Run the matching engine', 'Matching', 'Start matching runs for approved purposes.'),
  P('match.decide', 'Review matches', 'Matching', 'Approve, reject and prioritise match candidates.'),
];
const KIND_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super admin', MANAGER: 'HumSaadhak staff, platform overview dashboard', ASSOCIATE: 'HumSaadhak staff, work dashboard',
  DONOR_INDIVIDUAL: 'Individual donor', DONOR_CORPORATE: 'Corporate donor', SCHOOL: 'Direct beneficiary (school)', NGO: 'Indirect beneficiary (NGO)', GUEST: 'Guest',
};
const R = (code: Role, name: string, description: string, perms: string[]) =>
  ({ code, name, description, kind: code, kindLabel: KIND_LABEL[code], system: true, users: 0, permissions: perms } as AccessRole);
let roles: AccessRole[] = [
  R('SUPER_ADMIN', 'Super admin', 'Runs the platform: users, roles, reference data and imports.', ['admin.users', 'admin.roles', 'import.run', 'dashboard.platform', 'reference.manage', 'audit.view']),
  R('MANAGER', 'Manager', 'Approves records, reviews documents and matches, and sees platform data.', ['user.approve', 'org.approve', 'record.approve', 'document.review', 'match.run', 'match.decide', 'dashboard.platform', 'audit.view']),
  R('ASSOCIATE', 'Operations associate', 'Enters purposes, causes, projects and donations, and prepares matches.', ['purpose.edit', 'need.edit', 'document.upload', 'match.decide', 'donation.record', 'import.run', 'dashboard.platform']),
  R('DONOR_INDIVIDUAL', 'Individual donor', 'Gives in their own name.', ['purpose.edit', 'document.upload', 'share.request', 'donation.record']),
  R('DONOR_CORPORATE', 'Corporate donor', 'Companies, CSR teams, foundations and trusts.', ['purpose.edit', 'document.upload', 'share.request', 'donation.record']),
  R('SCHOOL', 'Direct beneficiary (school)', 'Schools that receive help directly.', ['need.edit', 'document.upload', 'share.decide']),
  R('NGO', 'Indirect beneficiary (NGO)', 'NGOs working with communities.', ['need.edit', 'document.upload', 'share.decide']),
  R('GUEST', 'Guest', 'Signed in but not yet allowed to do anything.', []),
];
const changes: AccessChange[] = [];
export const CUSTOM_KINDS: RoleKindOption[] = (['MANAGER', 'ASSOCIATE', 'DONOR_INDIVIDUAL', 'DONOR_CORPORATE', 'SCHOOL', 'NGO'] as Role[])
  .map((kind) => ({ kind, label: KIND_LABEL[kind] }));

const bad = (message: string, code?: string): never => { throw new AccessProblem(422, message, code); };
const log = (actor: string, action: string, subject: string, details: string | null = null) =>
  changes.unshift({ at: new Date().toISOString(), actor, action, subject, details });

/** Like AdminUserController: a person's role change goes in the change log. */
export const logUserRoleChange = (actor: string, email: string, before: string, after: string) => log(actor, 'USER_ROLE_CHANGED', email, `${roleName(before)} → ${roleName(after)}`);

export const permissionsOf = (roleCode: string) => [...(roles.find((r) => r.code === roleCode)?.permissions ?? [])];
export const roleName = (roleCode: string) => roles.find((r) => r.code === roleCode)?.name ?? roleCode;
export const kindOf = (roleCode: string) => roles.find((r) => r.code === roleCode)?.kind;

export function listRoles(userCount: (code: string) => number): AccessRole[] {
  return roles.map((r) => ({ ...r, users: userCount(r.code), permissions: [...r.permissions].sort() }));
}
export function listPermissions(): AccessPermission[] {
  return permissions.map((p) => ({ ...p, roles: roles.filter((r) => r.permissions.includes(p.code)).map((r) => r.name).sort() }))
    .sort((a, b) => a.category.localeCompare(b.category) || a.name.localeCompare(b.name));
}
export const listChanges = () => changes.slice(0, 50);

export function createRole(actor: string, b: { name?: string; description?: string; kind?: string; copyFrom?: string }) {
  const name = (b.name ?? '').trim();
  if (!name) bad('Enter a name.');
  const kind = CUSTOM_KINDS.find((k) => k.kind === b.kind)?.kind ?? bad('Choose the type of account for this role.');
  const code = name.toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_|_$/g, '').slice(0, 40);
  if (roles.some((r) => r.code === code || r.name.toLowerCase() === name.toLowerCase())) bad(`A role called “${name}” already exists.`, 'duplicate');
  roles = [...roles, { code, name, description: b.description?.trim() || null, kind: kind!, kindLabel: KIND_LABEL[kind!], system: false, users: 0,
    permissions: b.copyFrom ? permissionsOf(b.copyFrom) : [] }];
  log(actor, 'ROLE_CREATED', name, `Account type: ${KIND_LABEL[kind!]}${b.copyFrom ? `; permissions copied from ${roleName(b.copyFrom)}` : ''}`);
  return roles.find((r) => r.code === code)!;
}

export function updateRole(actor: string, code: string, b: { name?: string; description?: string; permissions?: string[] }) {
  const role = roles.find((r) => r.code === code) ?? bad('No such role.');
  const wanted = [...new Set(b.permissions ?? [])];
  const unknown = wanted.filter((p) => !permissions.some((x) => x.code === p));
  if (unknown.length) bad(`Unknown permission ${unknown.join(', ')}.`, 'unknown-permission');
  if (code === 'SUPER_ADMIN' && !['admin.users', 'admin.roles'].every((p) => wanted.includes(p)))
    bad('The Super admin role always keeps “Manage users” and “Manage roles and permissions”, so the platform can always be administered.', 'lockout');
  const name = b.name?.trim() || role!.name;
  if (name !== role!.name && role!.system) bad('Built-in roles keep their names. Create a new role instead.', 'system-role');
  const nameOf = (c: string) => permissions.find((p) => p.code === c)?.name ?? c;
  const added = wanted.filter((p) => !role!.permissions.includes(p)).map(nameOf).sort();
  const removed = role!.permissions.filter((p) => !wanted.includes(p)).map(nameOf).sort();
  roles = roles.map((r) => (r.code === code ? { ...r, name, description: b.description?.trim() || null, permissions: wanted } : r));
  if (added.length || removed.length)
    log(actor, 'ROLE_PERMISSIONS_CHANGED', name, [added.length ? `Added: ${added.join(', ')}` : '', removed.length ? `Removed: ${removed.join(', ')}` : ''].filter(Boolean).join('; '));
  return roles.find((r) => r.code === code)!;
}

export function deleteRole(actor: string, code: string, users: number) {
  const role = roles.find((r) => r.code === code) ?? bad('No such role.');
  if (role!.system) bad('Built-in roles cannot be deleted.', 'system-role');
  if (users > 0) bad(`${users} ${users === 1 ? 'person has' : 'people have'} this role. Give them another role first.`, 'in-use');
  roles = roles.filter((r) => r.code !== code);
  log(actor, 'ROLE_DELETED', role!.name);
}

export function createPermission(actor: string, b: { code?: string; name?: string; description?: string; category?: string }) {
  const code = (b.code ?? '').trim().toLowerCase();
  if (code.length > 80 || !/^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/.test(code)) bad('A permission code looks like reports.view: lower case words joined by dots.', 'bad-code');
  if (permissions.some((p) => p.code === code)) bad(`The permission ${code} already exists.`, 'duplicate');
  if (!b.name?.trim()) bad('Give the permission a name people will understand.');
  permissions = [...permissions, { code, name: b.name!.trim(), description: b.description?.trim() || null, category: b.category?.trim() || 'Custom', system: false, roles: [] }];
  log(actor, 'PERMISSION_CREATED', code, b.name!.trim());
  return listPermissions().find((p) => p.code === code)!;
}

export function updatePermission(actor: string, code: string, b: { name?: string; description?: string; category?: string }) {
  const p = permissions.find((x) => x.code === code) ?? bad('No such permission.');
  if (p!.system) bad('Built-in permissions are part of the application and cannot be changed or deleted.', 'system-permission');
  if (!b.name?.trim()) bad('Give the permission a name people will understand.');
  permissions = permissions.map((x) => (x.code === code ? { ...x, name: b.name!.trim(), description: b.description?.trim() || null, category: b.category?.trim() || 'Custom' } : x));
  log(actor, 'PERMISSION_CHANGED', code, b.name!.trim());
  return listPermissions().find((x) => x.code === code)!;
}

export function deletePermission(actor: string, code: string) {
  const p = permissions.find((x) => x.code === code) ?? bad('No such permission.');
  if (p!.system) bad('Built-in permissions are part of the application and cannot be changed or deleted.', 'system-permission');
  const usedBy = roles.filter((r) => r.permissions.includes(code)).map((r) => r.name);
  permissions = permissions.filter((x) => x.code !== code);
  roles = roles.map((r) => ({ ...r, permissions: r.permissions.filter((x) => x !== code) }));
  log(actor, 'PERMISSION_DELETED', code, usedBy.length ? `Removed from: ${usedBy.join(', ')}` : null);
}

/** Same rule as RolePolicy.fits: staff roles for staff, otherwise the role's account type must match the organisation's. */
export function fits(kind: Role, orgType: string | null): boolean {
  if (['SUPER_ADMIN', 'MANAGER', 'ASSOCIATE'].includes(kind)) return orgType == null;
  if (kind === 'GUEST') return true;
  return kind === orgType;
}
