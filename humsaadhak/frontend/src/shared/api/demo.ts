/**
 * Demo mode (VITE_DEMO=true): an in-browser stand-in for the API so the UI can be shown without a server.
 * Every name and number here is sample data.
 */
import * as audit from './demoAudit';
import { ApiError } from './client';
import { demoDetails, withDemoDrills } from './demoDetails';
import * as access from './demoAccess';
import { demoReport as demoBeneficiaryReport, demoReportOptions, ReportProblem } from './demoReports';
import type {
  Counters, Drill, OpenNeed, OrganisationOption, ReportRequest,
  DocumentTypeAdminRow, SectorAdminRow,
  AdminUserRow, ApprovalRow, BankRow, CandidateDecision, CandidateRow, ContactRow, DashboardDto, DocRow, DocumentType, DonationResult,
  DocumentListRow, ImportJob, MatchRow, Me, NeedView, Option, OrgSummary, PageResponse, PurposeView, ReviewRow, Role, ShareRow, StepRow,
} from './types';

const users: Record<string, Me> = {
  manager: { id: 1, email: 'manager@demo.humsaadhak.org', fullName: 'Priya Kulkarni', orgId: null, role: 'MANAGER',
    permissions: ['user.approve', 'org.approve', 'record.approve', 'document.review', 'match.run', 'match.decide', 'dashboard.platform'] },
  associate: { id: 2, email: 'associate@demo.humsaadhak.org', fullName: 'Rahul Mehta', orgId: null, role: 'ASSOCIATE',
    permissions: ['purpose.edit', 'need.edit', 'document.upload', 'match.decide', 'donation.record', 'import.run', 'dashboard.platform'] },
  donor: { id: 3, email: 'donor@demo.humsaadhak.org', fullName: 'Meera Iyer', orgId: 11, role: 'DONOR_CORPORATE',
    permissions: ['purpose.edit', 'document.upload', 'share.request', 'donation.record'] },
  individual: { id: 8, email: 'individual@demo.humsaadhak.org', fullName: 'Neha Joshi', orgId: 12, role: 'DONOR_INDIVIDUAL',
    permissions: ['purpose.edit', 'document.upload', 'share.request', 'donation.record'] },
  school: { id: 4, email: 'school@demo.humsaadhak.org', fullName: 'Sunita Pawar', orgId: 21, role: 'SCHOOL',
    permissions: ['need.edit', 'document.upload', 'share.decide'] },
  ngo: { id: 5, email: 'ngo@demo.humsaadhak.org', fullName: 'Anita Deshpande', orgId: 31, role: 'NGO',
    permissions: ['need.edit', 'document.upload', 'share.decide'] },
  admin: { id: 6, email: 'admin@demo.humsaadhak.org', fullName: 'Kavita Rao', orgId: null, role: 'SUPER_ADMIN',
    permissions: ['admin.users', 'import.run', 'dashboard.platform', 'reference.manage'] },
};

// The four groups, named as on the server (BeneficiaryTerms.java).
const IND = 'Individual donors', CORP = 'Corporate donors', DIRECT = 'Direct beneficiaries (schools)', INDIRECT = 'Indirect beneficiaries (NGOs)';
const quarters = (rows: [number, number][]) => rows.map((v, i) => ({ label: `Q${i + 1}`, values: v }));

/* Sample figures are consistent across charts: e.g. the monthly split (₹10.7 L individual + ₹30.9 L corporate)
   equals "Who gave to whom" and the ₹41.6 L total. */
