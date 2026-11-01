import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuth } from '../../app/auth';
import { api } from '../../shared/api/client';
import type { BeneficiaryReportType, OrganisationOption } from '../../shared/api/types';
import { SearchSelect } from '../../shared/ui/SearchSelect';
import { BeneficiaryReports } from './BeneficiaryReports';
import { DataExports } from './DataExports';
import { ActivityPanel } from '../../shared/ui/AuditTrail';

type Tab = BeneficiaryReportType | 'exports';

/**
 * All reports of the original project, for schools and NGOs (their own data) and for staff (any school or NGO),
 * plus whole-table CSV exports for staff.
 */
export default function ReportsPage() {
  const { me, can } = useAuth();
  const staff = can('dashboard.platform');
  const school = me?.role === 'SCHOOL';
  const [tab, setTab] = useState<Tab>('donations');
  const [orgId, setOrgId] = useState<number | null>(null);
  const beneficiaries = useQuery({ queryKey: ['lookups', 'BENEFICIARIES'], enabled: staff,
    queryFn: () => api<OrganisationOption[]>('/lookups/organisations?group=BENEFICIARIES') });
  const chosen = beneficiaries.data?.find((o) => o.id === orgId);
  const needWord = staff ? (chosen?.type === 'SCHOOL' ? 'Cause' : chosen ? 'Project' : 'Project or cause') : school ? 'Cause' : 'Project';

  if (me && !staff && me.role !== 'SCHOOL' && me.role !== 'NGO') {
    return <div className="page"><h1 className="title">Reports</h1><p className="muted">These reports are for schools and NGOs. Your giving is on My giving and the dashboard.</p></div>;
  }

  const tabs: { key: Tab; label: string }[] = [
    { key: 'donations', label: 'Donation report' }, { key: 'matches', label: 'Match report' },
    { key: 'needs', label: `${needWord} report` }, { key: 'custom', label: `Customize ${needWord.toLowerCase()} report` },
    ...(staff ? [{ key: 'exports' as Tab, label: 'Data exports (CSV)' }] : []),
  ];

  return (
    <div className="page">
      <div><span className="eyebrow">Filter, view, and export to Excel or PDF</span><h1 className="title">Reports</h1></div>
      <div role="tablist" aria-label="Report" className="segmented">
        {tabs.map((t) => <button key={t.key} type="button" role="tab" className="chip-btn" aria-selected={tab === t.key} onClick={() => setTab(t.key)}>{t.label}</button>)}
      </div>
      {tab === 'exports' ? <DataExports /> : (
        <>
          {staff && (
            <div className="card">
              <SearchSelect label="School or NGO" loading={beneficiaries.isLoading} value={orgId} onChange={setOrgId}
                            placeholder="Search by name or reference"
                            options={(beneficiaries.data ?? []).map((o) => ({ value: o.id, label: o.name, detail: `${o.group}, ${o.refNo}` }))} />
            </div>
          )}
          {staff && !orgId ? <p className="muted">Choose the school or NGO to report on.</p> : <BeneficiaryReports type={tab} orgId={staff ? orgId : null} />}
        </>
      )}
      <ActivityPanel categories={['report']} />
    </div>
  );
}
