/**
 * Demo mode only: the beneficiary reports (/beneficiary-reports) over sample data, applying the same rules as
 * BeneficiaryReportQuery.java (filters, financial years, the either/or project columns, paging, export-all).
 */
import type { ReportColumn, ReportOptions, ReportRequest, ReportResult } from './types';

interface Need { id: number; refNo: string; name: string; orgId: number; approvedAt: string | null; periodicity: string; docApproved: boolean }
interface Gift { needId: number; donor: string; donorRef: string; donorType: string; purposeRef: string | null; purposeName: string | null;
  commitment: number | null; commitmentDate: string | null; periodicity: string; amount: number; paidOn: string }
interface Match { needId: number; priority: number; purposeRef: string; purposeName: string; donor: string; donorRef: string; donorType: string;
  commitment: number; commitmentDate: string; periodicity: string; upcoming: { amount: number; date: string } | null }

const ORGS: Record<number, ReportOptions['organisation']> = {
  21: { id: 21, name: 'ZP Primary School Khed', refNo: 'DB100021', type: 'SCHOOL', group: 'Direct beneficiary (school)' },
  31: { id: 31, name: 'Pragati Trust', refNo: 'IB100031', type: 'NGO', group: 'Indirect beneficiary (NGO)' },
  41: { id: 41, name: 'Shiksha Sahyog NGO', refNo: 'IB100041', type: 'NGO', group: 'Indirect beneficiary (NGO)' },
};
const NEEDS: Need[] = [
  { id: 877, refNo: 'CA100877', name: 'Smart classroom kit', orgId: 21, approvedAt: '2026-04-10', periodicity: 'One-time', docApproved: true },
  { id: 878, refNo: 'CA100878', name: 'Drinking water station', orgId: 21, approvedAt: '2025-11-03', periodicity: 'One-time', docApproved: true },
  { id: 879, refNo: 'CA100879', name: 'Playground repair', orgId: 21, approvedAt: null, periodicity: 'One-time', docApproved: false },
  { id: 219, refNo: 'PR100219', name: 'Digital literacy lab for adolescent girls', orgId: 31, approvedAt: '2026-03-15', periodicity: 'One-time', docApproved: false },
  { id: 220, refNo: 'PR100220', name: 'Millet snack programme', orgId: 31, approvedAt: '2025-09-20', periodicity: 'Quarterly', docApproved: true },
  { id: 221, refNo: 'PR100221', name: 'Sanitary pad machines', orgId: 31, approvedAt: null, periodicity: 'One-time', docApproved: false },
];
const ASHA = { donor: 'Asha Foundation CSR', donorRef: 'BN100011', donorType: 'Corporate donor' };
const NEHA = { donor: 'Neha Joshi', donorRef: 'BN100012', donorType: 'Individual donor' };
const MATCHES: Match[] = [
  { needId: 877, priority: 1, purposeRef: 'PU100302', purposeName: 'Digital classrooms for rural schools', ...ASHA, commitment: 500000, commitmentDate: '2026-04-20', periodicity: 'Yearly', upcoming: { amount: 100000, date: '2026-11-15' } },
  { needId: 219, priority: 2, purposeRef: 'PU100302', purposeName: 'Digital classrooms for rural schools', ...ASHA, commitment: 500000, commitmentDate: '2026-04-20', periodicity: 'Yearly', upcoming: null },
  { needId: 220, priority: 1, purposeRef: 'PU100305', purposeName: 'Nutrition for anganwadi children', ...ASHA, commitment: 400000, commitmentDate: '2025-10-02', periodicity: 'Quarterly', upcoming: { amount: 100000, date: '2026-12-01' } },
];
const GIFTS: Gift[] = [
  { needId: 877, ...NEHA, purposeRef: null, purposeName: null, commitment: null, commitmentDate: null, periodicity: 'One-time', amount: 150000, paidOn: '2026-10-18' },
  { needId: 878, ...ASHA, purposeRef: 'PU100290', purposeName: 'Clean water in schools', commitment: 260000, commitmentDate: '2025-11-20', periodicity: 'One-time', amount: 260000, paidOn: '2025-12-12' },
  { needId: 219, ...ASHA, purposeRef: 'PU100302', purposeName: 'Digital classrooms for rural schools', commitment: 500000, commitmentDate: '2026-04-20', periodicity: 'Yearly', amount: 120000, paidOn: '2026-09-30' },
  { needId: 220, ...ASHA, purposeRef: 'PU100305', purposeName: 'Nutrition for anganwadi children', commitment: 400000, commitmentDate: '2025-10-02', periodicity: 'Quarterly', amount: 100000, paidOn: '2025-10-15' },
  { needId: 220, ...ASHA, purposeRef: 'PU100305', purposeName: 'Nutrition for anganwadi children', commitment: 400000, commitmentDate: '2025-10-02', periodicity: 'Quarterly', amount: 140000, paidOn: '2026-01-15' },
];
const CUSTOM: [string, string, string][] = [   // key, NGO label, school label
  ['NEED', 'Project', 'Cause'], ['PENDING_NEED', 'Project (yet to be approved)', 'Cause (yet to be approved)'], ['APPROVED_DATE', 'Approved Date', 'Approved Date'],
  ['MATCHED_FLAG', 'Matched Flag', 'Matched Flag'], ['PURPOSE_ID', 'Purpose Id', 'Purpose Id'], ['PURPOSE_NAME', 'Purpose Name', 'Purpose Name'],
  ['COMMITMENT_AMOUNT', 'Commitment Amount', 'Commitment Amount'], ['COMMITMENT_DATE', 'Commitment Date', 'Commitment Date'],
  ['DONATION_AMOUNT', 'Donation Amount', 'Donation Amount'], ['DONATION_DATE', 'Donation Date', 'Donation Date'], ['DONOR_NAME', 'Donor Name', 'Donor Name'],
  ['DONOR_TYPE', 'Donor Type', 'Donor Type'], ['UPCOMING_AMOUNT', 'Upcoming Donation Amount', 'Upcoming Donation Amount'],
  ['UPCOMING_DATE', 'Upcoming Donation Date', 'Upcoming Donation Date'], ['DOC_STATUS', 'Project Doc status', 'Cause Doc status'], ['PERIODICITY', 'Periodicity', 'Periodicity'],
];

