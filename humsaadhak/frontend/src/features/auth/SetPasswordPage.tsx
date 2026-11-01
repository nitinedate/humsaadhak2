import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../../shared/api/client';
import { FlipLink } from './flip';
import { passwordProblems } from './passwordRules';

/** Opened from the invitation or reset email; the token is single-use and expires. */
export default function SetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [repeat, setRepeat] = useState('');
  const problems = [...passwordProblems(password), ...(repeat && repeat !== password ? ['The two passwords are different.'] : [])];
  const save = useMutation({ mutationFn: () => api('/auth/set-password', { method: 'POST', json: { token, password } }) });

  return (
    <>
      {save.isSuccess ? (
        <div className="panel" role="status">
          <h2 className="title" style={{ fontSize: 30 }}>Password set</h2>
          <p style={{ margin: 0 }}>You can sign in now.</p>
          <FlipLink to="/signin" className="btn btn-primary">Sign in</FlipLink>
        </div>
      ) : (
        <form className="panel" noValidate onSubmit={(e: FormEvent) => { e.preventDefault(); if (!problems.length && repeat) save.mutate(); }}>
          <h2 className="title" style={{ fontSize: 30 }}>Set your password</h2>
          {!token && <div className="alert alert-danger" role="alert">This link is incomplete. Open the link from your email again.</div>}
          {save.error && <div className="alert alert-danger" role="alert">{save.error instanceof ApiError ? save.error.message : 'Could not save. The link may have expired.'}</div>}
          <div className="field"><label htmlFor="pw1">New password</label><input id="pw1" className="form-control" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} aria-describedby="pw-rules" /></div>
          <div className="field"><label htmlFor="pw2">Type it again</label><input id="pw2" className="form-control" type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} /></div>
          <ul id="pw-rules" className="small" style={{ margin: 0, paddingLeft: 18, color: problems.length ? 'var(--danger)' : 'var(--success)' }}>
            {(password ? problems : ['At least 12 characters, with a number or symbol.']).map((p) => <li key={p}>{p}</li>)}
            {password && !problems.length && <li>Looks good.</li>}
          </ul>
          <button type="submit" className="btn btn-primary" disabled={!token || !!problems.length || !repeat || save.isPending}>Set password</button>
        </form>
      )}
    </>
  );
}