const dashboards: Partial<Record<Role, DashboardDto>> = {
  MANAGER: {
    role: 'MANAGER', title: 'Manager dashboard',
    kpis: [
      { label: 'Donations', value: '61', note: '₹41.6 L received this financial year' },
      { label: 'Approved causes and projects', value: '214', note: '102 for direct, 112 for indirect beneficiaries' },
      { label: 'Verified organisations', value: '174', note: 'of 198 registered' },
      { label: 'Match approval rate', value: '78%', note: 'of candidates reviewed' },
    ],
    donuts: [
      { title: 'Organisations by type', totalLabel: 'organisations', slices: [
        { label: IND, value: 42, colorKey: 'rust' }, { label: CORP, value: 24, colorKey: 'indigo' },
        { label: DIRECT, value: 73, colorKey: 'amber' }, { label: INDIRECT, value: 59, colorKey: 'teal' }] },
      { title: 'Beneficiaries by verification', totalLabel: 'beneficiaries', slices: [
        { label: `${DIRECT}, verified`, value: 64, colorKey: 'rust' }, { label: `${DIRECT}, waiting`, value: 9, colorKey: 'rust-light' },
        { label: `${INDIRECT}, verified`, value: 52, colorKey: 'indigo' }, { label: `${INDIRECT}, waiting`, value: 7, colorKey: 'indigo-light' }] },
      { title: 'Donors by verification', totalLabel: 'donors', slices: [
        { label: `${IND}, verified`, value: 37, colorKey: 'rust' }, { label: `${CORP}, verified`, value: 21, colorKey: 'indigo' },
        { label: 'Waiting for approval', value: 8, colorKey: 'grey' }] },
    ],
    bars: [
      { title: 'Money received by month', series: ['From individual donors', 'From corporate donors'], unit: '₹ lakh', bars: [
        { label: 'Apr', values: [1.2, 2.6] }, { label: 'May', values: [1.4, 3.8] }, { label: 'Jun', values: [1.1, 3.5] }, { label: 'Jul', values: [1.6, 4.5] },
        { label: 'Aug', values: [1.3, 4.6] }, { label: 'Sep', values: [2.0, 5.6] }, { label: 'Oct', values: [2.1, 6.3] }] },
      { title: 'Who gave to whom', series: [`To direct beneficiaries (schools)`, `To indirect beneficiaries (NGOs)`], unit: '₹', bars: [
        { label: IND, values: [640000, 430000] }, { label: CORP, values: [1720000, 1370000] }] },
      { title: 'Causes and projects by funding state', series: [DIRECT, INDIRECT], unit: 'count', bars: [
        { label: 'Matched and donated', values: [24, 21] }, { label: 'Matched, no donation yet', values: [31, 27] },
        { label: 'Donated without a match', values: [6, 10] }, { label: 'Neither matched nor donated', values: [41, 54] }] },
    ],
  },
  ASSOCIATE: {
    role: 'ASSOCIATE', title: 'Your work',
    kpis: [
      { label: 'Returned to you', value: '2', note: 'fix and resubmit first' },
      { label: 'Drafts', value: '6', note: 'not yet submitted' },
      { label: 'Waiting for a manager', value: '9', note: 'oldest 2 days' },
      { label: 'Approved this month', value: '23', note: '92% approved first time' },
    ],
    donuts: [
      { title: 'Causes and projects on the platform', totalLabel: 'listed', slices: [
        { label: `Causes of direct beneficiaries (schools)`, value: 102, colorKey: 'rust' },
        { label: `Projects of indirect beneficiaries (NGOs)`, value: 112, colorKey: 'indigo' },
        { label: 'Waiting for approval', value: 19, colorKey: 'grey' }] },
      { title: 'Organisations on the platform', totalLabel: 'organisations', slices: [
        { label: IND, value: 42, colorKey: 'rust' }, { label: CORP, value: 24, colorKey: 'indigo' },
        { label: DIRECT, value: 73, colorKey: 'amber' }, { label: INDIRECT, value: 59, colorKey: 'teal' }] },
    ],
    bars: [{ title: 'Entries this week', series: ['Created', 'Submitted'], unit: 'count', bars: [
      { label: 'Mon', values: [7, 5] }, { label: 'Tue', values: [9, 8] }, { label: 'Wed', values: [4, 4] },
      { label: 'Thu', values: [11, 6] }, { label: 'Fri', values: [8, 7] }, { label: 'Sat', values: [3, 1] }] }],
  },
  DONOR_CORPORATE: {
    role: 'DONOR', title: 'Your giving at a glance',
    kpis: [
      { label: 'Given', value: '₹7,50,000', note: '62% of ₹12,00,000 pledged' },
      { label: 'Beneficiaries supported', value: '6', note: '4 direct and 2 indirect beneficiaries' },
      { label: 'Donations made', value: '9', note: 'this financial year' },
      { label: 'Commitments', value: '5', note: 'approved purposes' },
    ],
    donuts: [
      { title: 'Where your money went', totalLabel: 'given this year', slices: [
        { label: DIRECT, value: 310000, colorKey: 'rust' }, { label: INDIRECT, value: 440000, colorKey: 'indigo' }] },
      { title: 'Your purposes', totalLabel: 'purposes', slices: [
        { label: 'Matched and donated', value: 2, colorKey: 'green' }, { label: 'Matched, not donated yet', value: 2, colorKey: 'amber' },
        { label: 'Not matched yet', value: 1, colorKey: 'grey' }] },
    ],
    bars: [
      { title: 'Given to direct and indirect beneficiaries, by quarter', series: [DIRECT, INDIRECT], unit: '₹',
        bars: quarters([[100000, 150000], [110000, 190000], [100000, 100000], [0, 0]]) },
      { title: 'Giving by sector', series: ['Given'], unit: '₹', bars: [
        { label: 'Education', values: [340000] }, { label: 'Nutrition', values: [300000] }, { label: 'Health and sanitation', values: [110000] }] },
    ],
  },
  DONOR_INDIVIDUAL: {
    role: 'DONOR', title: 'Your giving at a glance',
    kpis: [
      { label: 'Given', value: '₹1,10,000', note: '92% of ₹1,20,000 pledged' },
      { label: 'Beneficiaries supported', value: '2', note: '1 direct and 1 indirect beneficiaries' },
      { label: 'Donations made', value: '4', note: 'this financial year' },
      { label: 'Commitments', value: '1', note: 'approved purposes' },
    ],
    donuts: [
      { title: 'Where your money went', totalLabel: 'given this year', slices: [
        { label: DIRECT, value: 60000, colorKey: 'rust' }, { label: INDIRECT, value: 50000, colorKey: 'indigo' }] },
      { title: 'Your purposes', totalLabel: 'purposes', slices: [
        { label: 'Matched and donated', value: 1, colorKey: 'green' }, { label: 'Matched, not donated yet', value: 0, colorKey: 'amber' },
        { label: 'Not matched yet', value: 0, colorKey: 'grey' }] },
    ],
    bars: [{ title: 'Given to direct and indirect beneficiaries, by quarter', series: [DIRECT, INDIRECT], unit: '₹',
             bars: quarters([[30000, 20000], [30000, 30000], [0, 0], [0, 0]]) }],
  },
  SCHOOL: {
    role: 'SCHOOL', title: 'Direct beneficiary dashboard',
    kpis: [
      { label: 'Received', value: '₹6,10,000', note: '46% of ₹13,40,000' },
      { label: 'Donors', value: '5', note: '2 corporate, 3 individual' },
      { label: 'Document requests', value: '0', note: 'waiting for your answer' },
    ],
    donuts: [
      { title: 'Causes by funding state', totalLabel: 'causes', slices: [
        { label: 'Received a donation', value: 2, colorKey: 'green' }, { label: 'Matched, not funded yet', value: 1, colorKey: 'amber' },
        { label: 'No match yet', value: 1, colorKey: 'grey' }] },
      { title: 'Money by donor type', totalLabel: 'received', slices: [
        { label: IND, value: 110000, colorKey: 'rust' }, { label: CORP, value: 500000, colorKey: 'indigo' }] },
    ],
    bars: [
      { title: 'Raised against target', series: ['Raised', 'Target'], unit: '₹', bars: [
        { label: 'Drinking water station', values: [260000, 260000] }, { label: 'Toilet block for girls', values: [350000, 400000] },
        { label: 'Smart classroom kit', values: [0, 450000] }, { label: 'Playground repair', values: [0, 230000] }] },
      { title: 'Received from individual and corporate donors, by quarter', series: [IND, CORP], unit: '₹',
        bars: quarters([[40000, 200000], [50000, 150000], [20000, 150000], [0, 0]]) },
    ],
  },
  NGO: {
    role: 'NGO', title: 'Indirect beneficiary dashboard',
    kpis: [
      { label: 'Received', value: '₹4,00,000', note: '27% of ₹15,00,000' },
      { label: 'Donors', value: '4', note: '2 corporate, 2 individual' },
      { label: 'Document requests', value: '1', note: 'waiting for your answer' },
    ],
    donuts: [
      { title: 'Projects by funding state', totalLabel: 'projects', slices: [
        { label: 'Received a donation', value: 3, colorKey: 'green' }, { label: 'Matched, not funded yet', value: 1, colorKey: 'amber' },
        { label: 'No match yet', value: 1, colorKey: 'grey' }] },
      { title: 'Money by donor type', totalLabel: 'received', slices: [
        { label: IND, value: 100000, colorKey: 'rust' }, { label: CORP, value: 300000, colorKey: 'indigo' }] },
    ],
    bars: [
      { title: 'Raised against target', series: ['Raised', 'Target'], unit: '₹', bars: [
        { label: 'Millet snack programme', values: [240000, 240000] }, { label: 'Digital literacy lab', values: [120000, 520000] },
        { label: 'Sanitary pad machines', values: [40000, 160000] }, { label: 'Engineering coaching', values: [0, 300000] },
        { label: 'Library on wheels', values: [0, 280000] }] },
      { title: 'Received from individual and corporate donors, by quarter', series: [IND, CORP], unit: '₹',
        bars: quarters([[30000, 100000], [40000, 120000], [30000, 80000], [0, 0]]) },
    ],
  },
};
dashboards.SUPER_ADMIN = { ...dashboards.MANAGER!, title: 'Platform overview' };

const hoursAgo = (h: number) => new Date(Date.now() - h * 3600_000).toISOString();
let approvals: ApprovalRow[] = [
  { id: 1, subjectType: 'ORGANISATION', subjectId: 41, title: 'Shiksha Sahyog NGO', makerId: 9, status: 'SUBMITTED', revision: 1, lastComment: null, submittedAt: hoursAgo(96) },
  { id: 2, subjectType: 'PURPOSE', subjectId: 231, title: 'Mid-day meals for 400 children', makerId: 2, status: 'SUBMITTED', revision: 2, lastComment: 'Edited after approval', submittedAt: hoursAgo(50) },
  { id: 3, subjectType: 'NEED', subjectId: 877, title: 'Smart classroom kit, ZP School Khed', makerId: 4, status: 'SUBMITTED', revision: 1, lastComment: null, submittedAt: hoursAgo(48) },
  { id: 4, subjectType: 'DOCUMENT', subjectId: 2291, title: 'Trust registration certificate (v2)', makerId: 5, status: 'SUBMITTED', revision: 2, lastComment: null, submittedAt: hoursAgo(26) },
  { id: 5, subjectType: 'USER', subjectId: 8812, title: 'Vikram Rao (DONOR_INDIVIDUAL)', makerId: 8812, status: 'SUBMITTED', revision: 1, lastComment: null, submittedAt: hoursAgo(6) },
  { id: 6, subjectType: 'NEED', subjectId: 219, title: 'Digital literacy lab for adolescent girls', makerId: 5, status: 'SUBMITTED', revision: 2, lastComment: 'Edited after approval', submittedAt: hoursAgo(5) },
  { id: 7, subjectType: 'USER', subjectId: 8820, title: 'Meera Iyer (DONOR_CORPORATE)', makerId: 8820, status: 'SUBMITTED', revision: 1, lastComment: null, submittedAt: hoursAgo(3) },
];

