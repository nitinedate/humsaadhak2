import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import type { AccessChange, AccessPermission, AccessRole, Role, RoleKindOption } from '../../shared/api/types';
import { Drawer } from '../../shared/ui/Drawer';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

type Tab = 'roles' | 'permissions' | 'log';
/** The Super admin role always keeps these two (same rule as RolePolicy.java), so the platform can always be administered. */
const SUPER_ADMIN_KEEPS = ['admin.users', 'admin.roles'];

const useRoles = () => useQuery({ queryKey: ['access', 'roles'], queryFn: () => api<AccessRole[]>('/admin/access/roles') });
const usePermissions = () => useQuery({ queryKey: ['access', 'permissions'], queryFn: () => api<AccessPermission[]>('/admin/access/permissions') });

function useNotice() {
  const [msg, setMsg] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  return {
    ok: (text: string) => setMsg({ tone: 'success', text }),
    fail: (e: unknown) => setMsg({ tone: 'error', text: e instanceof ApiError ? e.message : 'Could not save. Try again.' }),
    clear: () => setMsg(null),
    node: msg && <div className={`alert ${msg.tone}`} role={msg.tone === 'error' ? 'alert' : 'status'}>{msg.text}</div>,
  };
}

/** Super admins: roles, the permissions each grants, custom roles and permissions, and a log of every change. */
export default function RolesPage() {
  const [tab, setTab] = useState<Tab>('roles');
  return (
    <div className="page">
      <div>
        <span className="eyebrow">What each role can do on HumSaadhak</span>
        <h1 className="title">Roles and permissions</h1>
      </div>
      <div role="tablist" aria-label="Show" className="segmented">
        <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'roles'} onClick={() => setTab('roles')}>Roles</button>
        <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'permissions'} onClick={() => setTab('permissions')}>Permissions</button>
        <button type="button" role="tab" className="chip-btn" aria-selected={tab === 'log'} onClick={() => setTab('log')}>Change log</button>
      </div>
      {tab === 'roles' ? <Roles /> : tab === 'permissions' ? <Permissions /> : <ChangeLog />}
      <ActivityPanel categories={['role', 'permission']} />
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------- roles
function Roles() {
  const qc = useQueryClient();
  const roles = useRoles();
  const perms = usePermissions();
  const kinds = useQuery({ queryKey: ['access', 'kinds'], queryFn: () => api<RoleKindOption[]>('/admin/access/kinds') });
  const [selected, setSelected] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const notice = useNotice();
  const refresh = () => { qc.invalidateQueries({ queryKey: ['access'] }); qc.invalidateQueries({ queryKey: ['me'] }); };

  useEffect(() => { if (!selected && roles.data?.length) setSelected(roles.data[0]!.code); }, [roles.data, selected]);
  const role = roles.data?.find((r) => r.code === selected) ?? null;

  if (roles.isLoading || perms.isLoading) return <Skeleton height={320} />;
  if (roles.error) return <ErrorNote error={roles.error} />;
  if (perms.error) return <ErrorNote error={perms.error} />;

  return (
    <>
      {notice.node}
      <div className="split">
        <section className="list" aria-label="Roles" style={{ flex: '1 1 320px' }}>
          <button type="button" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} onClick={() => { notice.clear(); setCreating(true); }}>New role</button>
          {roles.data!.map((r) => (
            <button key={r.code} type="button" className="row-btn" aria-current={r.code === selected} onClick={() => { notice.clear(); setSelected(r.code); }}>
              <span className="grow">
                <strong>{r.name}</strong>
                <span className="sub">{r.kindLabel}</span>
                <span className="sub">{r.permissions.length} {r.permissions.length === 1 ? 'permission' : 'permissions'} · {r.users} {r.users === 1 ? 'person' : 'people'}</span>
              </span>
              <span className={`pill ${r.system ? 'neutral' : 'info'}`}>{r.system ? 'Built-in' : 'Custom'}</span>
            </button>
          ))}
        </section>
        {role && <RoleEditor key={role.code} role={role} permissions={perms.data!} onSaved={(t) => { notice.ok(t); refresh(); }} onError={notice.fail}
                             onDeleted={(name) => { notice.ok(`Deleted the role “${name}”.`); setSelected(null); refresh(); }} />}
      </div>
      {creating && (
        <NewRole roles={roles.data!} kinds={kinds.data ?? []} onClose={() => setCreating(false)}
                 onCreated={(r) => { setCreating(false); setSelected(r.code); notice.ok(`Created the role “${r.name}”. Choose its permissions, then save.`); refresh(); }} />
      )}
    </>
  );
}

