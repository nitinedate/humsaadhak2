import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, DEMO } from '../shared/api/client';
import type { Counters } from '../shared/api/types';
import { initials, ROLE_LABEL } from '../shared/format';
import { useAuth } from './auth';
import logo from '../assets/logo.png';
import logoTagline from '../assets/logo-tagline.png';   // the complete logo, as on the sign-in page

const Icon = ({ d }: { d: string }) => (
  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
);
const I = {
  dashboard: 'M3 3v18h18M8 15v-4M13 15V7M18 15v-6',
  approvals: 'M9 11l3 3 8-8M20 12v7a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h9',
  matching: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4a5 5 0 1 0 0 10 5 5 0 0 0 0-10z',
  purposes: 'M12 3l2.5 5 5.5.8-4 3.9.9 5.5L12 15.6 7.1 18.2l.9-5.5-4-3.9 5.5-.8z',
  needs: 'M4 4h16v16H4zM4 10h16M10 4v16',
  giving: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z',
  donate: 'M12 5v14M5 12h14',
  documents: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5',
  share: 'M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13',
  org: 'M4 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M16 9h2a2 2 0 0 1 2 2v10M8 7h4M8 11h4M8 15h4M3 21h18',
  import: 'M12 3v12M7 10l5 5 5-5M5 21h14',
  reports: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M9 13h6M9 17h6M9 9h1',
  reference: 'M4 6h16M4 12h16M4 18h10',
  roles: 'M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6zM9 12l2 2 4-4',
  audit: 'M9 4h6v3H9zM7 5H5v16h14V5h-2M8 12h8M8 16h5',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  logout: 'M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H3',
};

interface Link { to: string; label: string; icon: string; show: boolean; count?: number }