const purposes: PurposeView[] = [
  { id: 302, refNo: 'PU100302', donorOrgId: 11, name: 'Digital classrooms for rural schools', sector: 'Education', commitmentAmount: 500000, periodicity: 'YEARLY', approved: true,
    description: 'Fund smart classrooms and computer labs in government and zilla parishad schools in rural Maharashtra, classes 5 to 10, including teacher training and two years of maintenance.' },
  { id: 305, refNo: 'PU100305', donorOrgId: 11, name: 'Nutrition for anganwadi children', sector: 'Nutrition', commitmentAmount: 400000, periodicity: 'QUARTERLY', approved: true,
    description: 'Supplementary nutrition for children under six in tribal anganwadis, with growth monitoring.' },
];

const cand = (id: number, needId: number, needName: string, kind: 'CAUSE' | 'PROJECT', score: number, sameSector: boolean, terms: string, sentence: string, remaining: number): CandidateRow =>
  ({ id, needId, needName, kind, sector: sameSector ? 'Education' : 'Other', remaining, score, engineRank: 0, decision: 'PENDING', remark: null, priority: null,
     explanation: JSON.stringify({ sameSector: String(sameSector), sharedTerms: terms, bestSentence: sentence }) });
const candidates: Record<number, CandidateRow[]> = {
  302: [
    cand(1, 877, 'Smart classroom kit, ZP School Khed', 'CAUSE', 92, true, 'smart, classroom, teacher', 'Two interactive panels and a projector for classes 5 to 7, with teacher training.', 450000),
    cand(2, 219, 'Digital literacy lab for adolescent girls', 'PROJECT', 88, true, 'computer, lab', 'A 20-seat computer lab used after school hours by 150 girls aged 12 to 16.', 400000),
    cand(3, 220, 'Tablet learning programme, classes 6–8', 'PROJECT', 84, true, 'classes, rural', 'Shared tablets with offline lessons in Marathi for 6 rural schools.', 380000),
    cand(4, 880, 'Computer lab refurbishment', 'CAUSE', 79, false, 'computer, lab', 'Replace 15 outdated computers and rewire the lab for classes 8 to 10.', 200000),
    cand(5, 221, 'Teacher training in ed-tech', 'PROJECT', 71, true, 'teacher, training', 'Six-month training for 40 government school teachers on digital lessons.', 180000),
  ].map((c, i) => ({ ...c, engineRank: i + 1 })),
  305: [
    cand(6, 301, 'Meal top-up for 3 anganwadis', 'PROJECT', 93, true, 'anganwadi, children', 'Eggs, milk and millet snacks for 210 children, five days a week.', 300000),
    cand(7, 302, 'Millet snack programme', 'PROJECT', 85, true, 'children, six', 'Locally made millet laddoos for 4 anganwadis.', 240000),
  ].map((c, i) => ({ ...c, engineRank: i + 1 })),
};

// ---------- data for the screens added in this release ----------
let sectors: SectorAdminRow[] = [['Education', 9], ['Nutrition', 3], ['Health and sanitation', 1], ['Women and girls', 0], ['Livelihood', 0], ['Environment', 0]]
  .map(([name, usedBy]) => ({ code: String(name).toUpperCase().replace(/\W+/g, '_'), name: String(name), active: true, usedBy: Number(usedBy) }));
const states: Option[] = [['MH', 'Maharashtra'], ['GJ', 'Gujarat'], ['KA', 'Karnataka'], ['MP', 'Madhya Pradesh'], ['DL', 'Delhi']]
  .map(([code, name]) => ({ code: code!, name: name! }));
const NGO_AND_SCHOOL = ['SCHOOL', 'NGO'] as DocumentTypeAdminRow['mandatoryFor'];
let documentTypes: DocumentTypeAdminRow[] = [
  { id: 1, code: 'REGISTRATION_CERT', name: 'Registration certificate', category: 'REGISTRATION', shareable: true, active: true, mandatoryFor: ['NGO'] },
  { id: 2, code: 'PAN', name: 'PAN card', category: 'REGISTRATION', shareable: false, active: true, mandatoryFor: ['DONOR_CORPORATE', 'NGO', 'SCHOOL'] },
  { id: 3, code: 'CERT_12A', name: '12A certificate', category: 'REGISTRATION', shareable: true, active: true, mandatoryFor: ['NGO'] },
  { id: 4, code: 'CERT_80G', name: '80G certificate', category: 'REGISTRATION', shareable: true, active: true, mandatoryFor: ['NGO'] },
  { id: 6, code: 'AUDITED_ACCOUNTS', name: 'Latest audited accounts', category: 'REGISTRATION', shareable: true, active: true, mandatoryFor: ['NGO'] },
  { id: 7, code: 'CANCELLED_CHEQUE', name: 'Cancelled cheque', category: 'REGISTRATION', shareable: false, active: true, mandatoryFor: NGO_AND_SCHOOL },
  { id: 9, code: 'SCHOOL_UDISE', name: 'UDISE+ school certificate', category: 'REGISTRATION', shareable: true, active: true, mandatoryFor: ['SCHOOL'] },
  { id: 10, code: 'NEED_BUDGET', name: 'Budget or quotation', category: 'NEED', shareable: true, active: true, mandatoryFor: [] },
];
const publicTypes = (): DocumentType[] => documentTypes.filter((t) => t.active)
  .map(({ id, code, name, category, shareable, mandatoryFor }) => ({ id, code, name, category, shareable, mandatoryFor }));
const need = (id: number, orgId: number, kind: 'CAUSE' | 'PROJECT', name: string, target: number, raised: number, approved = true): NeedView =>
  ({ id, refNo: `${kind === 'CAUSE' ? 'CA' : 'PR'}${100000 + id}`, orgId, kind, name, sector: 'Education', targetAmount: target, raisedAmount: raised,
     periodicity: 'ONE_TIME', approved, completed: raised >= target,
     description: `${name}: equipment, installation and training for students in rural Maharashtra, with a progress report after six months.` });