function RoleEditor({ role, permissions, onSaved, onError, onDeleted }: {
  role: AccessRole; permissions: AccessPermission[]; onSaved: (text: string) => void; onError: (e: unknown) => void; onDeleted: (name: string) => void;
}) {
  const [name, setName] = useState(role.name);
  const [description, setDescription] = useState(role.description ?? '');
  const [granted, setGranted] = useState<Set<string>>(new Set(role.permissions));
  const [filter, setFilter] = useState('');
  const locked = (code: string) => role.code === 'SUPER_ADMIN' && SUPER_ADMIN_KEEPS.includes(code);
  const dirty = name !== role.name || description !== (role.description ?? '') || granted.size !== role.permissions.length || role.permissions.some((p) => !granted.has(p));

  const groups = useMemo(() => {
    const f = filter.trim().toLowerCase();
    const shown = permissions.filter((p) => !f || `${p.name} ${p.code} ${p.description ?? ''}`.toLowerCase().includes(f));
    const out = new Map<string, AccessPermission[]>();
    shown.forEach((p) => out.set(p.category, [...(out.get(p.category) ?? []), p]));
    return [...out.entries()];
  }, [permissions, filter]);

  const save = useMutation({
    mutationFn: () => api<AccessRole>(`/admin/access/roles/${role.code}`, { method: 'PUT', json: { name: role.system ? undefined : name, description, permissions: [...granted] } }),
    onSuccess: (r) => onSaved(`Saved “${r.name}”. ${r.users ? `${r.users} ${r.users === 1 ? 'person was' : 'people were'} signed out so the change applies straight away.` : ''}`.trim()),
    onError,
  });
  const remove = useMutation({ mutationFn: () => api(`/admin/access/roles/${role.code}`, { method: 'DELETE' }), onSuccess: () => onDeleted(role.name), onError });
  const toggle = (code: string) => setGranted((s) => { const n = new Set(s); if (n.has(code)) n.delete(code); else n.add(code); return n; });

  return (
    <section className="side card" aria-label={`Role ${role.name}`} style={{ flex: '2 1 480px', position: 'static' }}>
      <div className="field-row">
        <div className="field">
          <label htmlFor="role-name">Role name</label>
          <input id="role-name" className="form-control" value={name} readOnly={role.system} onChange={(e) => setName(e.target.value)} />
          {role.system && <span className="hint">Built-in roles keep their names.</span>}
        </div>
        <div className="field">
          <span className="label">Type of account</span>
          <span style={{ minHeight: 44, display: 'flex', alignItems: 'center' }}>{role.kindLabel}</span>
          <span className="hint">Decides whose data people with this role work with and which dashboard they see.</span>
        </div>
      </div>
      <div className="field">
        <label htmlFor="role-desc">Description</label>
        <textarea id="role-desc" className="form-control" rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <div className="page-head" style={{ alignItems: 'center' }}>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}><AuditTrailButton type="ROLE" id={role.code} name={role.name} /></div>
        <h2 className="card-title">Permissions <span className="muted small">{granted.size} of {permissions.length} granted</span></h2>
        <div className="field" style={{ minWidth: 240 }}>
          <label htmlFor="perm-filter" className="visually-hidden">Filter permissions</label>
          <input id="perm-filter" className="form-control" type="search" placeholder="Filter permissions" value={filter} onChange={(e) => setFilter(e.target.value)} />
        </div>
      </div>
      {groups.map(([category, list]) => (
        <fieldset key={category} style={{ border: '1px solid var(--line)', borderRadius: 12, padding: '10px 14px', margin: 0 }}>
          <legend className="small" style={{ fontWeight: 700, padding: '0 6px' }}>{category}</legend>
          {list.map((p) => (
            <label key={p.code} style={{ display: 'flex', gap: 12, alignItems: 'flex-start', padding: '8px 0', borderBottom: '1px solid var(--line-soft)' }}>
              <input className="form-check-input" type="checkbox" checked={granted.has(p.code)} disabled={locked(p.code)} onChange={() => toggle(p.code)}
                     style={{ width: 18, height: 18, marginTop: 2 }} aria-describedby={`pd-${p.code}`} />
              <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span><strong>{p.name}</strong> <code className="small muted">{p.code}</code> {!p.system && <span className="pill info">Custom</span>}</span>
                <span id={`pd-${p.code}`} className="small muted">{locked(p.code) ? 'Always kept by Super admin, so the platform can always be administered.' : p.description}</span>
              </span>
            </label>
          ))}
        </fieldset>
      ))}
      <div className="segmented">
        <button type="button" className="btn btn-primary" disabled={!dirty || !name.trim() || save.isPending} onClick={() => save.mutate()}>Save role</button>
        <button type="button" className="btn btn-outline-secondary" disabled={!dirty} onClick={() => { setName(role.name); setDescription(role.description ?? ''); setGranted(new Set(role.permissions)); }}>Undo changes</button>
        {!role.system && (
          <button type="button" className="btn btn-outline-danger reject" style={{ marginLeft: 'auto' }} disabled={remove.isPending}
                  title={role.users ? 'Give these people another role first' : undefined}
                  onClick={() => { if (window.confirm(`Delete the role “${role.name}”?`)) remove.mutate(); }}>Delete role</button>
        )}
      </div>
      {role.users > 0 && <span className="small muted">{role.users} {role.users === 1 ? 'person has' : 'people have'} this role. Saving signs them out so the new permissions apply at once.</span>}
    </section>
  );
}

