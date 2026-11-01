import { useState } from 'react';
import { useAuth } from '../../app/auth';
import { THEMES, useTheme, type ThemeName } from '../../app/theme';
import { ApiError } from '../../shared/api/client';
import { ROLE_LABEL } from '../../shared/format';
import { ActivityPanel } from '../../shared/ui/AuditTrail';

/** Personal settings: colour theme (saved on the account) and account details. */
export default function SettingsPage() {
  const { me } = useAuth();
  const { theme, choose } = useTheme();
  const [status, setStatus] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);
  const [saving, setSaving] = useState<ThemeName | null>(null);

  async function pick(name: ThemeName) {
    if (name === theme || saving) return;
    setSaving(name);
    setStatus(null);
    try {
      await choose(name);
      setStatus({ tone: 'success', text: `${THEMES.find((t) => t.name === name)!.label} theme saved. It will be used every time you sign in.` });
    } catch (e) {
      setStatus({ tone: 'error', text: e instanceof ApiError ? e.message : 'Could not save the theme. Try again.' });
    } finally {
      setSaving(null);
    }
  }

  return (
    <div className="page">
      <h1 className="title">Settings</h1>

      <section className="card" aria-labelledby="theme-h">
        <div>
          <h2 id="theme-h" className="card-title">Theme</h2>
          <p className="muted small" style={{ margin: '4px 0 0' }}>Changes apply immediately and are saved to your account, so they follow you to any device.</p>
        </div>
        <div className="theme-grid" role="radiogroup" aria-labelledby="theme-h">
          {THEMES.map((t) => (
            <button key={t.name} type="button" role="radio" aria-checked={theme === t.name} className="theme-card"
                    disabled={saving !== null} onClick={() => pick(t.name)}>
              <span className="theme-preview" aria-hidden="true" style={{ background: t.preview.page }}>
                <span className="p-rail" style={{ background: t.preview.rail }}>
                  <i style={{ background: t.preview.text, opacity: 0.25 }} /><i style={{ background: t.preview.accent }} /><i style={{ background: t.preview.text, opacity: 0.25, width: '70%' }} />
                </span>
                <span className="p-main">
                  <i style={{ background: t.preview.text, width: '55%' }} />
                  <i style={{ background: t.preview.accent, width: '35%', height: 16, borderRadius: 6 }} />
                  <i style={{ background: t.preview.text, opacity: 0.25 }} />
                  <i style={{ background: t.preview.text, opacity: 0.25, width: '80%' }} />
                </span>
              </span>
              <strong>{t.label}{theme === t.name ? ' (current)' : ''}</strong>
              <span className="small muted">{t.description}</span>
            </button>
          ))}
        </div>
        {status && <div className={`alert ${status.tone}`} role="status">{status.text}</div>}
      </section>

      {me && (
        <section className="card" aria-labelledby="acc-h">
          <h2 id="acc-h" className="card-title">Your account</h2>
          <dl style={{ display: 'grid', gridTemplateColumns: 'max-content 1fr', gap: '8px 20px', margin: 0 }}>
            <dt className="muted">Name</dt><dd style={{ margin: 0 }}>{me.fullName}</dd>
            <dt className="muted">Email</dt><dd style={{ margin: 0 }}>{me.email}</dd>
            <dt className="muted">Role</dt><dd style={{ margin: 0 }}>{me.roleName ?? ROLE_LABEL[me.role]}</dd>
          </dl>
          <span className="small muted">To change your password, sign out and use “Forgot your password?”; we email a single-use link valid for 30 minutes.</span>
        </section>
      )}
      <ActivityPanel categories={['auth', 'preferences']} mine title="My activity" />
    </div>
  );
}