let needs: NeedView[] = [
  need(877, 21, 'CAUSE', 'Smart classroom kit', 450000, 150000), need(878, 21, 'CAUSE', 'Drinking water station', 260000, 260000),
  need(879, 21, 'CAUSE', 'Playground repair', 230000, 0, false),
  need(219, 31, 'PROJECT', 'Digital literacy lab for adolescent girls', 520000, 120000), need(220, 31, 'PROJECT', 'Millet snack programme', 240000, 240000),
];
const matchesByPurpose: Record<number, MatchRow[]> = {
  302: [{ id: 41, refNo: 'MA100041', needId: 877, needName: 'Smart classroom kit, ZP School Khed', kind: 'CAUSE', remaining: 300000, priority: 1 },
        { id: 42, refNo: 'MA100042', needId: 219, needName: 'Digital literacy lab for adolescent girls', kind: 'PROJECT', remaining: 400000, priority: 2 }],
};
let shares: ShareRow[] = [{ id: 7, needId: 219, needName: 'Digital literacy lab for adolescent girls', donorOrgId: 11, donorName: 'Asha Foundation CSR',
  ownerOrgId: 31, ownerName: 'Pragati Trust', status: 'REQUESTED' }];
let docs: DocRow[] = [
  { id: 2290, typeId: 1, name: 'registration.pdf', version: 1, state: 'REVIEWED', rejectionReason: null },
  { id: 2291, typeId: 4, name: '80g-2024.pdf', version: 1, state: 'REJECTED', rejectionReason: 'The certificate expired in March 2025. Upload the renewed one.' },
  { id: 2292, typeId: 3, name: '12a.pdf', version: 1, state: 'PENDING', rejectionReason: null },
];
let pending: ReviewRow[] = [
  { id: 2292, ownerOrgId: 31, ownerName: 'Pragati Trust', typeId: 3, typeName: '12A certificate', name: '12a.pdf', version: 1, createdBy: 'ngo@demo.humsaadhak.org' },
  { id: 2301, ownerOrgId: 41, ownerName: 'Shiksha Sahyog NGO', typeId: 1, typeName: 'Registration certificate', name: 'trust-deed.pdf', version: 2, createdBy: 'shiksha@example.org' },
];
// Staff "All documents": decided documents of several organisations; documents decided in the review queue join them
let decidedDocs: DocumentListRow[] = [
  { id: 2201, ownerOrgId: 41, ownerName: 'Shiksha Sahyog NGO', typeId: 2, typeName: 'PAN card', name: 'pan-shiksha.pdf', version: 1, state: 'REVIEWED',
    rejectionReason: null, uploadedBy: 'shiksha@demo.humsaadhak.org', uploadedAt: '2026-08-02T10:15:00Z', reviewedBy: 'manager@demo.humsaadhak.org', reviewedAt: '2026-08-03T09:40:00Z' },
  { id: 2214, ownerOrgId: 52, ownerName: 'Gyan Jyoti Vidyalaya', typeId: 9, typeName: 'UDISE+ school certificate', name: 'udise-2026.pdf', version: 1, state: 'REVIEWED',
    rejectionReason: null, uploadedBy: 'principal@gyanjyoti.example.org', uploadedAt: '2026-08-11T06:20:00Z', reviewedBy: 'manager@demo.humsaadhak.org', reviewedAt: '2026-08-12T11:05:00Z' },
  { id: 2230, ownerOrgId: 52, ownerName: 'Gyan Jyoti Vidyalaya', typeId: 7, typeName: 'Cancelled cheque', name: 'cheque-scan.jpg', version: 2, state: 'REJECTED',
    rejectionReason: 'The account number is not readable. Scan it again in better light.', uploadedBy: 'principal@gyanjyoti.example.org',
    uploadedAt: '2026-09-01T08:00:00Z', reviewedBy: 'manager@demo.humsaadhak.org', reviewedAt: '2026-09-02T12:30:00Z' },
  { id: 2246, ownerOrgId: 31, ownerName: 'Pragati Trust', typeId: 1, typeName: 'Registration certificate', name: 'trust-registration.pdf', version: 1, state: 'REVIEWED',
    rejectionReason: null, uploadedBy: 'ngo@demo.humsaadhak.org', uploadedAt: '2026-09-10T05:45:00Z', reviewedBy: 'admin@demo.humsaadhak.org', reviewedAt: '2026-09-10T14:10:00Z' },
  { id: 2259, ownerOrgId: 31, ownerName: 'Pragati Trust', typeId: 4, typeName: '80G certificate', name: '80g-2024.pdf', version: 1, state: 'REJECTED',
    rejectionReason: 'The certificate expired in March 2025. Upload the renewed one.', uploadedBy: 'ngo@demo.humsaadhak.org',
    uploadedAt: '2026-09-14T07:30:00Z', reviewedBy: 'manager@demo.humsaadhak.org', reviewedAt: '2026-09-15T10:00:00Z' },
];
const allDocuments = (): DocumentListRow[] => [
  ...pending.map((p): DocumentListRow => ({ id: p.id, ownerOrgId: p.ownerOrgId, ownerName: p.ownerName, typeId: p.typeId, typeName: p.typeName,
    name: p.name, version: p.version, state: 'PENDING', rejectionReason: null, uploadedBy: p.createdBy, uploadedAt: '2026-10-20T09:00:00Z',
    reviewedBy: null, reviewedAt: null })),
  ...decidedDocs,
].sort((a, b) => b.id - a.id);
const orgs: Record<number, OrgSummary> = {
  11: { id: 11, refNo: 'BN100011', name: 'Asha Foundation CSR', type: 'DONOR_CORPORATE', city: 'Mumbai', state: 'Maharashtra', verified: true },
  21: { id: 21, refNo: 'DB100021', name: 'ZP Primary School Khed', type: 'SCHOOL', city: 'Khed', state: 'Maharashtra', verified: true },
  31: { id: 31, refNo: 'IB100031', name: 'Pragati Trust', type: 'NGO', city: 'Satara', state: 'Maharashtra', verified: false },
};
const contacts: Record<number, ContactRow[]> = { 31: [{ role: 'PRIMARY', name: 'Anita Deshpande', designation: 'Director', email: 'ngo@demo.humsaadhak.org', phone: '+91 98200 00000' }] };
const banks: Record<number, BankRow[]> = { 31: [{ id: 1, accountName: 'Pragati Trust', accountNumber: '••••4521', ifsc: 'SBIN0001234', bankName: 'State Bank of India' }] };
let adminUsers: AdminUserRow[] = Object.values(users).map((u) => ({ id: u.id, email: u.email, fullName: u.fullName, role: u.role, roleCode: u.role, roleName: access.roleName(u.role), status: 'ACTIVE',
  orgId: u.orgId, hasPassword: true, createdAt: '2026-04-01T09:00:00Z' }));
adminUsers.push({ id: 7, email: 'vikram.rao@example.com', fullName: 'Vikram Rao', role: 'DONOR_INDIVIDUAL', roleCode: 'DONOR_INDIVIDUAL', roleName: 'Individual donor', status: 'ACTIVE', orgId: 12, hasPassword: false, createdAt: '2026-10-23T09:00:00Z' });
let imports: ImportJob[] = [];
let nextId = 5000;

/** Like the server's sign-in: the person's permissions are whatever their assigned role grants right now. */
function signedIn(u: Me): Me {
  const roleCode = u.roleCode ?? u.role;
  return { ...u, roleCode, roleName: access.roleName(roleCode), permissions: access.permissionsOf(roleCode) };
}

let demoUser: Me | null = (() => {
  try { const k = sessionStorage.getItem('demoUser'); return k ? users[k] ?? null : null; } catch { return null; }
})();

const delay = (ms = 200) => new Promise((r) => setTimeout(r, ms));

function fail(status: number, detail: string): never {
  throw Object.assign(new Error(detail), { status, detail });
}

/**
 * The demo backend: every request goes through the router below and, like the server, every audited action is recorded
 * (with the fields it changed and who may see it) whether it succeeded, was refused or failed.
 */