/** The fixed left panel (it does not scroll with the page) plus the page area. */
export function Shell() {
  const { me, can, signOut } = useAuth();
  const navigate = useNavigate();
  const { pathname, state } = useLocation();
  // Just signed in: the first page flips into view, continuing the sign-in card's flip (once)
  const [arrived] = useState(() => !!(state as { flipIn?: boolean } | null)?.flipIn);
  // Phones and tablets: the side panel is an off-canvas menu. It closes on navigation, on Escape and on a tap outside.
  const [menuOpen, setMenuOpen] = useState(false);
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    setMenuOpen(false);
    document.documentElement.scrollTop = 0;   // each page opens at its top (phones keep the previous page's scroll otherwise)
    document.body.scrollTop = 0;
  }, [pathname]);
  useEffect(() => {
    if (!menuOpen) return;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [menuOpen]);
  // What is waiting for this person, refreshed every minute and after each decision (pages invalidate ['counters']).
  const counters = useQuery({ queryKey: ['counters', me?.id], enabled: !!me, refetchInterval: 60_000,
    queryFn: () => api<Counters>('/dashboards/me/counters') }).data;
  if (!me) return null;
  const isDonor = me.role === 'DONOR_CORPORATE' || me.role === 'DONOR_INDIVIDUAL';
  const isPartner = me.role === 'SCHOOL' || me.role === 'NGO';

  const work: Link[] = [
    { to: '/', label: 'Dashboard', icon: I.dashboard, show: true },
    { to: '/approvals', label: 'Approvals', icon: I.approvals, show: can('record.approve'), count: counters?.approvalsWaiting },
    { to: '/matching', label: 'Match Studio', icon: I.matching, show: can('match.decide') },
    { to: '/giving', label: 'My giving', icon: I.giving, show: isDonor },
    { to: '/purposes', label: isDonor ? 'My purposes' : 'Purposes', icon: I.purposes, show: can('purpose.edit') },
    { to: '/needs', label: me.role === 'SCHOOL' ? 'Our causes' : me.role === 'NGO' ? 'Our projects' : 'Causes and projects', icon: I.needs, show: can('need.edit') },
    { to: '/donate', label: 'Record donation', icon: I.donate, show: can('donation.record') },
    { to: '/documents', label: 'Documents', icon: I.documents, show: can('document.upload') || can('document.review'), count: counters?.documentsToReview },
    { to: '/share-requests', label: 'Document requests', icon: I.share, show: can('share.request') || can('share.decide'), count: counters?.shareRequestsWaiting },
    { to: '/reports', label: 'Reports', icon: I.reports, show: can('dashboard.platform') || isPartner },
    { to: '/organisation', label: 'Organisation profile', icon: I.org, show: isDonor || isPartner },
  ];
  const admin: Link[] = [
    { to: '/imports', label: 'Bulk import', icon: I.import, show: can('import.run') },
    { to: '/admin/reference', label: 'Reference data', icon: I.reference, show: can('reference.manage') },
    { to: '/admin/users', label: 'Users', icon: I.users, show: can('admin.users') },
    { to: '/admin/roles', label: 'Roles and permissions', icon: I.roles, show: can('admin.roles') },
    { to: '/admin/audit', label: 'Audit log', icon: I.audit, show: can('audit.view') },
  ];
  const item = (l: Link) => (
    <NavLink key={l.to} to={l.to} end={l.to === '/'} className={({ isActive }) => `rail-link${isActive ? ' active' : ''}`}
             onClick={() => setMenuOpen(false)}>
      <Icon d={l.icon} />{l.label}
      {!!l.count && <span className="count" aria-label={`${l.count} waiting`}>{l.count}</span>}
    </NavLink>
  );

  return (
    <div className="app">
      <a href="#content" className="skip-link">Skip to content</a>
      <header className="topbar navbar d-lg-none">
        <button type="button" className="navbar-toggler" aria-controls="side-panel" aria-expanded={menuOpen} aria-label="Open menu"
                onClick={() => setMenuOpen(true)}>
          <span className="navbar-toggler-icon" />
        </button>
        <img className="topbar-logo" src={logo} alt="" width="128" height="30" />
        <div className="avatar" aria-hidden="true">{initials(me.fullName)}</div>
      </header>
      <aside id="side-panel" className={`rail offcanvas-lg offcanvas-start${menuOpen ? ' show' : ''}`} aria-label="Side panel" tabIndex={-1}>
        <div className="offcanvas-header d-lg-none">
          <strong className="rail-section m-0">Menu</strong>
          <button type="button" className="btn-close" aria-label="Close menu" ref={closeRef} onClick={() => setMenuOpen(false)} />
        </div>
        <div className="offcanvas-body">
          <div className="rail-logo"><img src={logoTagline} alt="HumSaadhak, where social sector needs find a suitable match" width="196" height="73" /></div>
          <div className="rail-org">
            <strong>{me.roleName ?? ROLE_LABEL[me.role]} workspace</strong>
            {DEMO && (
              <span className="rail-demo">
                Demo data
                <button type="button" className="rail-switch" onClick={async () => { await signOut(); navigate('/signin'); }}>
                  Switch demo role
                </button>
              </span>
            )}
          </div>
          <nav aria-label="Main">
            {work.filter((l) => l.show).map(item)}
            {admin.some((l) => l.show) && <span className="rail-section">Administration</span>}
            {admin.filter((l) => l.show).map(item)}
            <span className="rail-section">You</span>
            {item({ to: '/settings', label: 'Settings', icon: I.settings, show: true })}
          </nav>
          <div className="rail-user">
            <div className="avatar" aria-hidden="true">{initials(me.fullName)}</div>
            <div className="who"><strong>{me.fullName}</strong><span>{me.roleName ?? ROLE_LABEL[me.role]}</span></div>
            <button type="button" className="icon-btn" aria-label="Sign out" title="Sign out"
                    onClick={async () => { await signOut(); navigate('/signin'); }}>
              <Icon d={I.logout} />
            </button>
          </div>
        </div>
      </aside>
      {menuOpen && <div className="offcanvas-backdrop fade show d-lg-none" aria-hidden="true" onClick={() => setMenuOpen(false)} />}
      <main className={`main${arrived ? ' page-flip-in' : ''}`} id="content" tabIndex={-1}><Outlet /></main>
    </div>
  );
}
