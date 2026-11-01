import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { api } from '../../shared/api/client';
import { FlipLink } from './flip';

/** Always shows the same message, so nobody can use this page to find out which emails have accounts. */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const send = useMutation({ mutationFn: () => api('/auth/password-reset', { method: 'POST', json: { email } }) });
  return (
    <>
      {send.isSuccess || send.isError ? (
        <div className="panel" role="status">
          <h2 className="title" style={{ fontSize: 30 }}>Check your email</h2>
          <p style={{ margin: 0, lineHeight: 1.6 }}>If <strong>{email}</strong> has an account, we have sent a link to set a new password. It works once and expires in 30 minutes.</p>
          <FlipLink to="/signin" className="btn btn-outline-secondary">Back to sign in</FlipLink>
        </div>
      ) : (
        <form className="panel" onSubmit={(e: FormEvent) => { e.preventDefault(); send.mutate(); }}>
          <h2 className="title" style={{ fontSize: 30 }}>Forgot your password?</h2>
          <div className="field"><label htmlFor="fp-email">Email</label><input id="fp-email" className="form-control" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} /></div>
          <button type="submit" className="btn btn-primary" disabled={!email.includes('@') || send.isPending}>Email me a link</button>
          <FlipLink to="/signin" className="small">Back to sign in</FlipLink>
        </form>
      )}
    </>
  );
}