export async function demoApi<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const [route = '', query = ''] = path.split('?');
  if (route.startsWith('/audit/')) {
    await delay();
    try { return audit.read(route, new URLSearchParams(query), demoUser ? signedIn(demoUser) : null) as unknown as T; }
    catch (e) { const err = e as { status?: number; detail?: string; message: string }; throw new ApiError(err.status ?? 500, err.detail ?? err.message); }
  }
  const body = (init.json ?? {}) as Record<string, unknown>;
  const actor = demoUser ? signedIn(demoUser) : null;
  const subject = approvalSubject(route);
  const before = snapshot(route, subject);
  try {
    const result = await demoRoute<T>(path, init);
    audit.record({ method, route, body, result, actor: demoUser ? signedIn(demoUser) : actor, subject, owners: owners(route, subject),
      changes: before ? audit.diff(before.value, snapshot(route, subject)?.value, before.fields) : [] });
    return result;
  } catch (e) {
    const err = e as ApiError;
    audit.record({ method, route, body, actor, subject, owners: owners(route, subject), error: { status: err.status ?? 500, code: err.code, message: err.message } });
    throw e;
  }
}

/** The record an approval decides on, so its trail shows the decision. */
function approvalSubject(route: string): audit.Recorded['subject'] {
  const m = route.match(/^\/approvals\/(\d+)\/(approve|reject)$/);
  const a = m ? approvals.find((x) => x.id === Number(m[1])) : undefined;
  return a ? { type: a.subjectType, id: a.subjectId, title: a.title } : undefined;
}

const PURPOSE_FIELDS = ['name', 'description', 'sector', 'commitmentAmount', 'periodicity', 'approved'];
const NEED_FIELDS = ['name', 'description', 'sector', 'targetAmount', 'periodicity', 'approved'];

/** The purpose or need a request changes, as it is now (to show what the request changed). */
function snapshot(route: string, subject: audit.Recorded['subject']): { value: Record<string, unknown>; fields: string[] } | undefined {
  const m = route.match(/^\/(purposes|needs)\/(\d+)(\/submit)?$/);
  const kind = m ? m[1] : subject?.type === 'PURPOSE' ? 'purposes' : subject?.type === 'NEED' ? 'needs' : null;
  const id = m ? Number(m[2]) : subject?.id;
  if (kind === 'purposes') { const p = purposes.find((x) => x.id === id); return p && { value: { ...p }, fields: PURPOSE_FIELDS }; }
  if (kind === 'needs') { const n = needs.find((x) => x.id === id); return n && { value: { ...n }, fields: NEED_FIELDS }; }
  return undefined;
}

