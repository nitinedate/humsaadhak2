import { lazy, Suspense, type ReactNode } from 'react';
import { BrowserRouter, MemoryRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Skeleton } from '../shared/ui/States';
import { RequireAuth } from './auth';
import { Shell } from './Shell';
import { AuthLayout } from '../features/auth/AuthLayout';
import { LoginPage } from '../features/auth/LoginPage';

// One bundle per screen: people only download what their role uses.
const DashboardPage = lazy(() => import('../features/dashboards/DashboardPage'));
const ApprovalsPage = lazy(() => import('../features/approvals/ApprovalsPage'));
const MatchStudioPage = lazy(() => import('../features/matching/MatchStudioPage'));
const DonatePage = lazy(() => import('../features/giving/DonatePage'));
const MyGivingPage = lazy(() => import('../features/giving/MyGivingPage'));
const PurposesPage = lazy(() => import('../features/catalogue/PurposesPage'));
const NeedsPage = lazy(() => import('../features/catalogue/NeedsPage'));
const DocumentsPage = lazy(() => import('../features/documents/DocumentsPage'));
const ShareRequestsPage = lazy(() => import('../features/documents/ShareRequestsPage'));
const OrganisationPage = lazy(() => import('../features/organisation/OrganisationPage'));
const ImportPage = lazy(() => import('../features/admin/ImportPage'));
const UsersPage = lazy(() => import('../features/admin/UsersPage'));
const RolesPage = lazy(() => import('../features/admin/RolesPage'));
const ReportsPage = lazy(() => import('../features/reports/ReportsPage'));
const ReferenceDataPage = lazy(() => import('../features/admin/ReferenceDataPage'));
const AuditLogPage = lazy(() => import('../features/admin/AuditLogPage'));
const SettingsPage = lazy(() => import('../features/settings/SettingsPage'));
const SignUpPage = lazy(() => import('../features/auth/SignUpPage'));
const SetPasswordPage = lazy(() => import('../features/auth/SetPasswordPage'));
const ForgotPasswordPage = lazy(() => import('../features/auth/ForgotPasswordPage'));

// The single-file preview build runs inside a viewer frame, where the address bar is not ours to change.
const Router = import.meta.env.VITE_ROUTER === 'memory' ? MemoryRouter : BrowserRouter;

const guarded = (permission: string, page: ReactNode) => <RequireAuth permission={permission}>{page}</RequireAuth>;

export function App() {
  return (
    <Router>
      <Suspense fallback={<div className="main"><Skeleton height={400} /></div>}>
        <Routes>
          {/* One layout for the sign-in pages: the brand panel stays, the page card flips between them */}
          <Route element={<AuthLayout />}>
            <Route path="/signin" element={<LoginPage />} />
            <Route path="/signup" element={<SignUpPage />} />
            <Route path="/set-password" element={<SetPasswordPage />} />
            <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          </Route>
          <Route element={<RequireAuth><Shell /></RequireAuth>}>
            <Route index element={<DashboardPage />} />
            <Route path="approvals" element={guarded('record.approve', <ApprovalsPage />)} />
            <Route path="matching" element={guarded('match.decide', <MatchStudioPage />)} />
            <Route path="donate" element={guarded('donation.record', <DonatePage />)} />
            <Route path="giving" element={guarded('share.request', <MyGivingPage />)} />
            <Route path="purposes" element={guarded('purpose.edit', <PurposesPage />)} />
            <Route path="needs" element={guarded('need.edit', <NeedsPage />)} />
            <Route path="documents" element={<RequireAuth><DocumentsPage /></RequireAuth>} />
            <Route path="share-requests" element={<RequireAuth><ShareRequestsPage /></RequireAuth>} />
            <Route path="organisation" element={<RequireAuth><OrganisationPage /></RequireAuth>} />
            <Route path="imports" element={guarded('import.run', <ImportPage />)} />
            <Route path="admin/users" element={guarded('admin.users', <UsersPage />)} />
            <Route path="admin/roles" element={guarded('admin.roles', <RolesPage />)} />
            <Route path="admin/reference" element={guarded('reference.manage', <ReferenceDataPage />)} />
            <Route path="admin/audit" element={guarded('audit.view', <AuditLogPage />)} />
            <Route path="reports" element={<RequireAuth><ReportsPage /></RequireAuth>} />
            <Route path="settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </Router>
  );
}
