import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import { useStates } from '../../shared/api/reference';
import type { BankRow, ContactRow, OrgSummary } from '../../shared/api/types';
import { ErrorNote, Skeleton } from '../../shared/ui/States';
import { ActivityPanel, AuditTrailButton } from '../../shared/ui/AuditTrail';

const IFSC = /^[A-Za-z]{4}0[A-Za-z0-9]{6}$/;
export const isIfsc = (v: string) => IFSC.test(v.trim());
export const isAccountNumber = (v: string) => /^[0-9]{6,18}$/.test(v.trim());

/** Profile, contacts and bank accounts of the signed-in person's organisation. */
export default function OrganisationPage() {
  const qc = useQueryClient();
  const org = useQuery({ queryKey: ['org', 'me'], queryFn: () => api<OrgSummary>('/organisations/me') });
  const id = org.data?.id;
  const contacts = useQuery({ queryKey: ['org', id, 'contacts'], enabled: !!id, queryFn: () => api<ContactRow[]>(`/organisations/${id}/contacts`) });
  const banks = useQuery({ queryKey: ['org', id, 'banks'], enabled: !!id, queryFn: () => api<BankRow[]>(`/organisations/${id}/bank-accounts`) });
  const states = useStates();

  const [profile, setProfile] = useState({ name: '', city: '', state: '' });
  const [rows, setRows] = useState<ContactRow[]>([]);
  const [bank, setBank] = useState({ accountName: '', accountNumber: '', ifsc: '', bankName: '' });
  const [msg, setMsg] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  useEffect(() => { if (org.data) setProfile({ name: org.data.name, city: org.data.city ?? '', state: org.data.state ?? '' }); }, [org.data]);
  useEffect(() => { if (contacts.data) setRows(contacts.data); }, [contacts.data]);

  const fail = (e: unknown) => setMsg({ tone: 'error', text: e instanceof ApiError ? e.message : 'Could not save. Try again.' });
  const saveProfile = useMutation({
    mutationFn: () => api(`/organisations/${id}/profile`, { method: 'PUT', json: profile }),
    onSuccess: () => { setMsg({ tone: 'success', text: 'Profile saved.' }); qc.invalidateQueries({ queryKey: ['org', 'me'] }); }, onError: fail,
  });
  const saveContacts = useMutation({
    mutationFn: () => api(`/organisations/${id}/contacts`, { method: 'PUT', json: rows }),
    onSuccess: () => { setMsg({ tone: 'success', text: 'Contacts saved.' }); qc.invalidateQueries({ queryKey: ['org', id, 'contacts'] }); }, onError: fail,
  });
  const addBank = useMutation({
    mutationFn: () => api(`/organisations/${id}/bank-accounts`, { method: 'POST', json: bank }),
    onSuccess: () => { setMsg({ tone: 'success', text: 'Bank account added.' }); setBank({ accountName: '', accountNumber: '', ifsc: '', bankName: '' }); qc.invalidateQueries({ queryKey: ['org', id, 'banks'] }); },
    onError: fail,
  });
  const bankProblem = !bank.accountName.trim() ? 'Enter the account holder name.' : !isAccountNumber(bank.accountNumber) ? 'Account numbers are 6 to 18 digits.'
    : !isIfsc(bank.ifsc) ? 'IFSC looks like SBIN0001234.' : null;

  if (org.isLoading) return <div className="page"><Skeleton height={300} /></div>;
  if (org.error) return <div className="page"><ErrorNote error={org.error} /></div>;
  const o = org.data!;
  const setRow = (i: number, patch: Partial<ContactRow>) => setRows((r) => r.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  return (
    <div className="page">
      <div className="page-head">
        <div><span className="eyebrow">{o.refNo}</span><h1 className="title">{o.name}</h1></div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span className={`pill ${o.verified ? 'approved' : 'pending'}`}>{o.verified ? 'Verified' : 'Not verified yet'}</span>
          <AuditTrailButton type="ORGANISATION" id={o.id} name={o.name} />
        </div>
      </div>
      {msg && <div className={`alert ${msg.tone}`} role={msg.tone === 'error' ? 'alert' : 'status'}>{msg.text}</div>}

      {/* Wide screens: profile and bank accounts side by side, contacts below; smaller screens keep this order. */}
      <div className="row g-4">
      <div className="col-12 col-xl-6 order-xl-1">
      <form className="card h-100" onSubmit={(e: FormEvent) => { e.preventDefault(); saveProfile.mutate(); }} aria-labelledby="pr-h">
        <h2 id="pr-h" className="card-title">Profile</h2>
        <div className="field-row">
          <div className="field"><label htmlFor="o-name">Organisation name</label><input id="o-name" className="form-control" value={profile.name} onChange={(e) => setProfile({ ...profile, name: e.target.value })} /></div>
          <div className="field"><label htmlFor="o-city">City</label><input id="o-city" className="form-control" value={profile.city} onChange={(e) => setProfile({ ...profile, city: e.target.value })} /></div>
          <div className="field"><label htmlFor="o-state">State</label>
            <select id="o-state" className="form-select" value={profile.state} onChange={(e) => setProfile({ ...profile, state: e.target.value })}>
              <option value="">Choose…</option>{states.data?.map((s) => <option key={s.code} value={s.name}>{s.name}</option>)}
            </select></div>
        </div>
        <button type="submit" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} disabled={!profile.name.trim() || saveProfile.isPending}>Save profile</button>
      </form>
      </div>

      <div className="col-12 order-xl-3">
      <section className="card" aria-labelledby="ct-h">
        <h2 id="ct-h" className="card-title">Contacts</h2>
        {rows.map((c, i) => (
          <div key={i} className="field-row" style={{ paddingBottom: 12, borderBottom: '1px solid var(--line-soft)' }}>
            <div className="field" style={{ flex: '0 1 160px' }}><label htmlFor={`c-role-${i}`}>Role</label>
              <select id={`c-role-${i}`} className="form-select" value={c.role} onChange={(e) => setRow(i, { role: e.target.value as ContactRow['role'] })}>
                <option value="PRIMARY">Primary</option><option value="SECONDARY">Secondary</option><option value="SIGNATORY">Signatory</option>
              </select></div>
            <div className="field"><label htmlFor={`c-name-${i}`}>Name</label><input id={`c-name-${i}`} className="form-control" value={c.name} onChange={(e) => setRow(i, { name: e.target.value })} /></div>
            <div className="field"><label htmlFor={`c-email-${i}`}>Email</label><input id={`c-email-${i}`} type="email" className="form-control" value={c.email ?? ''} onChange={(e) => setRow(i, { email: e.target.value })} /></div>
            <div className="field"><label htmlFor={`c-phone-${i}`}>Phone</label><input id={`c-phone-${i}`} className="form-control" value={c.phone ?? ''} onChange={(e) => setRow(i, { phone: e.target.value })} /></div>
            <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setRows((r) => r.filter((_, j) => j !== i))}>Remove</button>
          </div>
        ))}
        <div className="segmented">
          <button type="button" className="btn btn-outline-secondary" onClick={() => setRows((r) => [...r, { role: r.length ? 'SECONDARY' : 'PRIMARY', name: '', designation: null, email: '', phone: '' }])}>Add contact</button>
          <button type="button" className="btn btn-primary" disabled={saveContacts.isPending || rows.some((r) => !r.name.trim())} onClick={() => saveContacts.mutate()}>Save contacts</button>
        </div>
      </section>
      </div>

      <div className="col-12 col-xl-6 order-xl-2">
      <section className="card h-100" aria-labelledby="bk-h">
        <h2 id="bk-h" className="card-title">Bank accounts</h2>
        <span className="small muted">Account numbers are stored securely and only ever shown masked.</span>
        {banks.data?.map((b) => (
          <div key={b.id} style={{ display: 'flex', flexWrap: 'wrap', gap: 16, justifyContent: 'space-between' }}>
            <strong>{b.accountName}</strong><span className="num">{b.accountNumber}</span><span>{b.ifsc}</span><span className="muted">{b.bankName}</span>
          </div>
        ))}
        <div className="field-row">
          <div className="field"><label htmlFor="b-name">Account holder</label><input id="b-name" className="form-control" value={bank.accountName} onChange={(e) => setBank({ ...bank, accountName: e.target.value })} /></div>
          <div className="field"><label htmlFor="b-no">Account number</label><input id="b-no" className="form-control num" inputMode="numeric" autoComplete="off" value={bank.accountNumber} onChange={(e) => setBank({ ...bank, accountNumber: e.target.value })} /></div>
          <div className="field"><label htmlFor="b-ifsc">IFSC</label><input id="b-ifsc" className="form-control" value={bank.ifsc} onChange={(e) => setBank({ ...bank, ifsc: e.target.value.toUpperCase() })} /></div>
          <div className="field"><label htmlFor="b-bank">Bank</label><input id="b-bank" className="form-control" value={bank.bankName} onChange={(e) => setBank({ ...bank, bankName: e.target.value })} /></div>
        </div>
        {bankProblem && (bank.accountName || bank.accountNumber || bank.ifsc) && <span className="small" style={{ color: 'var(--danger)' }}>{bankProblem}</span>}
        <button type="button" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} disabled={!!bankProblem || addBank.isPending} onClick={() => addBank.mutate()}>Add bank account</button>
      </section>
      </div>
      </div>
      <ActivityPanel categories={['organisation']} />
    </div>
  );
}