/** Organisations whose people may see the record's trail (the server's AuditOwnership). */
function owners(route: string, subject: audit.Recorded['subject']): number[] {
  const s = snapshot(route, subject)?.value;
  if (s && 'donorOrgId' in s) return [Number(s.donorOrgId)];
  if (s && 'orgId' in s) return [Number(s.orgId)];
  const org = route.match(/^\/organisations\/(\d+)\//);
  return org ? [Number(org[1])] : [];
}

/** Tiny router: method + path pattern -> handler. Mirrors the real API's paths and response shapes. */
async function demoRoute<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  await delay();
  const method = (init.method ?? 'GET').toUpperCase();
  const body = (init.json ?? {}) as Record<string, unknown>;
  const [route, query = ''] = path.split('?');
  const params = new URLSearchParams(query);
  const m = (re: RegExp) => route!.match(re);
  const ok = <R>(v: R) => v as unknown as T;
  try {
    // ----- public -----
    if (route === '/auth/login' && method === 'POST') {
      const key = String(body.email ?? '').split('@')[0]!;
      if (!users[key]) fail(422, 'Email or password is not correct, or the account is not active yet.');
      demoUser = users[key]!;
      try { sessionStorage.setItem('demoUser', key); } catch { /* storage unavailable */ }
      return ok(signedIn(demoUser));
    }
    if (route === '/auth/logout') { demoUser = null; try { sessionStorage.removeItem('demoUser'); } catch { /* storage unavailable */ } return ok(undefined); }
    if (route === '/registrations' && method === 'POST') {
      if (String(body.email).endsWith('@taken.org')) fail(422, 'An account with this email already exists. Sign in or reset your password.');
      return ok(undefined);
    }
    if (route === '/auth/password-reset') return ok(undefined);
    if (route === '/auth/set-password') {
      if (body.token === 'expired') fail(422, 'This link has expired or was already used. Ask for a new one.');
      return ok(undefined);
    }
    if (route === '/reference/sectors') return ok(sectors.filter((s) => s.active).map(({ code, name }) => ({ code, name }) as Option));
    if (route === '/reference/states') return ok(states);
    if (route === '/reference/document-types') return ok(publicTypes());

    if (!demoUser) fail(401, 'Please sign in.');
    const me = signedIn(demoUser!);   // permissions always come from the role, as on the server
    const staff = ['SUPER_ADMIN', 'MANAGER', 'ASSOCIATE'].includes(me.role);

    // ----- you -----
    if (route === '/me') return ok(signedIn(me));
    if (route === '/me/preferences') {
      if (method === 'PUT') { demoUser = { ...me, theme: body.theme as Me['theme'] }; Object.values(users).forEach((u) => { if (u.id === me.id) u.theme = demoUser!.theme; }); }
      return ok({ theme: demoUser!.theme ?? 'INDIGO' });
    }
    if (route === '/dashboards/me') return ok(withDemoDrills(dashboards[me.role]!, me.orgId ? orgs[me.orgId]?.name ?? null : null));
    if (route === '/dashboards/details') {
      const set = params.get('set') as Drill['set'];
      const filters: Record<string, string> = {};
      params.forEach((v, k) => { if (k !== 'set') filters[k] = v; });
      if (set === 'ORGANISATIONS' && !staff) fail(403, 'Organisation lists are for HumSaadhak staff.');
      return ok(demoDetails({ set, filters }));
    }
    if (route === '/dashboards/me/counters') return ok({
      approvalsWaiting: me.permissions.includes('record.approve') ? approvals.filter((a) => a.status === 'SUBMITTED' && a.makerId !== me.id).length : 0,
      documentsToReview: me.permissions.includes('document.review') ? pending.length : 0,
      shareRequestsWaiting: me.permissions.includes('share.decide') ? shares.filter((s) => s.ownerOrgId === me.orgId && s.status === 'REQUESTED').length : 0,
    } as Counters);
    if (route === '/lookups/open-needs') {
      const q = (params.get('q') ?? '').toLowerCase();
      return ok(needs.filter((n) => n.approved && !n.completed && n.raisedAmount < n.targetAmount)
        .map((n): OpenNeed => ({ id: n.id, refNo: n.refNo, name: n.name, kind: n.kind, sector: n.sector, orgId: n.orgId, orgName: orgs[n.orgId]?.name ?? '',
          orgGroup: n.kind === 'CAUSE' ? 'Direct beneficiary (school)' : 'Indirect beneficiary (NGO)', targetAmount: n.targetAmount, raisedAmount: n.raisedAmount,
          remaining: n.targetAmount - n.raisedAmount }))
        .filter((n) => !q || `${n.name} ${n.orgName} ${n.refNo}`.toLowerCase().includes(q)));
    }
    if (route === '/lookups/organisations') {
      if (!staff) fail(403, 'Staff only.');
      const donors = params.get('group') === 'DONORS';
      const all = [...Object.values(orgs), { id: 12, refNo: 'BN100012', name: 'Neha Joshi', type: 'DONOR_INDIVIDUAL', city: 'Pune', state: 'Maharashtra', verified: true },
        { id: 41, refNo: 'IB100041', name: 'Shiksha Sahyog NGO', type: 'NGO', city: 'Nashik', state: 'Maharashtra', verified: false }];
      return ok(all.filter((o) => (donors ? o.type.startsWith('DONOR') : !o.type.startsWith('DONOR')))
        .map((o): OrganisationOption => ({ id: o.id, refNo: o.refNo, name: o.name, type: o.type as OrganisationOption['type'],
          group: ({ DONOR_INDIVIDUAL: 'Individual donor', DONOR_CORPORATE: 'Corporate donor', SCHOOL: 'Direct beneficiary (school)', NGO: 'Indirect beneficiary (NGO)' } as Record<string, string>)[o.type]!,
          verified: o.verified })));
    }

    // ----- approvals -----
    if (route === '/approvals' && method === 'GET') {
      const type = params.get('type');
      const items = approvals.filter((a) => a.status === 'SUBMITTED' && (!type || a.subjectType === type));
      return ok({ items, page: 0, size: 25, total: items.length } as PageResponse<ApprovalRow>);
    }
    let x = m(/^\/approvals\/(\d+)\/history$/);
    if (x) {
      const a = approvals.find((v) => v.id === Number(x![1]))!;
      return ok([{ from: null, to: 'DRAFT', actorId: a.makerId, comment: null, at: a.submittedAt },
                 { from: 'DRAFT', to: 'SUBMITTED', actorId: a.makerId, comment: a.lastComment, at: a.submittedAt }] as StepRow[]);
    }
    x = m(/^\/approvals\/(\d+)\/(approve|reject)$/);
    if (x) {
      const a = approvals.find((v) => v.id === Number(x![1]))!;
      if (a.makerId === me.id) fail(422, 'You cannot approve or reject your own submission.');
      if (x[2] === 'reject' && !String(body.comment ?? '').trim()) fail(422, 'A reason is required to reject.');
      approvals = approvals.map((v) => (v.id === a.id ? { ...v, status: x![2] === 'approve' ? 'APPROVED' : 'REJECTED', lastComment: (body.comment as string) ?? null } : v));
      return ok(undefined);
    }

    // ----- purposes and matching -----
    if (route === '/purposes' && method === 'GET') return ok(staff ? purposes : purposes.filter((p) => p.donorOrgId === me.orgId));
    if (route === '/purposes' && method === 'POST') {
      // like the server: only staff choose the donor; everyone else creates purposes for their own organisation
      const p: PurposeView = { ...(body as object), id: ++nextId, refNo: `PU${100000 + nextId}`, approved: false,
        donorOrgId: staff ? Number(body.donorOrgId) : me.orgId! } as PurposeView;
      purposes.unshift(p);
      return ok({ id: p.id });
    }
    x = m(/^\/purposes\/(\d+)$/);
    if (x && method === 'PUT') { const i = purposes.findIndex((p) => p.id === Number(x![1])); purposes[i] = { ...purposes[i]!, ...(body as object), donorOrgId: purposes[i]!.donorOrgId, approved: false }; return ok(undefined); }
    x = m(/^\/purposes\/(\d+)\/submit$/);
    if (x) return ok(undefined);
    x = m(/^\/purposes\/(\d+)\/matches$/);
    if (x) return ok(matchesByPurpose[Number(x[1])] ?? []);
    x = m(/^\/purposes\/(\d+)\/candidates$/);
    if (x) return ok((candidates[Number(x[1])] ?? []).filter((c) => c.decision === 'PENDING'));
    x = m(/^\/purposes\/(\d+)\/candidates\/decisions$/);
    if (x) {
      const decisions = (body.decisions ?? []) as CandidateDecision[];
      const list = candidates[Number(x[1])] ?? [];
      if (decisions.length !== list.filter((c) => c.decision === 'PENDING').length) fail(422, 'Decide every candidate before confirming.');
      decisions.forEach((d) => { const c = list.find((v) => v.id === d.candidateId); if (c) c.decision = d.approve ? 'APPROVED' : 'REJECTED'; });
      return ok({ id: 43 });
    }
    if (route === '/match-runs') return ok({ id: 42 });

    // ----- causes and projects -----
    if (route === '/needs' && method === 'GET') return ok(staff ? needs : needs.filter((n) => n.orgId === me.orgId));
    if (route === '/needs' && method === 'POST') {
      const owner = staff ? Number(body.orgId) : me.orgId!;
      const n = { ...need(++nextId, owner, (body.kind as 'CAUSE' | 'PROJECT') ?? 'CAUSE', String(body.name), Number(body.targetAmount), 0, false), ...(body as object), orgId: owner } as NeedView;
      needs = [n, ...needs];
      return ok({ id: n.id });
    }
    x = m(/^\/needs\/(\d+)$/);
    if (x && method === 'PUT') { needs = needs.map((n) => (n.id === Number(x![1]) ? { ...n, ...(body as object), orgId: n.orgId, approved: false } : n)); return ok(undefined); }
    if (m(/^\/needs\/(\d+)\/submit$/)) return ok(undefined);
    if (m(/^\/needs\/(\d+)\/suggested-benefactors$/))
      return ok([{ purposeId: 302, purposeName: 'Digital classrooms for rural schools', donorOrgId: 11, score: 91 },
                 { purposeId: 310, purposeName: 'Girls’ education in Satara district', donorOrgId: 14, score: 83 }]);

    // ----- documents -----
    if (route === '/documents' && method === 'GET') return ok(docs);
    if (route === '/documents' && method === 'POST') {
      const file = (init.body as FormData).get('file') as File;
      const typeId = Number(params.get('typeId'));
      const prev = docs.find((d) => d.typeId === typeId);
      const d: DocRow = { id: ++nextId, typeId, name: file.name, version: (prev?.version ?? 0) + 1, state: 'PENDING', rejectionReason: null };
      docs = [d, ...docs.filter((v) => v.typeId !== typeId)];
      return ok({ id: d.id });
    }
    if (route === '/documents/pending') return ok(pending);
    if (route === '/documents/all') {
      const state = params.get('state'), typeId = params.get('typeId'), org = (params.get('organisation') ?? '').trim().toLowerCase();
      const page = Number(params.get('page') ?? 0), size = Number(params.get('size') ?? 25);
      const rows = allDocuments().filter((d) => (!state || d.state === state) && (!typeId || d.typeId === Number(typeId))
        && (!org || d.ownerName.toLowerCase().includes(org)));
      return ok({ items: rows.slice(page * size, page * size + size), page, size, total: rows.length } as PageResponse<DocumentListRow>);
    }
    x = m(/^\/documents\/(\d+)\/(review|reject)$/);
    if (x) {
      if (x[2] === 'reject' && !String(body.reason ?? '').trim()) fail(422, 'Say what needs fixing.');
      const decided = allDocuments().find((d) => d.id === Number(x![1]));
      if (decided) decidedDocs = [{ ...decided, state: x[2] === 'reject' ? 'REJECTED' : 'REVIEWED', rejectionReason: x[2] === 'reject' ? String(body.reason) : null,
        reviewedBy: demoUser?.email ?? 'manager@demo.humsaadhak.org', reviewedAt: new Date().toISOString() }, ...decidedDocs];
      pending = pending.filter((d) => d.id !== Number(x![1]));
      return ok(undefined);
    }
    if (m(/^\/documents\/(\d+)\/download-url$/)) return ok({ url: 'about:blank' });

    // ----- document requests -----
    if (route === '/share-requests' && method === 'GET')
      return ok(me.role === 'NGO' || me.role === 'SCHOOL' ? shares.filter((s) => s.ownerOrgId === me.orgId) : shares.filter((s) => s.donorOrgId === me.orgId));
    if (route === '/share-requests' && method === 'POST') {
      const needId = Number(params.get('needId'));
      if (shares.some((s) => s.needId === needId && s.donorOrgId === me.orgId)) fail(422, 'You have already asked for these documents.');
      const n = needs.find((v) => v.id === needId);
      const owner = n?.orgId ?? 21;
      shares = [...shares, { id: ++nextId, needId, needName: n?.name ?? `Need ${needId}`, donorOrgId: me.orgId ?? 11, donorName: orgs[me.orgId ?? 11]?.name ?? 'Donor',
        ownerOrgId: owner, ownerName: orgs[owner]?.name ?? 'Organisation', status: 'REQUESTED' }];
      return ok({ id: nextId });
    }
    x = m(/^\/share-requests\/(\d+)\/(approve|reject)$/);
    if (x) { shares = shares.map((s) => (s.id === Number(x![1]) ? { ...s, status: x![2] === 'approve' ? 'APPROVED' : 'REJECTED' } : s)); return ok(undefined); }

    // ----- organisation profile -----
    if (route === '/organisations/me') { if (!me.orgId || !orgs[me.orgId]) fail(404, 'You are not linked to an organisation.'); return ok(orgs[me.orgId!]); }
    x = m(/^\/organisations\/(\d+)\/profile$/);
    if (x) { const id = Number(x[1]); orgs[id] = { ...orgs[id]!, ...(body as object) }; return ok(orgs[id]); }
    x = m(/^\/organisations\/(\d+)\/contacts$/);
    if (x) { const id = Number(x[1]); if (method === 'PUT') contacts[id] = body as unknown as ContactRow[]; return ok(contacts[id] ?? []); }
    x = m(/^\/organisations\/(\d+)\/bank-accounts$/);
    if (x) {
      const id = Number(x[1]);
      if (method === 'POST') {
        const n = String(body.accountNumber);
        const row: BankRow = { id: ++nextId, accountName: String(body.accountName), accountNumber: `••••${n.slice(-4)}`, ifsc: String(body.ifsc), bankName: String(body.bankName || '') };
        banks[id] = [...(banks[id] ?? []), row];
        return ok(row);
      }
      return ok(banks[id] ?? []);
    }

    // ----- giving -----
    if (route === '/donations' && method === 'POST') {
      const paid = Number(body.paidAmount ?? 0);
      if (paid > 450000) fail(422, 'This cause needs ₹4,50,000 at most.');
      return ok({ donationId: 46, refNo: body.matchId ? 'DN100046' : 'DDN100046', paid, commitmentLeft: Math.max(0, 500000 - paid),
        nextDue: body.periodicity === 'ONE_TIME' ? null : '2027-10-24' } as DonationResult);
    }

    // ----- administration -----
    if (route === '/imports' && method === 'GET') return ok({ items: imports, page: 0, size: 20, total: imports.length });
    if (route === '/imports' && method === 'POST') {
      const file = (init.body as FormData).get('file') as File;
      const bad = /error|bad/i.test(file.name);
      const job: ImportJob = { id: ++nextId, type: (params.get('type') as ImportJob['type']) ?? 'NEEDS', fileName: file.name,
        status: bad ? 'REJECTED' : 'COMPLETED', rowCount: 12, createdCount: bad ? 0 : 12, errorCount: bad ? 2 : 0, createdBy: me.email,
        createdAt: new Date().toISOString(),
        errors: bad ? [{ row: 4, column: 'Sector', message: 'Use one of: Education, Environment, Health and sanitation, Livelihood, Nutrition, Women and girls.' },
                       { row: 9, column: 'Target amount', message: 'Enter an amount above zero, e.g. 4,50,000.' }] : [] };
      imports = [job, ...imports];
      return ok(job);
    }
    if (route === '/admin/users' && method === 'GET') {
      const q = (params.get('q') ?? '').toLowerCase();
      const items = adminUsers.filter((u) => !q || u.email.includes(q) || u.fullName.toLowerCase().includes(q));
      return ok({ items, page: 0, size: 25, total: items.length });
    }
    if (route === '/admin/users' && method === 'POST') {
      const email = String(body.email ?? '').trim().toLowerCase(), roleCode = String(body.roleCode ?? '');
      if (!email || !String(body.fullName ?? '').trim()) fail(400, 'Some fields are not valid.');
      if (adminUsers.some((u) => u.email.toLowerCase() === email)) fail(422, 'Someone already has an account with this email.');
      const kind = access.kindOf(roleCode);
      if (!kind || !access.fits(kind, null)) fail(422, 'Choose a staff role: this person is HumSaadhak staff.');
      const row: AdminUserRow = { id: Math.max(...adminUsers.map((u) => u.id)) + 1, email, fullName: String(body.fullName).trim(), role: kind!, roleCode,
        roleName: access.roleName(roleCode), status: 'ACTIVE', orgId: null, hasPassword: false, createdAt: new Date().toISOString() };
      adminUsers = [row, ...adminUsers];
      return ok(row);
    }
    x = m(/^\/admin\/users\/(\d+)\/(role|disable|enable|invite)$/);
    if (x) {
      const id = Number(x[1]);
      if (id === me.id && x[2] !== 'invite') fail(422, x[2] === 'role' ? 'You cannot change your own role.' : 'You cannot disable your own account.');
      if (x[2] === 'role') {
        const code = String(body.roleCode);
        const kind = access.kindOf(code) ?? fail(404, 'No such role.');
        const target = adminUsers.find((u) => u.id === id)!;
        const orgType = target.orgId ? orgs[target.orgId]?.type ?? (target.orgId === 12 ? 'DONOR_INDIVIDUAL' : null) : null;
        if (!access.fits(kind!, orgType)) fail(422, orgType == null ? 'This person is HumSaadhak staff, so they need a staff role.'
          : 'This person belongs to an organisation of a different type; choose a role for that type of account.');
        Object.values(users).forEach((u) => { if (u.id === id) { u.role = kind!; u.roleCode = code; } });
        access.logUserRoleChange(me.email, target.email, target.roleCode, code);
      }
      adminUsers = adminUsers.map((u) => (u.id !== id ? u : x![2] === 'role' ? { ...u, role: access.kindOf(String(body.roleCode))!, roleCode: String(body.roleCode), roleName: access.roleName(String(body.roleCode)) }
        : x![2] === 'disable' ? { ...u, status: 'DISABLED' } : x![2] === 'enable' ? { ...u, status: 'ACTIVE' } : u));
      return ok(adminUsers.find((u) => u.id === id));
    }

    // ----- the original project's reports (schools, NGOs, staff) -----
    if (route.startsWith('/beneficiary-reports/')) {
      if (me.role.startsWith('DONOR')) fail(403, 'These reports are for schools and NGOs. Donors see their giving on My giving and the dashboard.');
      const orgId = staff ? Number(route === '/beneficiary-reports/options' ? params.get('orgId') : body.orgId) : me.orgId!;
      if (!orgId) fail(422, 'Choose the school or NGO to report on.');
      try {
        if (route === '/beneficiary-reports/options') return ok(demoReportOptions(orgId));
        return ok(demoBeneficiaryReport(route.split('/')[2]!, orgId, body as ReportRequest));
      } catch (e) { if (e instanceof ReportProblem) fail(e.status, e.message); throw e; }
    }

    // ----- roles and permissions (super admins; the Users screen reads the role list) -----
    if (route.startsWith('/admin/access/')) {
      const can = (p: string) => signedIn(me).permissions.includes(p);
      if (!(can('admin.roles') || (route === '/admin/access/roles' && method === 'GET' && can('admin.users')))) fail(403, 'Not allowed.');
      const users = (code: string) => adminUsers.filter((u) => u.roleCode === code).length;
      try {
        if (route === '/admin/access/roles' && method === 'GET') return ok(access.listRoles(users));
        if (route === '/admin/access/roles' && method === 'POST') return ok(access.createRole(me.email, body as never));
        if (route === '/admin/access/permissions' && method === 'GET') return ok(access.listPermissions());
        if (route === '/admin/access/permissions' && method === 'POST') return ok(access.createPermission(me.email, body as never));
        if (route === '/admin/access/kinds') return ok(access.CUSTOM_KINDS);
        if (route === '/admin/access/changes') return ok(access.listChanges());
        let a = m(/^\/admin\/access\/roles\/(\w+)$/);
        if (a && method === 'PUT') return ok(access.updateRole(me.email, a[1]!, body as never));
        if (a && method === 'DELETE') { access.deleteRole(me.email, a[1]!, users(a[1]!)); return ok(undefined); }
        a = m(/^\/admin\/access\/permissions\/([\w.-]+)$/);
        if (a && method === 'PUT') return ok(access.updatePermission(me.email, a[1]!, body as never));
        if (a && method === 'DELETE') { access.deletePermission(me.email, a[1]!); return ok(undefined); }
      } catch (e) { if (e instanceof access.AccessProblem) fail(e.status, e.message); throw e; }
    }

    // ----- reference data (super admins) -----
    if (route === '/admin/sectors' && method === 'GET') return ok(sectors);
    if (route === '/admin/sectors' && method === 'POST') {
      const name = String(body.name).trim();
      if (sectors.some((s) => s.name.toLowerCase() === name.toLowerCase())) fail(422, `A sector called “${name}” already exists.`);
      const row: SectorAdminRow = { code: name.toUpperCase().replace(/\W+/g, '_'), name, active: true, usedBy: 0 };
      sectors = [...sectors, row].sort((a, b) => a.name.localeCompare(b.name));
      return ok(row);
    }
    x = m(/^\/admin\/sectors\/(\w+)$/);
    if (x) {
      const cur = sectors.find((s) => s.code === x![1])!;
      const name = String(body.name).trim();
      if (name !== cur.name && cur.usedBy > 0)
        fail(422, `“${cur.name}” is used by ${cur.usedBy} purposes, causes or projects, so it can’t be renamed. Add a new sector and switch this one off instead.`);
      const row = { ...cur, name, active: Boolean(body.active) };
      sectors = sectors.map((s) => (s.code === cur.code ? row : s));
      return ok(row);
    }
    if (route === '/admin/document-types' && method === 'GET') return ok(documentTypes);
    if (route === '/admin/document-types' && method === 'POST') {
      const row: DocumentTypeAdminRow = { id: ++nextId, code: String(body.name).toUpperCase().replace(/\W+/g, '_'), name: String(body.name).trim(),
        category: body.category as DocumentTypeAdminRow['category'], shareable: Boolean(body.shareable), active: true,
        mandatoryFor: (body.mandatoryFor ?? []) as DocumentTypeAdminRow['mandatoryFor'] };
      documentTypes = [...documentTypes, row].sort((a, b) => a.name.localeCompare(b.name));
      return ok(row);
    }
    x = m(/^\/admin\/document-types\/(\d+)$/);
    if (x) {
      if (!body.active && (body.mandatoryFor as unknown[] | undefined)?.length) fail(422, 'An inactive document type cannot be mandatory. Clear “Mandatory for” first.');
      const row = { ...documentTypes.find((t) => t.id === Number(x![1]))!, name: String(body.name).trim(), shareable: Boolean(body.shareable),
        active: Boolean(body.active), mandatoryFor: (body.mandatoryFor ?? []) as DocumentTypeAdminRow['mandatoryFor'] };
      documentTypes = documentTypes.map((t) => (t.id === row.id ? row : t));
      return ok(row);
    }

    fail(404, `Demo mode has no data for ${method} ${route}`);
  } catch (e) {
    const err = e as { status?: number; detail?: string; message: string };
    throw new ApiError(err.status ?? 500, err.detail ?? err.message);
  }
}

