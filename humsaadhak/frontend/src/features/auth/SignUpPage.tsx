import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api, ApiError } from '../../shared/api/client';
import { useStates } from '../../shared/api/reference';
import type { Role } from '../../shared/api/types';
import { FlipLink } from './flip';

const WHO: { role: Role; label: string; hint: string }[] = [
  { role: 'DONOR_INDIVIDUAL', label: 'Individual donor', hint: 'You give in your own name.' },
  { role: 'DONOR_CORPORATE', label: 'Corporate donor', hint: 'Companies, CSR teams, foundations and trusts.' },
  { role: 'SCHOOL', label: 'School (direct beneficiary)', hint: 'Government, aided or low-fee schools that receive help directly.' },
  { role: 'NGO', label: 'NGO (indirect beneficiary)', hint: 'Registered trusts, societies and section 8 companies working with communities.' },
];
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Three short steps; HumSaadhak approves the account, then emails a link to set a password (no temporary passwords). */
export default function SignUpPage() {
  const states = useStates();
  const [step, setStep] = useState(0);
  const [f, setF] = useState({ role: '' as Role | '', organisationName: '', city: '', state: '', fullName: '', email: '', mobile: '', consent: false });
  const set = (patch: Partial<typeof f>) => setF((x) => ({ ...x, ...patch }));
  const individual = f.role === 'DONOR_INDIVIDUAL';

  const send = useMutation({ mutationFn: () => api('/registrations', { method: 'POST', json: { ...f, organisationName: individual ? f.fullName : f.organisationName } }) });

  const problems = [
    !f.role ? 'Choose who you are.' : null,
    !individual && step >= 1 && !f.organisationName.trim() ? 'Enter the organisation name.' : null,
    step >= 1 && !f.state ? 'Choose a state.' : null,
    step >= 2 && !f.fullName.trim() ? 'Enter your name.' : null,
    step >= 2 && !EMAIL.test(f.email) ? 'Enter a valid email address.' : null,
    step >= 2 && !f.consent ? 'Please agree to the terms to continue.' : null,
  ].filter(Boolean) as string[];

  if (send.isSuccess) {
    return (
      <>
        <div className="panel" role="status">
          <h2 className="title" style={{ fontSize: 30 }}>Thank you, {f.fullName.split(' ')[0]}</h2>
          <p style={{ margin: 0, lineHeight: 1.6 }}>We have your request. A HumSaadhak team member checks every new account, usually within two working days. When it is approved we email <strong>{f.email}</strong> a link to set your password.</p>
          <FlipLink to="/signin" className="btn btn-outline-secondary">Back to sign in</FlipLink>
        </div>
      </>
    );
  }

  return (
    <>
      <form className="panel" noValidate onSubmit={(e) => { e.preventDefault(); if (problems.length === 0) (step < 2 ? setStep(step + 1) : send.mutate()); }}>
        <ol className="steps" aria-label={`Step ${step + 1} of 3`}>{[0, 1, 2].map((i) => <li key={i} data-done={i <= step} />)}</ol>
        <h2 className="title" style={{ fontSize: 30 }}>{['Who are you?', individual ? 'Where are you?' : 'Your organisation', 'How do we reach you?'][step]}</h2>
        {send.error && <div className="alert alert-danger" role="alert">{send.error instanceof ApiError ? send.error.message : 'Could not send. Try again.'}</div>}

        {step === 0 && (
          <div role="radiogroup" aria-label="Who are you" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {WHO.map((w) => (
              <button key={w.role} type="button" role="radio" aria-checked={f.role === w.role} className="row-btn" aria-current={f.role === w.role}
                      onClick={() => set({ role: w.role })}>
                <span className="grow"><strong>{w.label}</strong><span className="sub">{w.hint}</span></span>
              </button>
            ))}
          </div>
        )}
        {step === 1 && (
          <>
            {!individual && <div className="field"><label htmlFor="s-org">Organisation name</label><input id="s-org" className="form-control" value={f.organisationName} onChange={(e) => set({ organisationName: e.target.value })} /></div>}
            <div className="field-row">
              <div className="field"><label htmlFor="s-city">City</label><input id="s-city" className="form-control" value={f.city} onChange={(e) => set({ city: e.target.value })} /></div>
              <div className="field"><label htmlFor="s-state">State</label>
                <select id="s-state" className="form-select" value={f.state} onChange={(e) => set({ state: e.target.value })}>
                  <option value="">Choose…</option>{states.data?.map((s) => <option key={s.code} value={s.name}>{s.name}</option>)}
                </select></div>
            </div>
          </>
        )}
        {step === 2 && (
          <>
            <div className="field"><label htmlFor="s-name">Your full name</label><input id="s-name" className="form-control" autoComplete="name" value={f.fullName} onChange={(e) => set({ fullName: e.target.value })} /></div>
            <div className="field"><label htmlFor="s-email">Email</label><input id="s-email" className="form-control" type="email" autoComplete="email" value={f.email} onChange={(e) => set({ email: e.target.value })} /></div>
            <div className="field"><label htmlFor="s-mobile">Mobile (optional)</label><input id="s-mobile" className="form-control" type="tel" autoComplete="tel" value={f.mobile} onChange={(e) => set({ mobile: e.target.value })} /></div>
            <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: 14 }}>
              <input className="form-check-input" type="checkbox" checked={f.consent} onChange={(e) => set({ consent: e.target.checked })} style={{ width: 18, height: 18, marginTop: 2 }} />
              <span>I agree to the terms and the privacy notice, and to HumSaadhak contacting me about my account.</span>
            </label>
          </>
        )}
        {problems.length > 0 && step > 0 && <span className="small" style={{ color: 'var(--danger)' }}>{problems[0]}</span>}
        <div className="segmented">
          {step > 0 && <button type="button" className="btn btn-outline-secondary" onClick={() => setStep(step - 1)}>Back</button>}
          <button type="submit" className="btn btn-primary" style={{ flex: 1 }} disabled={problems.length > 0 || send.isPending}>
            {step < 2 ? 'Continue' : send.isPending ? 'Sending…' : 'Send for approval'}
          </button>
        </div>
        <span className="small">Already have an account? <FlipLink to="/signin">Sign in</FlipLink></span>
      </form>
    </>
  );
}
