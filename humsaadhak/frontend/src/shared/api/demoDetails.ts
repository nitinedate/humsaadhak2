/**
 * Demo mode only: sample records behind chart segments, mirroring /api/v2/dashboards/details. Counts and money match the
 * segment that was clicked (the demo charts pass their value as _demoValue), so the preview behaves like the real thing.
 */
import type { DashboardDto, DetailChild, DetailColumn, DetailRow, DetailTable, Drill } from './types';

const C = (key: string, label: string, type: DetailColumn['type'] = 'text'): DetailColumn => ({ key, label, type });
const ORG_COLUMNS = [C('ref', 'Reference'), C('name', 'Name'), C('group', 'Group'), C('city', 'City'), C('state', 'State'),
  C('verified', 'Verified', 'yesno'), C('registered', 'Registered on', 'date'), C('needs', 'Causes or projects', 'count'),
  C('given', 'Given', 'money'), C('raised', 'Received', 'money')];
const NEED_COLUMNS = [C('ref', 'Reference'), C('name', 'Name'), C('kind', 'Kind'), C('beneficiary', 'Beneficiary'), C('sector', 'Sector'),
  C('target', 'Target', 'money'), C('raised', 'Raised', 'money'), C('remaining', 'Still needed', 'money')];
const DONATION_COLUMNS = [C('ref', 'Reference'), C('paidOn', 'Paid on', 'date'), C('donor', 'Donor'), C('donorGroup', 'Donor type'),
  C('need', 'Cause or project'), C('beneficiary', 'Beneficiary'), C('beneficiaryGroup', 'Beneficiary type'), C('amount', 'Paid', 'money')];
const PURPOSE_COLUMNS = [C('ref', 'Reference'), C('name', 'Purpose'), C('donor', 'Donor'), C('sector', 'Sector'), C('commitment', 'Commitment', 'money'),
  C('periodicity', 'How often'), C('matches', 'Confirmed matches', 'count'), C('given', 'Given', 'money')];
const CHILD_NEEDS = [C('ref', 'Reference'), C('name', 'Cause or project'), C('target', 'Target', 'money'), C('raised', 'Raised', 'money')];
const CHILD_DONATIONS = [C('ref', 'Reference'), C('paidOn', 'Paid on', 'date'), C('party', 'Donor or beneficiary'), C('need', 'Cause or project'), C('amount', 'Paid', 'money')];
const CHILD_MATCHES = [C('priority', 'Priority', 'count'), C('ref', 'Match'), C('need', 'Cause or project'), C('beneficiary', 'Beneficiary'), C('remaining', 'Still needed', 'money')];

const PEOPLE = ['Neha Joshi', 'Vikram Rao', 'Ananya Kulkarni', 'Rohan Shah', 'Kavya Nair', 'Arjun Patil', 'Meenal Deshmukh', 'Sanjay Iyer', 'Pooja Bhat', 'Rahul Gokhale'];
const COMPANIES = ['Asha Foundation CSR', 'Sahyadri Industries CSR', 'Konkan Power Trust', 'Deccan Motors Foundation', 'Pune Tech CSR', 'Godavari Agro Foundation'];
const SCHOOLS = ['ZP Primary School Khed', 'Ashram Shala Mokhada', 'ZP School Velhe', 'Govt. High School Ambegaon', 'ZP School Jawhar', 'Nutan Vidyalaya Satara'];
const NGOS = ['Pragati Trust', 'Shiksha Sahyog NGO', 'Gyan Setu Foundation', 'Disha Sanstha', 'Udaan Welfare Society', 'Sakhi Mahila Mandal'];
const CITIES = ['Pune', 'Satara', 'Nashik', 'Palghar', 'Nandurbar', 'Kolhapur'];
const CAUSES = ['Smart classroom kit', 'Drinking water station', 'Toilet block for girls', 'Playground repair', 'Library shelves and books', 'Science lab equipment'];
const PROJECTS = ['Digital literacy lab', 'Millet snack programme', 'Sanitary pad machines', 'Engineering coaching', 'Library on wheels', 'Teacher training in ed-tech'];
const SECTORS = ['Education', 'Nutrition', 'Health and sanitation'];