class ReportProblem extends Error { constructor(public status: number, message: string) { super(message); } }

export function demoReportOptions(orgId: number): ReportOptions {
  const org = ORGS[orgId];
  if (!org) throw new ReportProblem(422, 'Reports are run for a school or an NGO.');
  const school = org.type === 'SCHOOL';
  return { organisation: org, needs: NEEDS.filter((n) => n.orgId === orgId).map((n) => ({ id: n.id, refNo: n.refNo, name: n.name, approved: !!n.approvedAt })),
    years: ['2026-2027', '2025-2026', '2024-2025'], customColumns: CUSTOM.map(([key, ngo, sch]) => ({ key, label: school ? sch : ngo })) };
}

export function demoReport(type: string, orgId: number, r: ReportRequest): ReportResult {
  const org = ORGS[orgId]!;
  const school = org.type === 'SCHOOL';
  const word = school ? 'Cause' : 'Project';
  const cols = type === 'custom' ? CUSTOM.map((c) => c[0]).filter((k) => r.columns?.includes(k)) : [];
  if (type === 'custom' && cols.includes('NEED') === cols.includes('PENDING_NEED'))
    throw new ReportProblem(422, `Choose either “${word}” or “${word} (yet to be approved)”.`);
  const pending = cols.includes('PENDING_NEED');
  let range: [string, string] | null = null;
  if (r.year && (r.approvedFrom || r.approvedTo)) throw new ReportProblem(422, 'Choose a financial year or approval dates, not both.');
  if (r.year) { const y = Number(r.year.slice(0, 4)); range = [`${y}-04-01`, `${y + 1}-03-31`]; }
  else if (r.approvedFrom && r.approvedTo) range = [r.approvedFrom, r.approvedTo];
  if (pending && range) throw new ReportProblem(422, 'Approval dates do not apply to causes or projects that are not approved yet.');

  const needs = NEEDS.filter((n) => n.orgId === orgId && (!r.needId || n.id === r.needId)
    && (pending ? !n.approvedAt : !!n.approvedAt && (!range || (n.approvedAt >= range[0] && n.approvedAt <= range[1]))));
  const ids = new Set(needs.map((n) => n.id));
  const need = (id: number) => NEEDS.find((n) => n.id === id)!;
  const C = (key: string, label: string, t: ReportColumn['type'] = 'text'): ReportColumn => ({ key, label, type: t });
  let columns: ReportColumn[] = [];
  let rows: ReportResult['rows'] = [];

  if (type === 'donations') {
    columns = [C('need', `${word} Name`), C('needRef', `${word} Id`), C('donorName', 'Donor Name'), C('donorType', 'Donor Type'), C('commitmentAmount', 'Commitment Amount', 'money'),
      C('commitmentDate', 'Commitment Date', 'date'), C('periodicity', 'Periodicity'), C('donationAmount', 'Donation Amount', 'money'), C('donationDate', 'Donation Date', 'date')];
    rows = GIFTS.filter((g) => ids.has(g.needId)).sort((a, b) => b.paidOn.localeCompare(a.paidOn)).map((g) => ({ need: need(g.needId).name, needRef: need(g.needId).refNo,
      donorName: g.donor, donorType: g.donorType, commitmentAmount: g.commitment, commitmentDate: g.commitmentDate, periodicity: g.periodicity, donationAmount: g.amount, donationDate: g.paidOn }));
  } else if (type === 'matches') {
    columns = [C('need', `${word} Name`), C('needRef', `${word} Id`), C('purposeRef', 'Purpose ID'), C('purposeName', 'Purpose Name'), C('commitmentAmount', 'Commitment Amount', 'money'),
      C('commitmentDate', 'Commitment Date', 'date'), C('periodicity', 'Periodicity')];
    rows = MATCHES.filter((m) => ids.has(m.needId)).map((m) => ({ need: need(m.needId).name, needRef: need(m.needId).refNo, purposeRef: m.purposeRef, purposeName: m.purposeName,
      commitmentAmount: m.commitment, commitmentDate: m.commitmentDate, periodicity: m.periodicity }));
  } else if (type === 'needs') {
    columns = [C('need', `${word} Name`), C('needRef', `${word} Id`), C('approvedDate', 'Approved Date', 'date'), C('matched', 'Matched (Y/N)'), C('donated', 'Donation Received (Y/N)'),
      C('commitmentTotal', 'Sum of Commitment Amount (matched)', 'money'), C('donationTotal', 'Sum of Donation Amount', 'money')];
    rows = needs.map((n) => ({ need: n.name, needRef: n.refNo, approvedDate: n.approvedAt, matched: MATCHES.some((m) => m.needId === n.id) ? 'Y' : 'N',
      donated: GIFTS.some((g) => g.needId === n.id) ? 'Y' : 'N', commitmentTotal: MATCHES.filter((m) => m.needId === n.id).reduce((a, m) => a + m.commitment, 0),
      donationTotal: GIFTS.filter((g) => g.needId === n.id).reduce((a, g) => a + g.amount, 0) }));
  } else {
    const label = (k: string) => CUSTOM.find((c) => c[0] === k)![school ? 2 : 1];
    const types: Record<string, ReportColumn['type']> = { APPROVED_DATE: 'date', COMMITMENT_AMOUNT: 'money', COMMITMENT_DATE: 'date', DONATION_AMOUNT: 'money',
      DONATION_DATE: 'date', UPCOMING_AMOUNT: 'money', UPCOMING_DATE: 'date' };
    const keyOf: Record<string, string> = { APPROVED_DATE: 'approvedDate', MATCHED_FLAG: 'matchedFlag', PURPOSE_ID: 'purposeRef', PURPOSE_NAME: 'purposeName',
      COMMITMENT_AMOUNT: 'commitmentAmount', COMMITMENT_DATE: 'commitmentDate', DONATION_AMOUNT: 'donationAmount', DONATION_DATE: 'donationDate', DONOR_NAME: 'donorName',
      DONOR_TYPE: 'donorType', UPCOMING_AMOUNT: 'upcomingAmount', UPCOMING_DATE: 'upcomingDate', DOC_STATUS: 'docStatus', PERIODICITY: 'periodicity' };
    columns = [C('need', label('NEED')), C('needRef', `${word} Id`), ...cols.filter((k) => keyOf[k]).map((k) => C(keyOf[k]!, label(k), types[k] ?? 'text'))];
    const joinMatch = cols.some((k) => ['MATCHED_FLAG', 'PURPOSE_ID', 'PURPOSE_NAME', 'COMMITMENT_AMOUNT', 'COMMITMENT_DATE', 'DONATION_AMOUNT', 'DONATION_DATE', 'DONOR_NAME', 'DONOR_TYPE', 'UPCOMING_AMOUNT', 'UPCOMING_DATE'].includes(k));
    const joinGift = cols.some((k) => ['DONATION_AMOUNT', 'DONATION_DATE', 'DONOR_NAME', 'DONOR_TYPE'].includes(k));
    for (const n of needs) {
      const ms: (Match | null)[] = joinMatch ? (MATCHES.filter((m) => m.needId === n.id).length ? MATCHES.filter((m) => m.needId === n.id) : [null]) : [null];
      for (const m of ms) {
        const gs: (Gift | null)[] = joinGift && m ? (GIFTS.filter((g) => g.needId === n.id && g.purposeRef === m.purposeRef).length
          ? GIFTS.filter((g) => g.needId === n.id && g.purposeRef === m.purposeRef) : [null]) : [null];
        for (const g of gs) {
          rows.push({ need: n.name, needRef: n.refNo, approvedDate: n.approvedAt, matchedFlag: m ? 'Yes' : 'No', purposeRef: m?.purposeRef ?? null, purposeName: m?.purposeName ?? null,
            commitmentAmount: m?.commitment ?? null, commitmentDate: m?.commitmentDate ?? null, donationAmount: g?.amount ?? null, donationDate: g?.paidOn ?? null,
            donorName: g ? `${g.donor} [ID: ${g.donorRef}]` : m ? `${m.donor} [ID: ${m.donorRef}]` : null, donorType: g?.donorType ?? m?.donorType ?? null,
            upcomingAmount: m?.upcoming?.amount ?? null, upcomingDate: m?.upcoming?.date ?? null, docStatus: n.docApproved ? 'Approved' : 'Not approved', periodicity: n.periodicity });
        }
      }
    }
  }
  const size = r.all ? 10000 : Math.min(Math.max(r.size ?? 10, 1), 100);
  const page = r.all ? 0 : r.page ?? 0;
  const parts: string[] = [];
  if (r.year) parts.push(`Financial year - ${r.year}`);
  else if (range) parts.push(`Approved between - ${range[0]} to ${range[1]}`);
  if (r.needId) parts.push(`${word} - ${need(r.needId).name}`);
  if (pending) parts.push('Not yet approved');
  const title = r.header?.trim() || { donations: 'Donation Report', matches: 'Matches Report', needs: `${word} Report`, custom: `Customize ${word} Report` }[type]!;
  return { title, entityLine: `Entity Name - ${org.name} [ID: ${org.refNo}]`, filterLine: parts.length ? parts.join('; ') : `All ${school ? 'causes' : 'projects'}`,
    columns, rows: rows.slice(page * size, page * size + size), total: rows.length, page, size };
}

export { ReportProblem };
