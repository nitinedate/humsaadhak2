import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { AccessRole, AdminUserRow, PageResponse, Role } from '../../shared/api/types';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { StatusPill } from '../../shared/ui/StatusPill';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';
import { Drawer } from '../../shared/ui/Drawer';

const STAFF: Role[] = ['SUPER_ADMIN', 'MANAGER', 'ASSOCIATE'];

/** Roles a person may be given: staff roles for staff, otherwise roles of their account type (same rule as RolePolicy.fits). */
export function rolesFor(user: Pick<AdminUserRow, 'role'>, roles: AccessRole[]): AccessRole[] {
  const staff = STAFF.includes(user.role);
  return roles.filter((r) => (staff ? STAFF.includes(r.kind) : r.kind === user.role) && r.kind !== 'GUEST');
}

/** Super admins: find people, change roles, disable or re-enable accounts, resend invitations. */
export default function UsersPage() {
  const { me } = useAuth();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const list = useQuery({
    queryKey: ['admin-users', search],
    queryFn: () => api<PageResponse<AdminUserRow>>(`/admin/users${search ? `?q=${encodeURIComponent(search)}` : ''}`),
  });
  const [msg, setMsg] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [adding, setAdding] = useState(false);
  const roles = useQuery({ queryKey: ['access', 'roles'], queryFn: () => api<AccessRole[]>('/admin/access/roles') });

  const act = useMutation({
    mutationFn: ({ u, action, role }: { u: AdminUserRow; action: 'role' | 'disable' | 'enable' | 'invite'; role?: AccessRole }) =>
      action === 'role' ? api(`/admin/users/${u.id}/role`, { method: 'PUT', json: { roleCode: role!.code } }) : api(`/admin/users/${u.id}/${action}`, { method: 'POST' }),
    onSuccess: (_, v) => {
      // build only the message for this action (building them all read v.role on a disable and crashed)
      const text = v.action === 'role' ? `${v.u.fullName} is now ${v.role!.name}. They were signed out so the new role applies at once.`
        : v.action === 'disable' ? `${v.u.fullName} can no longer sign in.`
        : v.action === 'enable' ? `${v.u.fullName} can sign in again.`
        : `A new set-password link was emailed to ${v.u.email}.`;
      setMsg({ tone: 'success', text });
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      qc.invalidateQueries({ queryKey: ['access'] });
    },
    onError: (e) => setMsg({ tone: 'error', text: e instanceof ApiError ? e.message : 'Could not save.' }),
  });

  return (
    <div className="page">
      <div className="page-head">
        <h1 className="title">Users</h1>
        <form className="field-row" onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); }} role="search">
          <div className="field" style={{ minWidth: 280 }}>
            <label htmlFor="u-q" className="visually-hidden">Search users</label>
            <input id="u-q" className="form-control" type="search" placeholder="Name or email" value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <button type="submit" className="btn btn-outline-secondary">Search</button>
          <button type="button" className="btn btn-primary" disabled={!roles.data} onClick={() => setAdding(true)}>Add staff member</button>
        </form>
      </div>
      {adding && roles.data && (
        <AddStaff roles={roles.data} onClose={() => setAdding(false)}
                  onDone={(text) => { setAdding(false); setMsg({ tone: 'success', text }); }} />
      )}
      {msg && <div className={`alert ${msg.tone}`} role={msg.tone === 'error' ? 'alert' : 'status'}>{msg.text}</div>}
      {list.isLoading && <Skeleton height={240} />}
      {list.error && <ErrorNote error={list.error} />}
      {list.data && (
        <div className="table-responsive table-wrap">
          <table className="table table-hover align-middle mb-0">
            <thead><tr><th>Name</th><th>Role</th><th>Status</th><th>Password set</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
            <tbody>
              {list.data.items.length === 0 && <tr><td colSpan={5} className="empty">No users found.</td></tr>}
              {list.data.items.map((u) => {
                const self = u.id === me?.id;
                return (
                  <tr key={u.id}>
                    <td><strong>{u.fullName}</strong><div className="small muted">{u.email}</div></td>
                    <td>
                      <label htmlFor={`role-${u.id}`} className="visually-hidden">Role of {u.fullName}</label>
                      <select id={`role-${u.id}`} className="form-select" style={{ minHeight: 36, padding: '4px 10px' }} value={u.roleCode} disabled={self || act.isPending || !roles.data}
                              onChange={(e) => act.mutate({ u, action: 'role', role: roles.data!.find((r) => r.code === e.target.value)! })}>
                        {!roles.data && <option value={u.roleCode}>{u.roleName}</option>}
                        {roles.data && rolesFor(u, roles.data).map((r) => <option key={r.code} value={r.code}>{r.name}{r.system ? '' : ' (custom)'}</option>)}
                      </select>
                    </td>
                    <td><StatusPill status={u.status} /></td>
                    <td>{u.hasPassword ? 'Yes' : 'Not yet'}</td>
                    <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                      <AuditTrailButton type="USER" id={u.id} name={u.fullName} />{' '}
                      {u.status === 'ACTIVE' && !u.hasPassword && <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => act.mutate({ u, action: 'invite' })}>Resend invitation</button>}{' '}
                      {!self && (u.status === 'DISABLED'
                        ? <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => act.mutate({ u, action: 'enable' })}>Enable</button>
                        : u.status === 'ACTIVE' && <button type="button" className="btn btn-outline-danger reject btn-sm" onClick={() => act.mutate({ u, action: 'disable' })}>Disable</button>)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      <ActivityPanel categories={['user']} />
    </div>
  );
}

/** HumSaadhak staff do not sign up: a super admin adds them, and they receive a single-use set-password link by email. */
function AddStaff({ roles, onClose, onDone }: { roles: AccessRole[]; onClose: () => void; onDone: (text: string) => void }) {
  const staffRoles = roles.filter((r) => STAFF.includes(r.kind));
  const [form, setForm] = useState({ fullName: '', email: '', mobile: '', roleCode: staffRoles.find((r) => r.code === 'ASSOCIATE')?.code ?? staffRoles[0]?.code ?? '' });
  const [error, setError] = useState<string | null>(null);
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: () => api<AdminUserRow>('/admin/users', { method: 'POST', json: { ...form, mobile: form.mobile.trim() || null } }),
    onSuccess: (u) => {
      qc.invalidateQueries({ queryKey: ['admin-users'] });
      onDone(`${u.fullName} was added as ${u.roleName}. A set-password link was emailed to ${u.email}.`);
    },
    onError: (e) => setError(e instanceof ApiError ? e.message : 'Could not add this person.'),
  });
  const set = (k: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [k]: e.target.value });
  return (
    <Drawer title="Add staff member" onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); setError(null); save.mutate(); }} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        <p className="small muted">Staff do not sign up themselves. The person gets an email with a single-use link to choose their password (valid 72 hours).</p>
        <div className="field"><label htmlFor="ns-name">Full name</label><input id="ns-name" className="form-control" required value={form.fullName} onChange={set('fullName')} /></div>
        <div className="field"><label htmlFor="ns-email">Email</label><input id="ns-email" className="form-control" type="email" required value={form.email} onChange={set('email')} /></div>
        <div className="field"><label htmlFor="ns-mobile">Mobile (optional)</label><input id="ns-mobile" className="form-control" type="tel" value={form.mobile} onChange={set('mobile')} /></div>
        <div className="field">
          <label htmlFor="ns-role">Role</label>
          <select id="ns-role" className="form-select" value={form.roleCode} onChange={set('roleCode')}>
            {staffRoles.map((r) => <option key={r.code} value={r.code}>{r.name}{r.system ? '' : ' (custom)'}</option>)}
          </select>
        </div>
        {error && <div className="alert alert-danger" role="alert">{error}</div>}
        <div className="d-flex flex-wrap gap-2">
          <button type="submit" className="btn btn-primary" disabled={save.isPending || !form.fullName.trim() || !form.email.trim() || !form.roleCode}>
            {save.isPending ? 'Adding…' : 'Add and send invitation'}
          </button>
          <button type="button" className="btn btn-outline-secondary" onClick={onClose}>Cancel</button>
        </div>
      </form>
    </Drawer>
  );
}

