import { useState, type FormEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '../../app/auth';
import { ApiError, DEMO } from '../../shared/api/client';
import { DEMO_ACCOUNTS } from '../../shared/api/demoAccounts';
import { ROLE_LABEL } from '../../shared/format';
import { FlipLink, useFlip } from './flip';

export function LoginPage() {
  const { signIn } = useAuth();
  const { flipTo } = useFlip();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent, override?: string) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await signIn(override ?? email, password || 'demo');
      flipTo(from, { replace: true, intoApp: true });   // the card flips away and the app flips in
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not sign in. Try again.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
        <form onSubmit={submit} noValidate>
          <h2 className="title" style={{ fontSize: 30 }}>Sign in</h2>
          {error && <div className="alert alert-danger" role="alert">{error}</div>}
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" className="form-control" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" className="form-control" type="password" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button className="btn btn-primary" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 14 }}>
            <FlipLink to="/forgot-password">Forgot your password?</FlipLink>
            <FlipLink to="/signup">Create an account</FlipLink>
          </div>
          {DEMO && (
            <div className="card" style={{ marginTop: 8 }}>
              <h2 className="card-title">Try a role (demo data)</h2>
              <div className="segmented">
                {DEMO_ACCOUNTS.map((a) => (
                  <button key={a.key} type="button" className="chip-btn" disabled={busy} onClick={(e) => submit(e, a.email)}>
                    {ROLE_LABEL[a.role]}
                  </button>
                ))}
              </div>
            </div>
          )}
        </form>
    </>
  );
}