/** Small deterministic random generator, so the same segment always shows the same sample rows. */
function seeded(text: string) {
  let h = 2166136261;
  for (const ch of text) h = Math.imul(h ^ ch.charCodeAt(0), 16777619);
  return () => { h = Math.imul(h ^ (h >>> 15), 2246822507) ^ Math.imul(h ^ (h >>> 13), 3266489909); return ((h >>>= 0) % 10000) / 10000; };
}
const pick = <T,>(r: () => number, list: T[]) => list[Math.floor(r() * list.length)]!;
const GROUP: Record<string, string> = { DONOR_INDIVIDUAL: 'Individual donor', DONOR_CORPORATE: 'Corporate donor', SCHOOL: 'Direct beneficiary (school)', NGO: 'Indirect beneficiary (NGO)' };
const day = (r: () => number, from = '2026-04-01', span = 200) => new Date(Date.parse(from) + Math.floor(r() * span) * 86400000).toISOString().slice(0, 10);
const LIMIT = 40;

/** Split money into a few realistic gifts that add up exactly. */
function gifts(r: () => number, total: number): number[] {
  if (total <= 0) return [];
  const out: number[] = [];
  let left = total;
  while (left > 0 && out.length < LIMIT) {
    const g = left <= 150000 ? left : Math.round((20000 + r() * 130000) / 1000) * 1000;
    out.push(g); left -= g;
  }
  return out;
}