function NewRole({ roles, kinds, onClose, onCreated }: { roles: AccessRole[]; kinds: RoleKindOption[]; onClose: () => void; onCreated: (r: AccessRole) => void }) {
  const [f, setF] = useState({ name: '', description: '', kind: '' as Role | '', copyFrom: '' });
  const create = useMutation({ mutationFn: () => api<AccessRole>('/admin/access/roles', { method: 'POST', json: { ...f, copyFrom: f.copyFrom || null } }), onSuccess: onCreated });
  const submit = (e: FormEvent) => { e.preventDefault(); if (f.name.trim() && f.kind) create.mutate(); };
  const sameKind = roles.filter((r) => !f.kind || r.kind === f.kind || (['MANAGER', 'ASSOCIATE', 'SUPER_ADMIN'].includes(r.kind) && ['MANAGER', 'ASSOCIATE'].includes(f.kind)));
  return (
    <Drawer title="New role" onClose={onClose}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {create.error && <div className="alert alert-danger" role="alert">{create.error instanceof ApiError ? create.error.message : 'Could not create the role.'}</div>}
        <div className="field"><label htmlFor="nr-name">Role name</label><input id="nr-name" className="form-control" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="e.g. Regional coordinator" /></div>
        <div className="field"><label htmlFor="nr-desc">Description</label><textarea id="nr-desc" className="form-control" rows={2} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></div>
        <div className="field">
          <label htmlFor="nr-kind">Type of account</label>
          <select id="nr-kind" className="form-select" value={f.kind} onChange={(e) => setF({ ...f, kind: e.target.value as Role, copyFrom: '' })}>
            <option value="">Choose…</option>
            {kinds.map((k) => <option key={k.kind} value={k.kind}>{k.label}</option>)}
          </select>
          <span className="hint">Staff roles see every organisation; donor, school and NGO roles see only their own organisation’s data.</span>
        </div>
        <div className="field">
          <label htmlFor="nr-copy">Start with the permissions of</label>
          <select id="nr-copy" className="form-select" value={f.copyFrom} disabled={!f.kind} onChange={(e) => setF({ ...f, copyFrom: e.target.value })}>
            <option value="">No permissions (choose them next)</option>
            {sameKind.map((r) => <option key={r.code} value={r.code}>{r.name}</option>)}
          </select>
        </div>
        <button type="submit" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} disabled={!f.name.trim() || !f.kind || create.isPending}>Create role</button>
      </form>
    </Drawer>
  );
}

// ---------------------------------------------------------------------------------------------------- permissions
function Permissions() {
  const qc = useQueryClient();
  const perms = usePermissions();
  const notice = useNotice();
  const [editing, setEditing] = useState<AccessPermission | 'new' | null>(null);
  const refresh = () => qc.invalidateQueries({ queryKey: ['access'] });
  const remove = useMutation({
    mutationFn: (p: AccessPermission) => api(`/admin/access/permissions/${p.code}`, { method: 'DELETE' }),
    onSuccess: (_, p) => { notice.ok(`Deleted the permission ${p.code}${p.roles.length ? `; it was removed from ${p.roles.join(', ')}` : ''}.`); refresh(); },
    onError: notice.fail,
  });

  if (perms.isLoading) return <Skeleton height={320} />;
  if (perms.error) return <ErrorNote error={perms.error} />;
  const categories = [...new Set(perms.data!.map((p) => p.category))];

  return (
    <>
      {notice.node}
      <div className="alert alert-warning" style={{ fontWeight: 500 }}>
        Built-in permissions are checked by the application. A new permission can be granted to roles straight away and is included in each
        person’s rights; it takes effect wherever a feature or integration checks its code (for example <code>reports.view</code>).
      </div>
      <button type="button" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} onClick={() => { notice.clear(); setEditing('new'); }}>New permission</button>
      <div className="table-responsive table-wrap">
        <table className="table table-hover align-middle mb-0">
          <thead><tr><th>Permission</th><th>Area</th><th>Granted to</th><th>Type</th><th><span className="visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            {perms.data!.map((p) => (
              <tr key={p.code}>
                <td><strong>{p.name}</strong><div className="small muted"><code>{p.code}</code></div>{p.description && <div className="small muted">{p.description}</div>}</td>
                <td>{p.category}</td>
                <td>{p.roles.length ? <div className="why">{p.roles.map((r) => <span key={r}>{r}</span>)}</div> : <span className="muted small">No role yet</span>}</td>
                <td><span className={`pill ${p.system ? 'neutral' : 'info'}`}>{p.system ? 'Built-in' : 'Custom'}</span></td>
                <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                  {!p.system && (
                    <>
                      <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => { notice.clear(); setEditing(p); }}>Edit</button>{' '}
                      <button type="button" className="btn btn-outline-danger reject btn-sm" aria-label={`Delete ${p.code}`} disabled={remove.isPending}
                              onClick={() => { if (window.confirm(`Delete the permission ${p.code}?`)) remove.mutate(p); }}>Delete</button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {editing && (
        <PermissionForm permission={editing === 'new' ? null : editing} categories={categories} onClose={() => setEditing(null)}
                        onSaved={(p, created) => { setEditing(null); notice.ok(created ? `Created ${p.code}. Grant it to roles on the Roles tab.` : `Saved ${p.code}.`); refresh(); }} />
      )}
    </>
  );
}