/** Sample rows for the Reports page in demo builds (the real files come from /api/v2/reports/*.csv). */
export function demoReport(key: string): (string | number)[][] {
  if (key === 'donations') return [
    ['Reference', 'Paid on', 'Donor', 'Donor type', 'Cause or project', 'Beneficiary', 'Beneficiary type', 'Paid (INR)', 'Pledged (INR)', 'Payment reference', 'Matched'],
    ['DN100046', '2026-10-20', 'Asha Foundation CSR', 'Corporate donor', 'Toilet block for girls', 'Ashram Shala Mokhada', 'Direct beneficiary (school)', 100000, 500000, 'SBIN526871234', 'Yes'],
    ['DN100045', '2026-10-18', 'Neha Joshi', 'Individual donor', 'Smart classroom kit', 'ZP Primary School Khed', 'Direct beneficiary (school)', 150000, '', 'HDFC77812', 'No'],
    ['DN100044', '2026-09-30', 'Asha Foundation CSR', 'Corporate donor', 'Digital literacy lab for adolescent girls', 'Pragati Trust', 'Indirect beneficiary (NGO)', 120000, 500000, 'SBIN526871102', 'Yes'],
  ];
  if (key === 'organisations') return [
    ['Reference', 'Name', 'Type', 'City', 'State', 'Verified', 'Registered on'],
    ...Object.values(orgs).map((o) => [o.refNo, o.name, ({ DONOR_INDIVIDUAL: 'Individual donor', DONOR_CORPORATE: 'Corporate donor',
      SCHOOL: 'Direct beneficiary (school)', NGO: 'Indirect beneficiary (NGO)' } as Record<string, string>)[o.type] ?? o.type,
      o.city ?? '', o.state ?? '', o.verified ? 'Yes' : 'No', '2026-04-01']),
  ];
  return [
    ['Reference', 'Name', 'Kind', 'Beneficiary', 'Beneficiary type', 'Sector', 'Target (INR)', 'Raised (INR)', 'Still needed (INR)', 'Approved', 'Completed'],
    ...needs.map((n) => [n.refNo, n.name, n.kind === 'CAUSE' ? 'Cause' : 'Project', orgs[n.orgId]?.name ?? '', n.kind === 'CAUSE' ? 'Direct beneficiary (school)' : 'Indirect beneficiary (NGO)',
      n.sector, n.targetAmount, n.raisedAmount, Math.max(0, n.targetAmount - n.raisedAmount), n.approved ? 'Yes' : 'No', n.completed ? 'Yes' : 'No']),
  ];
}