export function demoDetails(drill: Drill): DetailTable {
  const f = { ...drill.filters };
  const value = Number(f._demoValue ?? 0);
  const r = seeded(JSON.stringify(drill));
  const rows: DetailRow[] = [];
  let total = value;

  if (drill.set === 'ORGANISATIONS') {
    const type = f.type ?? (f.group === 'DONORS' ? (r() > 0.5 ? 'DONOR_CORPORATE' : 'DONOR_INDIVIDUAL') : 'NGO');
    for (let i = 0; i < Math.min(value, LIMIT); i++) {
      const t = f.group === 'DONORS' ? (i % 3 ? 'DONOR_INDIVIDUAL' : 'DONOR_CORPORATE') : type;
      const name = t === 'DONOR_INDIVIDUAL' ? PEOPLE[i % PEOPLE.length]! + (i >= PEOPLE.length ? ` ${i}` : '')
        : (t === 'DONOR_CORPORATE' ? COMPANIES : t === 'SCHOOL' ? SCHOOLS : NGOS)[i % 6]! + (i >= 6 ? ` ${Math.floor(i / 6) + 1}` : '');
      const beneficiary = t === 'SCHOOL' || t === 'NGO';
      const kids = beneficiary
        ? Array.from({ length: 1 + Math.floor(r() * 3) }, (_, k) => {
          const target = Math.round((150 + r() * 400) * 1000);
          return { ref: `${t === 'SCHOOL' ? 'CA' : 'PR'}${100200 + i * 3 + k}`, name: pick(r, t === 'SCHOOL' ? CAUSES : PROJECTS), target, raised: Math.round(target * r() / 1000) * 1000 };
        })
        : Array.from({ length: 1 + Math.floor(r() * 3) }, (_, k) => ({ ref: `DN${100300 + i * 3 + k}`, paidOn: day(r), party: pick(r, [...SCHOOLS, ...NGOS]),
          need: pick(r, [...CAUSES, ...PROJECTS]), amount: Math.round((20 + r() * 130) * 1000) }));
      const child: DetailChild = beneficiary ? { title: 'Causes and projects', columns: CHILD_NEEDS, rows: kids } : { title: 'Donations made', columns: CHILD_DONATIONS, rows: kids };
      rows.push({ values: { id: 9000 + i, ref: `${t.startsWith('DONOR') ? 'BN' : t === 'SCHOOL' ? 'DB' : 'IB'}${100100 + i}`, name, group: GROUP[t]!,
        city: pick(r, CITIES), state: 'Maharashtra', verified: f.verified ? f.verified === 'true' : r() > 0.2, registered: day(r, '2025-06-01', 480),
        needs: beneficiary ? kids.length : 0, given: beneficiary ? 0 : kids.reduce((a, k) => a + Number((k as { amount: number }).amount), 0),
        raised: beneficiary ? kids.reduce((a, k) => a + Number((k as { raised: number }).raised), 0) : 0 }, children: child });
    }
    return { total, truncated: total > rows.length, columns: ORG_COLUMNS, rows };
  }

  if (drill.set === 'NEEDS') {
    for (let i = 0; i < Math.min(value, LIMIT); i++) {
      const kind = f.kind ?? (r() > 0.5 ? 'CAUSE' : 'PROJECT');
      const target = Math.round((150 + r() * 450) * 1000);
      const funded = f.state === 'NO_MATCH' || f.state === 'MATCHED_NOT_FUNDED' || f.state === 'NEITHER' || f.state === 'MATCHED_NO_DONATION' ? 0 : Math.round(target * (0.2 + r() * 0.8) / 1000) * 1000;
      const name = pick(r, kind === 'CAUSE' ? CAUSES : PROJECTS);
      const dons = gifts(r, funded).map((amount, k) => ({ ref: `DN${100400 + i * 4 + k}`, paidOn: day(r), party: pick(r, [...PEOPLE, ...COMPANIES]), need: name, amount }));
      rows.push({ values: { id: 8000 + i, ref: `${kind === 'CAUSE' ? 'CA' : 'PR'}${100500 + i}`, name, kind: kind === 'CAUSE' ? 'Cause' : 'Project',
        beneficiary: pick(r, kind === 'CAUSE' ? SCHOOLS : NGOS), sector: pick(r, SECTORS), target, raised: funded, remaining: target - funded },
      children: { title: 'Donations received', columns: CHILD_DONATIONS, rows: dons } });
    }
    return { total, truncated: total > rows.length, columns: NEED_COLUMNS, rows };
  }

  if (drill.set === 'PURPOSES') {
    for (let i = 0; i < Math.min(value, LIMIT); i++) {
      const matched = f.state !== 'NOT_MATCHED';
      const matches = matched ? Array.from({ length: 1 + Math.floor(r() * 3) }, (_, k) => ({ priority: k + 1, ref: `MA${100600 + i * 3 + k}`,
        need: pick(r, [...CAUSES, ...PROJECTS]), beneficiary: pick(r, [...SCHOOLS, ...NGOS]), remaining: Math.round(r() * 300) * 1000 })) : [];
      rows.push({ values: { id: 7000 + i, ref: `PU${100700 + i}`, name: ['Digital classrooms for rural schools', 'Nutrition for anganwadi children', 'Girls’ science kits'][i % 3]!,
        donor: 'Asha Foundation CSR', sector: pick(r, SECTORS), commitment: 500000, periodicity: 'Yearly', matches: matches.length,
        given: f.state === 'MATCHED_DONATED' ? Math.round((50 + r() * 200) * 1000) : 0 },
      children: { title: 'Confirmed matches', columns: CHILD_MATCHES, rows: matches } });
    }
    return { total, truncated: total > rows.length, columns: PURPOSE_COLUMNS, rows };
  }

  // DONATIONS: _demoUnit tells whether the clicked value is money (split into gifts) or a count
  const amounts = f._demoUnit === 'count' ? Array.from({ length: Math.min(value, LIMIT) }, () => Math.round((20 + r() * 130) * 1000))
    : gifts(r, f._demoUnit === '₹ lakh' ? Math.round(value * 100000) : value);
  total = amounts.length;
  amounts.forEach((amount, i) => {
    const donorType = f.donorType ?? (r() > 0.6 ? 'DONOR_INDIVIDUAL' : 'DONOR_CORPORATE');
    const kind = f.needKind ?? (r() > 0.5 ? 'CAUSE' : 'PROJECT');
    const from = f.month ? `${f.month}-01` : f.from ?? '2026-04-01';
    rows.push({ values: { id: 6000 + i, ref: `DN${100800 + i}`, paidOn: day(r, from, f.month ? 28 : 200),
      donor: donorType === 'DONOR_INDIVIDUAL' ? pick(r, PEOPLE) : pick(r, COMPANIES), donorGroup: GROUP[donorType]!,
      need: f._demoNeed ?? pick(r, kind === 'CAUSE' ? CAUSES : PROJECTS), beneficiary: f._demoOrg ?? pick(r, kind === 'CAUSE' ? SCHOOLS : NGOS),
      beneficiaryGroup: GROUP[kind === 'CAUSE' ? 'SCHOOL' : 'NGO']!, amount }, children: null });
  });
  rows.sort((a, b) => String(b.values.paidOn).localeCompare(String(a.values.paidOn)));
  return { total, truncated: false, columns: DONATION_COLUMNS, rows };
}

const ORG_TYPE: Record<string, string> = {
  'Individual donors': 'DONOR_INDIVIDUAL', 'Corporate donors': 'DONOR_CORPORATE',
  'Direct beneficiaries (schools)': 'SCHOOL', 'Indirect beneficiaries (NGOs)': 'NGO',
};
const STATE: Record<string, string> = {
  'Matched and donated': 'MATCHED_DONATED', 'Matched, no donation yet': 'MATCHED_NO_DONATION', 'Donated without a match': 'DONATED_NO_MATCH',
  'Neither matched nor donated': 'NEITHER', 'Received a donation': 'RECEIVED', 'Matched, not funded yet': 'MATCHED_NOT_FUNDED', 'No match yet': 'NO_MATCH',
};
const PURPOSE_STATE: Record<string, string> = { 'Matched and donated': 'MATCHED_DONATED', 'Matched, not donated yet': 'MATCHED_NOT_DONATED', 'Not matched yet': 'NOT_MATCHED' };
const MONTH: Record<string, string> = { Apr: '2026-04', May: '2026-05', Jun: '2026-06', Jul: '2026-07', Aug: '2026-08', Sep: '2026-09', Oct: '2026-10' };
const D = (set: Drill['set'], filters: Record<string, string>): Drill => ({ set, filters });