function PermissionForm({ permission, categories, onClose, onSaved }: {
  permission: AccessPermission | null; categories: string[]; onClose: () => void; onSaved: (p: AccessPermission, created: boolean) => void;
}) {
  const [f, setF] = useState({ code: permission?.code ?? '', name: permission?.name ?? '', category: permission?.category ?? '', description: permission?.description ?? '' });
  const codeOk = /^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$/.test(f.code.trim().toLowerCase());
  const save = useMutation({
    mutationFn: () => permission
      ? api<AccessPermission>(`/admin/access/permissions/${permission.code}`, { method: 'PUT', json: f })
      : api<AccessPermission>('/admin/access/permissions', { method: 'POST', json: f }),
    onSuccess: (p) => onSaved(p, !permission),
  });
  return (
    <Drawer title={permission ? `Edit ${permission.code}` : 'New permission'} onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); if (codeOk && f.name.trim()) save.mutate(); }} style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {save.error && <div className="alert alert-danger" role="alert">{save.error instanceof ApiError ? save.error.message : 'Could not save.'}</div>}
        <div className="field">
          <label htmlFor="np-code">Code</label>
          <input id="np-code" className="form-control" value={f.code} readOnly={!!permission} placeholder="e.g. reports.view" onChange={(e) => setF({ ...f, code: e.target.value })} />
          <span className="hint" style={{ color: f.code && !codeOk ? 'var(--danger)' : undefined }}>Lower-case words joined by dots, such as field-visits.approve. It cannot be changed later.</span>
        </div>
        <div className="field"><label htmlFor="np-name">Name</label><input id="np-name" className="form-control" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="e.g. View reports" /></div>
        <div className="field">
          <label htmlFor="np-cat">Area</label>
          <input id="np-cat" className="form-control" list="np-cats" value={f.category} onChange={(e) => setF({ ...f, category: e.target.value })} placeholder="Custom" />
          <datalist id="np-cats">{categories.map((c) => <option key={c} value={c} />)}</datalist>
        </div>
        <div className="field"><label htmlFor="np-desc">What it allows</label><textarea id="np-desc" className="form-control" rows={3} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} /></div>
        <button type="submit" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} disabled={!codeOk || !f.name.trim() || save.isPending}>{permission ? 'Save permission' : 'Create permission'}</button>
      </form>
    </Drawer>
  );
}

// ---------------------------------------------------------------------------------------------------- change log
const ACTION: Record<string, string> = {
  ROLE_CREATED: 'Role created', ROLE_PERMISSIONS_CHANGED: 'Role permissions changed', ROLE_DELETED: 'Role deleted', PERMISSION_CREATED: 'Permission created',
  PERMISSION_CHANGED: 'Permission changed', PERMISSION_DELETED: 'Permission deleted', USER_ROLE_CHANGED: 'Person’s role changed',
};

function ChangeLog() {
  const log = useQuery({ queryKey: ['access', 'changes'], queryFn: () => api<AccessChange[]>('/admin/access/changes') });
  if (log.isLoading) return <Skeleton height={200} />;
  if (log.error) return <ErrorNote error={log.error} />;
  return log.data!.length === 0 ? <div className="card empty">No changes yet.</div> : (
    <div className="table-responsive table-wrap">
      <table className="table table-hover align-middle mb-0">
        <thead><tr><th>When</th><th>Who</th><th>What</th><th>Role, permission or person</th><th>Details</th></tr></thead>
        <tbody>
          {log.data!.map((c, i) => (
            <tr key={i}>
              <td style={{ whiteSpace: 'nowrap' }}>{new Date(c.at).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}</td>
              <td>{c.actor}</td><td>{ACTION[c.action] ?? c.action}</td><td><strong>{c.subject}</strong></td><td className="small">{c.details ?? '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
