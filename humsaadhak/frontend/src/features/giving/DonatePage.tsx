import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../app/auth';
import { api, ApiError } from '../../shared/api/client';
import type { DonationResult, OpenNeed, OrganisationOption, Periodicity } from '../../shared/api/types';
import { inr, PERIODICITY_LABEL } from '../../shared/format';
import { SearchSelect } from '../../shared/ui/SearchSelect';
import { parseRupees } from '../catalogue/forms';
import { ActivityPanel } from '../../shared/ui/AuditTrail';

/** Amount problems, same rule as the server's DonationCalculator: above zero and not more than still needed. */
export function amountProblem(amount: number | null, remaining: number | null): string | null {
  if (amount == null || amount <= 0) return 'Enter an amount above zero.';
  if (remaining != null && amount > remaining) return `This cause or project needs ${inr(remaining)} at most.`;
  return null;
}

/**
 * Records a payment made by bank transfer, UPI or cheque. Causes, projects and (for staff) donors come from the API;
 * the Idempotency-Key makes a double submit harmless.
 */
export default function DonatePage() {
  const { me } = useAuth();
  const staff = me?.role === 'MANAGER' || me?.role === 'ASSOCIATE' || me?.role === 'SUPER_ADMIN';
  const [params] = useSearchParams();
  const matchId = params.get('matchId');
  const [needId, setNeedId] = useState<number | null>(params.get('needId') ? Number(params.get('needId')) : null);
  const [donorId, setDonorId] = useState<number | null>(null);
  const [amount, setAmount] = useState('');
  const [periodicity, setPeriodicity] = useState<Periodicity>('ONE_TIME');
  const [paymentRef, setPaymentRef] = useState('');
  const [touched, setTouched] = useState(false);
  const idempotencyKey = useMemo(() => crypto.randomUUID(), []);

  const needs = useQuery({ queryKey: ['lookups', 'open-needs'], queryFn: () => api<OpenNeed[]>('/lookups/open-needs') });
  const donors = useQuery({ queryKey: ['lookups', 'donors'], enabled: staff, queryFn: () => api<OrganisationOption[]>('/lookups/organisations?group=DONORS') });
  const need = needs.data?.find((n) => n.id === needId) ?? null;
  const cameFromMatch = !!params.get('needId');

  // Suggest what is still needed once the cause or project is known (the person can change it).
  useEffect(() => { if (need && !amount) setAmount(String(need.remaining)); }, [need, amount]);

  const paid = parseRupees(amount);
  const problem = !needId ? 'Choose the cause or project.' : staff && !donorId ? 'Choose the donor who made this payment.'
    : amountProblem(paid, need?.remaining ?? null) ?? (!paymentRef.trim() ? 'Enter the UTR or cheque number.' : null);

  const save = useMutation({
    mutationFn: () => api<DonationResult>('/donations', {
      method: 'POST', headers: { 'Idempotency-Key': idempotencyKey },
      json: { donorOrgId: staff ? donorId : null, needId, matchId: matchId ? Number(matchId) : null, paidAmount: paid, periodicity, paymentRef: paymentRef.trim() },
    }),
  });
  const submit = (e: FormEvent) => { e.preventDefault(); setTouched(true); if (!problem) save.mutate(); };

  if (save.data) {
    const r = save.data;
    return (
      <div className="page">
        <h1 className="title">Donation recorded</h1>
        <div className="card">
          <strong className="num" style={{ fontSize: 20 }}>{r.refNo} · {inr(r.paid)}</strong>
          {need && <span>To <strong>{need.name}</strong> ({need.orgName})</span>}
          {r.commitmentLeft != null && <span>Left in this cycle’s commitment: <strong>{inr(r.commitmentLeft)}</strong></span>}
          {r.nextDue && <span>Next instalment due on <strong>{new Date(r.nextDue).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })}</strong>. We’ll remind you before then.</span>}
          <span className="muted">HumSaadhak checks the payment reference, then the beneficiary uploads an acknowledgement.</span>
          <Link to="/giving" className="btn btn-outline-secondary" style={{ alignSelf: 'flex-start' }}>Back to my giving</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="page">
      <div>
        <span className="eyebrow">Paid by bank transfer, UPI or cheque</span>
        <h1 className="title">Record donation</h1>
      </div>
      <form className="card" onSubmit={submit} noValidate>
        {save.error && <div className="alert alert-danger" role="alert">{save.error instanceof ApiError ? save.error.message : 'Could not save. Try again.'}</div>}
        {staff && (
          <SearchSelect label="Donor" loading={donors.isLoading} value={donorId} onChange={setDonorId} placeholder="Search donors by name or reference"
                        options={(donors.data ?? []).map((d) => ({ value: d.id, label: d.name, detail: `${d.group}, ${d.refNo}` }))} />
        )}
        {cameFromMatch && need ? (
          <div className="field">
            <span className="label">Cause or project</span>
            <div className="file-chosen" style={{ background: 'var(--surface-2)', borderColor: 'var(--line)' }}>
              <span className="file-meta"><strong>{need.name}</strong><span className="small muted">{need.orgName} · {need.orgGroup} · still needs {inr(need.remaining)}</span></span>
              <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setNeedId(null)}>Change</button>
            </div>
          </div>
        ) : (
          <SearchSelect label="Cause or project" loading={needs.isLoading} value={needId} placeholder="Search by name, school or NGO"
                        onChange={(v) => { setNeedId(v); setAmount(''); }}
                        hint={need ? `${need.orgName} (${need.orgGroup}) still needs ${inr(need.remaining)} of ${inr(need.targetAmount)}.` : 'Only approved causes and projects that still need money are listed.'}
                        options={(needs.data ?? []).map((n) => ({ value: n.id, label: n.name, detail: `${n.orgName}, still needs ${inr(n.remaining)}` }))} />
        )}
        {cameFromMatch && needs.isSuccess && !need && <div className="alert alert-warning">This cause or project is already fully funded. Choose another one.</div>}
        <div className="field">
          <label htmlFor="amount">Amount (₹)</label>
          <input id="amount" className="form-control num" inputMode="numeric" value={amount} onChange={(e) => setAmount(e.target.value)} />
          {need && (
            <div className="segmented" role="group" aria-label="Suggested amounts">
              {[...new Set([Math.min(100000, need.remaining), Math.round(need.remaining / 2), need.remaining])].filter((v) => v > 0).map((v) => (
                <button key={v} type="button" className="chip-btn" aria-pressed={paid === v} onClick={() => setAmount(String(v))}>
                  {v === need.remaining ? `Complete it: ${inr(v)}` : inr(v)}
                </button>
              ))}
            </div>
          )}
        </div>
        <div className="field">
          <label htmlFor="period">Repeat</label>
          <select id="period" className="form-select" value={periodicity} onChange={(e) => setPeriodicity(e.target.value as Periodicity)}>
            {Object.entries(PERIODICITY_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </div>
        <div className="field">
          <label htmlFor="ref">UTR or cheque number</label>
          <input id="ref" className="form-control" value={paymentRef} onChange={(e) => setPaymentRef(e.target.value)} placeholder="e.g. SBIN526871234" />
        </div>
        {touched && problem && <span className="small" style={{ color: 'var(--danger)' }} role="alert">{problem}</span>}
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Saving…' : `Record ${paid && paid > 0 ? inr(paid) : ''} donation`}
        </button>
      </form>
      <ActivityPanel categories={['donation']} />
    </div>
  );
}