/** Gives the demo dashboards the same drill keys the server sends (DashboardService), plus the demo-only value hints. */
export function withDemoDrills(d: DashboardDto, ownOrg: string | null): DashboardDto {
  const donuts = d.donuts.map((c) => ({ ...c, slices: c.slices.map((s) => {
    const v = { _demoValue: String(s.value) };
    let drill: Drill | null = null;
    const base = s.label.split(',')[0]!;
    if (/^Organisations/.test(c.title)) drill = D('ORGANISATIONS', { type: ORG_TYPE[s.label]!, ...v });
    else if (c.title === 'Beneficiaries by verification' || c.title === 'Donors by verification')
      drill = s.label === 'Waiting for approval' ? D('ORGANISATIONS', { group: 'DONORS', verified: 'false', ...v })
        : D('ORGANISATIONS', { type: ORG_TYPE[base]!, verified: String(s.label.endsWith('verified')), ...v });
    else if (c.title === 'Causes and projects on the platform')
      drill = s.label === 'Waiting for approval' ? D('NEEDS', { state: 'WAITING_APPROVAL', ...v }) : D('NEEDS', { kind: s.label.startsWith('Causes') ? 'CAUSE' : 'PROJECT', approved: 'true', ...v });
    else if (c.title === 'Where your money went') drill = D('DONATIONS', { needKind: s.label.startsWith('Direct') ? 'CAUSE' : 'PROJECT', from: '2026-04-01', ...v });
    else if (c.title === 'Your purposes') drill = D('PURPOSES', { state: PURPOSE_STATE[s.label]!, ...v });
    else if (/by funding state$/.test(c.title)) drill = D('NEEDS', { state: STATE[s.label]!, ...v, ...(ownOrg ? { _demoOrg: ownOrg } : {}) });
    else if (c.title === 'Money by donor type') drill = D('DONATIONS', { donorType: ORG_TYPE[s.label]!, ...v, ...(ownOrg ? { _demoOrg: ownOrg } : {}) });
    return { ...s, drill };
  }) }));
  const bars = d.bars.map((c) => ({ ...c, bars: c.bars.map((b) => {
    const drills = c.series.map((_series, i) => {
      const v = { _demoValue: String(b.values[i] ?? 0), _demoUnit: c.unit };
      if (c.title === 'Money received by month') return D('DONATIONS', { month: MONTH[b.label]!, donorType: i === 0 ? 'DONOR_INDIVIDUAL' : 'DONOR_CORPORATE', ...v });
      if (c.title === 'Who gave to whom') return D('DONATIONS', { donorType: ORG_TYPE[b.label]!, needKind: i === 0 ? 'CAUSE' : 'PROJECT', from: '2026-04-01', ...v });
      if (c.title === 'Causes and projects by funding state') return D('NEEDS', { state: STATE[b.label]!, kind: i === 0 ? 'CAUSE' : 'PROJECT', _demoValue: String(b.values[i] ?? 0) });
      if (c.title === 'Given to direct and indirect beneficiaries, by quarter') return D('DONATIONS', { quarter: b.label, fy: '2026', needKind: i === 0 ? 'CAUSE' : 'PROJECT', ...v });
      if (c.title === 'Giving by sector') return D('DONATIONS', { sector: b.label, from: '2026-04-01', ...v });
      if (c.title === 'Raised against target') return D('DONATIONS', { needId: b.label, _demoValue: String(b.values[0] ?? 0), _demoUnit: '₹', _demoNeed: b.label, ...(ownOrg ? { _demoOrg: ownOrg } : {}) });
      if (c.title.startsWith('Received from individual and corporate donors'))
        return D('DONATIONS', { quarter: b.label, fy: '2026', donorType: i === 0 ? 'DONOR_INDIVIDUAL' : 'DONOR_CORPORATE', ...v, ...(ownOrg ? { _demoOrg: ownOrg } : {}) });
      return null;
    });
    return { ...b, drills: drills.some(Boolean) ? drills : null };
  }) }));
  return { ...d, donuts, bars };
}
