// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';

const T = { timeout: 4000 };
// Building a real Excel or PDF file is CPU-heavy (the libraries load on first use): more time than a screen update
const EXPORT = { timeout: 15000 };

beforeAll(() => {
  vi.stubEnv('VITE_DEMO', 'true');
  vi.stubEnv('VITE_ROUTER', 'memory');
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, get: () => 600 });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => 300 });
  window.open = vi.fn() as unknown as typeof window.open;
  window.confirm = vi.fn(() => true);       // jsdom has no confirm dialog; the person says yes
  // Capture files the app saves, so tests can read what a person would download.
  URL.createObjectURL = vi.fn((b: Blob) => { saved.push(b); return 'blob:test'; }) as unknown as typeof URL.createObjectURL;
  URL.revokeObjectURL = vi.fn();
  HTMLAnchorElement.prototype.click = vi.fn();
});
const saved: Blob[] = [];
const lastSaved = () => new Promise<string>((resolve) => {        // jsdom's Blob has no .text(); FileReader works
  const r = new FileReader();
  r.onload = () => resolve(String(r.result).replace(/^\uFEFF/, ''));
  r.readAsText(saved[saved.length - 1]!);
});
afterEach(() => { cleanup(); sessionStorage.clear(); localStorage.clear(); delete document.documentElement.dataset.theme; });

async function providers(children: React.ReactNode) {
  const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
  const { AuthProvider } = await import('./auth');
  const { ThemeProvider } = await import('./theme');
  return <QueryClientProvider client={new QueryClient()}><ThemeProvider><AuthProvider>{children}</AuthProvider></ThemeProvider></QueryClientProvider>;
}

async function renderApp() {
  const { App } = await import('./App');
  const { demoApi } = await import('../shared/api/demo');
  await demoApi('/auth/logout', { method: 'POST' });
  render(await providers(<App />));
}

async function signInAs(label: string) {
  await screen.findByRole('heading', { name: 'Sign in' }, T);
  fireEvent.click(screen.getByRole('button', { name: label }));
  await screen.findByRole('navigation', { name: 'Main' }, T);
}

// Links may carry a count ("Approvals 7 waiting"), so match the start of the name.
const go = (name: string) => fireEvent.click(within(screen.getByRole('navigation', { name: 'Main' }))
  .getByRole('link', { name: new RegExp(`^${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}( |$)`) }));

describe('switching people', () => {
  it('shows the new person and none of the previous person’s data after switching role', async () => {
    const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
    const { AuthProvider } = await import('./auth');
    const { ThemeProvider } = await import('./theme');
    const { App } = await import('./App');
    const { demoApi } = await import('../shared/api/demo');
    await demoApi('/auth/logout', { method: 'POST' });
    const qc = new QueryClient();
    render(<QueryClientProvider client={qc}><ThemeProvider><AuthProvider><App /></AuthProvider></ThemeProvider></QueryClientProvider>);

    await signInAs('Manager');
    go('Approvals');
    await screen.findByRole('button', { name: /Shiksha Sahyog NGO/ }, T);
    const rail = () => screen.getByRole('complementary', { name: 'Side panel' });
    expect(within(rail()).getByText('Priya Kulkarni')).toBeTruthy();

    fireEvent.click(within(rail()).getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Corporate donor');
    expect(await within(rail()).findByText('Meera Iyer', undefined, T)).toBeTruthy();
    expect(within(rail()).queryByText('Priya Kulkarni')).toBeNull();
    expect(within(rail()).queryByRole('link', { name: 'Approvals' })).toBeNull();
    expect(await screen.findByRole('heading', { name: 'Your giving at a glance' }, T)).toBeTruthy();
    expect(qc.getQueryCache().findAll({ queryKey: ['approvals'] })).toHaveLength(0);   // manager's queue is gone

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    await signInAs('Indirect beneficiary (NGO)');
    expect(await within(rail()).findByText('Anita Deshpande', undefined, T)).toBeTruthy();
  }, 20000);
});

describe('dashboards name the four groups', () => {
  const legend = (title: string) => within(screen.getByRole('region', { name: title }));

  it('manager: individual and corporate donors, direct and indirect beneficiaries, and who gave to whom', async () => {
    await renderApp();
    await signInAs('Manager');
    await screen.findByRole('heading', { name: 'Manager dashboard' }, T);
    expect(screen.getByText(/^Manager · Financial year \d{4}–\d{2}$/)).toBeTruthy();
    const types = legend('Organisations by type');
    for (const g of ['Individual donors', 'Corporate donors', 'Direct beneficiaries (schools)', 'Indirect beneficiaries (NGOs)'])
      expect(types.getByRole('button', { name: new RegExp(g.replace(/[()]/g, '\\$&')) })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Who gave to whom' })).toBeTruthy();
    expect(screen.getByText('102 for direct, 112 for indirect beneficiaries')).toBeTruthy();
  }, 20000);

  it('donors see direct and indirect beneficiaries; individual and corporate donors each get their own label', async () => {
    await renderApp();
    await signInAs('Corporate donor');
    await screen.findByRole('heading', { name: 'Your giving at a glance' }, T);
    expect(screen.getByText(/^Corporate donor · Financial year/)).toBeTruthy();
    expect(screen.getByText('4 direct and 2 indirect beneficiaries')).toBeTruthy();
    expect(legend('Where your money went').getByRole('button', { name: /Direct beneficiaries \(schools\)/ })).toBeTruthy();
    expect(legend('Where your money went').getByRole('button', { name: /Indirect beneficiaries \(NGOs\)/ })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Individual donor');
    await screen.findByText(/^Individual donor · Financial year/, undefined, T);
    expect(await screen.findByText('1 direct and 1 indirect beneficiaries', undefined, T)).toBeTruthy();
  }, 20000);

  it('direct and indirect beneficiaries see money from individual and corporate donors', async () => {
    await renderApp();
    await signInAs('Direct beneficiary (school)');
    await screen.findByRole('heading', { name: 'Direct beneficiary dashboard' }, T);
    expect(screen.getByText('2 corporate, 3 individual')).toBeTruthy();
    expect(legend('Money by donor type').getByRole('button', { name: /Individual donors/ })).toBeTruthy();
    expect(legend('Money by donor type').getByRole('button', { name: /Corporate donors/ })).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Received from individual and corporate donors, by quarter' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Indirect beneficiary (NGO)');
    expect(await screen.findByRole('heading', { name: 'Indirect beneficiary dashboard' }, T)).toBeTruthy();
  }, 20000);
});

describe('gaps closed', () => {
  it('document requests and the review queue show names, not numbers', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Document requests');
    expect(await screen.findByText('Asha Foundation CSR', undefined, T)).toBeTruthy();
    expect(screen.getByText(/“Digital literacy lab for adolescent girls”/)).toBeTruthy();
    expect(screen.queryByText(/#\d+/)).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Manager');
    go('Documents');
    const row = (await screen.findByText('Shiksha Sahyog NGO', undefined, T)).closest('tr')!;
    expect(within(row).getByText('Registration certificate')).toBeTruthy();
  }, 20000);

  it('every dashboard downloads as CSV with its figures and charts', async () => {
    await renderApp();
    await signInAs('Manager');
    await screen.findByRole('heading', { name: 'Manager dashboard' }, T);
    fireEvent.click(screen.getByRole('button', { name: 'Download as CSV' }));
    const csv = await lastSaved();
    expect(csv.startsWith('Manager dashboard\r\nManager · Financial year')).toBe(true);
    expect(csv).toContain('Corporate donors,1720000,1370000');
    expect(csv).toContain('Indirect beneficiaries (NGOs),59');
  }, 20000);

  it('staff download reports; donations need a valid period', async () => {
    await renderApp();
    await signInAs('Manager');
    go('Reports');
    fireEvent.click(await screen.findByRole('tab', { name: 'Data exports (CSV)' }, T));
    fireEvent.click(await screen.findByRole('button', { name: 'Download donations' }, T));
    await vi.waitFor(async () => expect(await lastSaved()).toContain('Neha Joshi,Individual donor'), T);
    fireEvent.change(screen.getByLabelText('From'), { target: { value: '2026-12-01' } });
    fireEvent.change(screen.getByLabelText('To'), { target: { value: '2026-04-01' } });
    expect(screen.getByText('The end date is before the start date.')).toBeTruthy();
    expect((screen.getByRole('button', { name: 'Download donations' }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: 'Download organisations' }) as HTMLButtonElement).disabled).toBe(false);
  }, 20000);

  it('super admins manage mandatory documents and sectors', async () => {
    await renderApp();
    await signInAs('Super admin');
    go('Reference data');
    const tick = await screen.findByRole('checkbox', { name: '12A certificate mandatory for direct beneficiaries (schools)' }, T);
    fireEvent.click(tick);
    const row = tick.closest('tr')!;
    fireEvent.click(within(row).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/Saved “12A certificate”. Verification of the affected organisations is being re-checked./, undefined, T)).toBeTruthy();
    expect((screen.getByRole('checkbox', { name: '12A certificate mandatory for direct beneficiaries (schools)' }) as HTMLInputElement).checked).toBe(true);

    fireEvent.click(screen.getByRole('tab', { name: 'Sectors' }));
    const education = (await screen.findByText('Education', undefined, T)).closest('tr')!;
    expect(within(education).queryByRole('button', { name: 'Rename' })).toBeNull();          // used, so never renamed
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Sports and play' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add sector' }));
    const sports = (await screen.findByText('Sports and play', undefined, T)).closest('tr')!;
    fireEvent.click(within(sports).getByRole('button', { name: 'Switch off' }));
    expect(await screen.findByText(/“Sports and play” is switched off/, undefined, T)).toBeTruthy();
    const { demoApi } = await import('../shared/api/demo');
    const offered = await demoApi<{ name: string }[]>('/reference/sectors');
    expect(offered.map((x) => x.name)).not.toContain('Sports and play');                       // hidden from new forms
  }, 20000);
});

describe('chart details, as in the original project', () => {
  it('a donut legend row opens a dialog with exactly the records behind it, and rows open into related records', async () => {
    await renderApp();
    await signInAs('Manager');
    await screen.findByRole('heading', { name: 'Manager dashboard' }, T);
    const donut = within(screen.getByRole('region', { name: 'Organisations by type' }));
    fireEvent.click(donut.getByRole('button', { name: /Direct beneficiaries \(schools\)/ }));
    const dialog = await screen.findByRole('dialog', { name: 'Direct beneficiaries (schools)' }, T);
    expect(await within(dialog).findByText('Organisations by type · 73 records', undefined, T)).toBeTruthy();
    expect(within(dialog).getByText(/Showing the first 40 of 73/)).toBeTruthy();
    expect(within(dialog).getAllByText('Direct beneficiary (school)').length).toBe(40);
    const opener = within(dialog).getAllByRole('button', { name: /Show causes and projects for/ })[0]!;
    fireEvent.click(opener);
    expect(opener.getAttribute('aria-expanded')).toBe('true');
    expect(within(dialog).getByText(/^Causes and projects \(\d\)$/)).toBeTruthy();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(screen.queryByRole('dialog')).toBeNull();
  }, 20000);

  it('a bar opens the donations behind it and they add up to the bar', async () => {
    await renderApp();
    await signInAs('Manager');
    await screen.findByRole('heading', { name: 'Manager dashboard' }, T);
    fireEvent.click(screen.getByRole('button', { name: 'Corporate donors: To direct beneficiaries (schools)' }));
    const dialog = await screen.findByRole('dialog', { name: 'Corporate donors: To direct beneficiaries (schools)' }, T);
    await within(dialog).findAllByText('Corporate donor', undefined, T);
    const paid = within(dialog).getAllByRole('row').slice(1).map((r) => Number(within(r).getAllByRole('cell').at(-1)!.textContent!.replace(/[₹,]/g, '')));
    expect(paid.reduce((a, b) => a + b, 0)).toBe(1720000);
    expect(within(dialog).getAllByText('Direct beneficiary (school)').length).toBe(paid.length);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Download as CSV' }));
    expect(await lastSaved()).toContain('Reference,Paid on,Donor,Donor type,Cause or project,Beneficiary,Beneficiary type,Paid');
  }, 20000);

  it('beneficiaries open their own projects and the donations to them', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    await screen.findByRole('heading', { name: 'Indirect beneficiary dashboard' }, T);
    fireEvent.click(screen.getByRole('button', { name: 'Millet snack programme: Raised' }));
    const dialog = await screen.findByRole('dialog', { name: 'Millet snack programme: Raised' }, T);
    await within(dialog).findAllByText('Pragati Trust', undefined, T);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Close' }));
    fireEvent.click(within(screen.getByRole('region', { name: 'Money by donor type' })).getByRole('button', { name: /Corporate donors/ }));
    expect(await screen.findByRole('dialog', { name: 'Corporate donors' }, T)).toBeTruthy();
  }, 20000);
});

describe('forms use live data from the API', () => {
  it('a donor giving to a match sees that cause preselected and cannot give more than it still needs', async () => {
    await renderApp();
    await signInAs('Corporate donor');
    go('My giving');
    const row = (await screen.findByText('Smart classroom kit, ZP School Khed', undefined, T)).closest('tr')!;
    fireEvent.click(within(row).getByRole('link', { name: 'Give' }));
    expect(await screen.findByText(/ZP Primary School Khed · Direct beneficiary \(school\) · still needs ₹3,00,000/, undefined, T)).toBeTruthy();
    expect((screen.getByLabelText('Amount (₹)') as HTMLInputElement).value).toBe('300000');
    fireEvent.change(screen.getByLabelText('Amount (₹)'), { target: { value: '4,00,000' } });
    fireEvent.change(screen.getByLabelText('UTR or cheque number'), { target: { value: 'SBIN1' } });
    fireEvent.click(screen.getByRole('button', { name: /Record ₹4,00,000 donation/ }));
    expect(screen.getByRole('alert').textContent).toBe('This cause or project needs ₹3,00,000 at most.');
    fireEvent.click(screen.getByRole('button', { name: 'Complete it: ₹3,00,000' }));
    fireEvent.click(screen.getByRole('button', { name: /Record ₹3,00,000 donation/ }));
    expect(await screen.findByRole('heading', { name: 'Donation recorded' }, T)).toBeTruthy();
  }, 20000);

  it('staff choose which organisation a purpose belongs to, from the API', async () => {
    await renderApp();
    await signInAs('Operations associate');
    go('Purposes');
    fireEvent.click(await screen.findByRole('button', { name: 'New purpose' }, T));
    const dialog = await screen.findByRole('dialog', { name: 'New purpose' }, T);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save and submit for approval' }));
    expect(within(dialog).getByText('Choose the organisation this belongs to.')).toBeTruthy();
    await within(dialog).findByRole('option', { name: /Asha Foundation CSR — Corporate donor, BN100011/ }, T);
    fireEvent.change(within(dialog).getByLabelText('Donor'), { target: { value: '11' } });
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Clean water for tribal hostels' } });
    fireEvent.change(within(dialog).getByLabelText('Description'), { target: { value: 'Water filters and storage tanks for six ashram shala hostels in Palghar district.' } });
    await within(dialog).findByRole('option', { name: 'Health and sanitation' }, T);
    fireEvent.change(within(dialog).getByLabelText('Sector'), { target: { value: 'Health and sanitation' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save and submit for approval' }));
    expect(await screen.findByText(/Saved and sent for approval/, undefined, T)).toBeTruthy();
  }, 20000);

  it('the side panel shows what is waiting, from the counters API, and updates after a decision', async () => {
    await renderApp();
    await signInAs('Manager');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(await within(nav).findByLabelText('7 waiting', undefined, T)).toBeTruthy();
    go('Approvals');
    fireEvent.click(await screen.findByRole('button', { name: 'Approve' }, T));
    expect(await within(nav).findByLabelText('6 waiting', undefined, T)).toBeTruthy();
  }, 20000);
});

describe('reports from the original project', () => {
  const table = () => within(screen.getByRole('region', { name: /Report/ }));

  it('an NGO runs the donation report and exports it to Excel and PDF', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Reports');
    expect(await screen.findByDisplayValue('Pragati Trust [IB100031]', undefined, T)).toBeTruthy();   // the original "Name" field
    fireEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByRole('heading', { name: 'Donation Report' }, T)).toBeTruthy();
    expect(table().getByText('Entity Name - Pragati Trust [ID: IB100031]')).toBeTruthy();
    expect(table().getAllByText('Asha Foundation CSR').length).toBe(3);
    expect(table().getByText('₹1,40,000')).toBeTruthy();
    expect(table().getByText('15-Jan-2026')).toBeTruthy();

    const before = saved.length;
    fireEvent.click(table().getByRole('button', { name: 'Export as Excel' }));
    await vi.waitFor(() => expect(saved.length).toBe(before + 1), EXPORT);
    expect(saved[saved.length - 1]!.type).toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    fireEvent.click(table().getByRole('button', { name: 'Export as PDF' }));
    await vi.waitFor(() => expect(saved.length).toBe(before + 2), EXPORT);
    expect(saved[saved.length - 1]!.type).toBe('application/pdf');
  }, 30000);

  it('the project report shows correct totals and the financial year filter works', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Reports');
    fireEvent.click(await screen.findByRole('tab', { name: 'Project report' }, T));
    fireEvent.click(await screen.findByRole('button', { name: 'Show report' }, T));
    const row = (await screen.findByText('Millet snack programme', undefined, T)).closest('tr')!;
    expect(within(row).getByText('₹4,00,000')).toBeTruthy();       // sum of commitments (one match)
    expect(within(row).getByText('₹2,40,000')).toBeTruthy();       // sum of two donations, counted once each

    fireEvent.click(screen.getByRole('tab', { name: 'Donation report' }));
    fireEvent.change(await screen.findByLabelText('Year', undefined, T), { target: { value: '2026-2027' } });
    expect((screen.getByLabelText('Approved Start Date') as HTMLInputElement).disabled).toBe(true);   // year or dates, not both
    fireEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByText(/Financial year - 2026-2027/, undefined, T)).toBeTruthy();
    expect(await screen.findByText('No rows for these filters.', undefined, T)).toBeTruthy();         // its projects were approved in 2025-26
  }, 30000);

  it('the customize report shows the chosen columns, with Project and "yet to be approved" excluding each other', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Reports');
    fireEvent.click(await screen.findByRole('tab', { name: 'Customize project report' }, T));
    const project = await screen.findByRole('button', { name: 'Project' }, T);
    const pending = screen.getByRole('button', { name: 'Project (yet to be approved)' });
    expect(project.getAttribute('aria-pressed')).toBe('true');
    fireEvent.click(pending);
    expect(project.getAttribute('aria-pressed')).toBe('false');
    fireEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByText('Sanitary pad machines', undefined, T)).toBeTruthy();

    fireEvent.click(project);
    fireEvent.click(screen.getByRole('button', { name: 'Donor Name' }));
    fireEvent.click(screen.getByRole('button', { name: 'Donation Amount' }));
    fireEvent.change(screen.getByLabelText('Header'), { target: { value: 'Pragati donors 2026' } });
    fireEvent.click(screen.getByRole('button', { name: 'Show report' }));
    expect(await screen.findByRole('heading', { name: 'Pragati donors 2026' }, T)).toBeTruthy();
    const custom = within(screen.getByRole('region', { name: 'Pragati donors 2026' }));
    expect(custom.getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Project', 'Project Id', 'Donation Amount', 'Donor Name']);
    expect(custom.getAllByText('Asha Foundation CSR [ID: BN100011]').length).toBeGreaterThan(0);

    fireEvent.click(project);
    expect(screen.getByRole('alert').textContent).toBe('Choose “Project” or “Project (yet to be approved)”.');
  }, 30000);

  it('a school sees cause wording; staff choose the school or NGO; donors have no reports', async () => {
    await renderApp();
    await signInAs('Direct beneficiary (school)');
    go('Reports');
    expect(await screen.findByRole('tab', { name: 'Cause report' }, T)).toBeTruthy();
    fireEvent.click(screen.getByRole('tab', { name: 'Customize cause report' }));
    expect(await screen.findByRole('button', { name: 'Cause Doc status' }, T)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Manager');
    go('Reports');
    expect(await screen.findByText('Choose the school or NGO to report on.', undefined, T)).toBeTruthy();
    await screen.findByRole('option', { name: /ZP Primary School Khed — Direct beneficiary \(school\)/ }, T);
    fireEvent.change(screen.getByLabelText('School or NGO'), { target: { value: '21' } });
    fireEvent.click(await screen.findByRole('button', { name: 'Show report' }, T));
    expect(await screen.findByText('Entity Name - ZP Primary School Khed [ID: DB100021]', undefined, T)).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Data exports (CSV)' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Corporate donor');
    expect(within(screen.getByRole('navigation', { name: 'Main' })).queryByRole('link', { name: /^Reports/ })).toBeNull();
  }, 40000);
});

describe('roles and permissions', () => {
  it('lists built-in roles and protects the Super admin role', async () => {
    await renderApp();
    await signInAs('Super admin');
    go('Roles and permissions');
    const list = await screen.findByRole('region', { name: 'Roles' }, T);
    for (const name of ['Super admin', 'Manager', 'Operations associate', 'Individual donor', 'Corporate donor', 'Direct beneficiary (school)', 'Indirect beneficiary (NGO)', 'Guest'])
      expect(within(list).getByRole('button', { name: new RegExp(`^${name.replace(/[()]/g, '\\$&')}`) })).toBeTruthy();
    const editor = within(await screen.findByRole('region', { name: 'Role Super admin' }, T));
    expect((editor.getByRole('checkbox', { name: /Manage users/ }) as HTMLInputElement).disabled).toBe(true);
    expect((editor.getByRole('checkbox', { name: /Manage roles and permissions/ }) as HTMLInputElement).disabled).toBe(true);
    expect((editor.getByRole('checkbox', { name: /Bulk import/ }) as HTMLInputElement).disabled).toBe(false);
    expect(editor.queryByRole('button', { name: 'Delete role' })).toBeNull();             // built-in roles stay
  }, 30000);

  it('creates a permission and a role, grants it, assigns the role and the person gets it', async () => {
    await renderApp();
    await signInAs('Super admin');
    go('Roles and permissions');

    fireEvent.click(await screen.findByRole('tab', { name: 'Permissions' }, T));
    fireEvent.click(await screen.findByRole('button', { name: 'New permission' }, T));
    let drawer = within(await screen.findByRole('dialog', { name: 'New permission' }, T));
    fireEvent.change(drawer.getByLabelText('Code'), { target: { value: 'reports' } });
    expect((drawer.getByRole('button', { name: 'Create permission' }) as HTMLButtonElement).disabled).toBe(true);   // not dotted
    fireEvent.change(drawer.getByLabelText('Code'), { target: { value: 'field-visits.approve' } });
    fireEvent.change(drawer.getByLabelText('Name'), { target: { value: 'Approve field visits' } });
    fireEvent.change(drawer.getByLabelText('Area'), { target: { value: 'Field work' } });
    fireEvent.click(drawer.getByRole('button', { name: 'Create permission' }));
    expect(await screen.findByText(/Created field-visits.approve/, undefined, T)).toBeTruthy();
    const permRow = (await screen.findByText('Approve field visits', undefined, T)).closest('tr')!;
    expect(within(permRow).getByText('No role yet')).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: 'Roles' }));
    fireEvent.click(await screen.findByRole('button', { name: 'New role' }, T));
    drawer = within(await screen.findByRole('dialog', { name: 'New role' }, T));
    fireEvent.change(drawer.getByLabelText('Role name'), { target: { value: 'Regional coordinator' } });
    fireEvent.change(drawer.getByLabelText('Type of account'), { target: { value: 'ASSOCIATE' } });
    fireEvent.change(drawer.getByLabelText('Start with the permissions of'), { target: { value: 'ASSOCIATE' } });
    fireEvent.click(drawer.getByRole('button', { name: 'Create role' }));
    expect(await screen.findByText(/Created the role “Regional coordinator”/, undefined, T)).toBeTruthy();
    const editor = within(await screen.findByRole('region', { name: 'Role Regional coordinator' }, T));
    expect((editor.getByRole('checkbox', { name: /Create and edit purposes/ }) as HTMLInputElement).checked).toBe(true);   // copied
    fireEvent.click(editor.getByRole('checkbox', { name: /Approve field visits/ }));
    fireEvent.click(editor.getByRole('checkbox', { name: /Bulk import/ }));
    fireEvent.click(editor.getByRole('button', { name: 'Save role' }));
    expect(await screen.findByText(/Saved “Regional coordinator”/, undefined, T)).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: 'Permissions' }));
    // the tab first shows what it had cached, then the refreshed list
    await vi.waitFor(() => expect(within(screen.getByText('Approve field visits').closest('tr')!).getByText('Regional coordinator')).toBeTruthy(), T);

    go('Users');
    const rahul = (await screen.findByText('associate@demo.humsaadhak.org', undefined, T)).closest('tr')!;
    await within(rahul).findByRole('option', { name: 'Regional coordinator (custom)' }, T);
    expect(within(rahul).queryByRole('option', { name: /Corporate donor/ })).toBeNull();      // only roles that fit staff
    fireEvent.change(within(rahul).getByLabelText('Role of Rahul Mehta'), { target: { value: 'REGIONAL_COORDINATOR' } });
    expect(await screen.findByText(/Rahul Mehta is now Regional coordinator/, undefined, T)).toBeTruthy();

    go('Roles and permissions');
    fireEvent.click(await screen.findByRole('button', { name: /^Regional coordinator/ }, T));
    fireEvent.click(within(await screen.findByRole('region', { name: 'Role Regional coordinator' }, T)).getByRole('button', { name: 'Delete role' }));
    expect(await screen.findByText('1 person has this role. Give them another role first.', undefined, T)).toBeTruthy();

    fireEvent.click(screen.getByRole('tab', { name: 'Change log' }));
    const log = within(await screen.findByRole('table', undefined, T));
    expect(log.getByText('Person’s role changed')).toBeTruthy();
    expect(log.getByText('Added: Approve field visits; Removed: Bulk import')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Switch demo role' }));
    await signInAs('Operations associate');
    const rail = screen.getByRole('complementary', { name: 'Side panel' });
    expect(await within(rail).findByText('Regional coordinator workspace', undefined, T)).toBeTruthy();
    expect(within(screen.getByRole('navigation', { name: 'Main' })).queryByRole('link', { name: /^Bulk import/ })).toBeNull();   // removed from the role
  }, 40000);
});

describe('staff accounts', () => {
  it('a super admin adds a staff member, who is invited by email; a used email is refused', async () => {
    await renderApp();
    await signInAs('Super admin');
    go('Users');
    const add = await screen.findByRole('button', { name: 'Add staff member' }, T);
    await vi.waitFor(() => expect((add as HTMLButtonElement).disabled).toBe(false), T);   // enabled once the roles are loaded
    fireEvent.click(add);
    const drawer = await screen.findByRole('dialog', { name: 'Add staff member' }, T);
    const roleOptions = within(drawer).getAllByRole('option').map((o) => o.textContent);
    expect(roleOptions).toContain('Operations associate');
    expect(roleOptions.some((r) => /donor|school|NGO/i.test(r ?? ''))).toBe(false);      // staff roles only
    fireEvent.change(within(drawer).getByLabelText('Full name'), { target: { value: 'Anita Deshpande' } });
    fireEvent.change(within(drawer).getByLabelText('Email'), { target: { value: 'anita@humsaadhak.org' } });
    fireEvent.click(within(drawer).getByRole('button', { name: 'Add and send invitation' }));
    expect((await screen.findByRole('status', {}, T)).textContent).toMatch(/Anita Deshpande was added as .*set-password link was emailed to anita@humsaadhak.org/);
    expect(await screen.findByText('anita@humsaadhak.org', {}, T)).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'Add staff member' }));
    const again = await screen.findByRole('dialog', { name: 'Add staff member' }, T);
    fireEvent.change(within(again).getByLabelText('Full name'), { target: { value: 'Anita again' } });
    fireEvent.change(within(again).getByLabelText('Email'), { target: { value: 'anita@humsaadhak.org' } });
    fireEvent.click(within(again).getByRole('button', { name: 'Add and send invitation' }));
    expect((await within(again).findByRole('alert', {}, T)).textContent).toContain('already has an account');
  }, 20000);
});

describe('left panel (Bootstrap, responsive)', () => {
  it('stays fixed beside the page on wide screens and becomes an off-canvas menu below 992px', () => {
    const css = readFileSync('src/styles/global.css', 'utf8');            // vitest runs from the frontend folder
    const wide = css.match(/@media \(min-width: 992px\) \{[\s\S]*?\n\}/)![0];
    expect(wide).toContain('position: sticky');
    expect(wide).toContain('top: 0');
    expect(wide).toContain('height: 100vh');
    expect(readFileSync('src/main.tsx', 'utf8')).toContain("import 'bootstrap/dist/css/bootstrap.min.css'");
  });

  it('fills the width of any monitor: no page is capped to a fixed width', () => {
    const css = readFileSync('src/styles/global.css', 'utf8');
    expect(css.match(/\.page \{[^}]*\}/)![0]).toContain('max-width: none');
    const pages = execSync('grep -rln "className=\\"page\\" style={{ maxWidth" src/features || true', { encoding: 'utf8' }).trim();
    expect(pages).toBe('');
  });

  it('opens from the menu button and closes on Escape or after choosing a page', async () => {
    await renderApp();
    await signInAs('Manager');
    const panel = screen.getByRole('complementary', { name: 'Side panel' });
    expect(panel.className).toContain('offcanvas-lg');
    const menu = screen.getByRole('button', { name: 'Open menu' });
    fireEvent.click(menu);
    expect(panel.className).toContain('show');
    expect(menu.getAttribute('aria-expanded')).toBe('true');
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(panel.className).not.toContain('show');
    fireEvent.click(menu);
    go('Settings');
    await screen.findByRole('heading', { name: 'Settings' }, T);
    expect(panel.className).not.toContain('show');
  });
});

describe('themes', () => {
  it('changes immediately, is saved to the account and comes back after signing in again', async () => {
    await renderApp();
    await signInAs('Manager');
    go('Settings');
    const night = await screen.findByRole('radio', { name: /Night/ }, T);
    expect(screen.getByRole('radio', { name: /Lavender \(current\)/ }).getAttribute('aria-checked')).toBe('true');
    fireEvent.click(night);
    expect(await screen.findByText(/Night theme saved/, undefined, T)).toBeTruthy();
    expect(document.documentElement.dataset.theme).toBe('NIGHT');
    expect(localStorage.getItem('humsaadhak.theme')).toBe('NIGHT');

    fireEvent.click(screen.getByRole('button', { name: 'Sign out' }));
    localStorage.clear();                                   // a different device: only the account remembers
    await signInAs('Manager');
    await vi.waitFor(() => expect(document.documentElement.dataset.theme).toBe('NIGHT'), T);
  }, 20000);

  it('offers exactly four themes', async () => {
    const { THEMES } = await import('./theme');
    expect(THEMES.map((t) => t.name)).toEqual(['INDIGO', 'HUMSAADHAK', 'OCEAN', 'NIGHT']);
  });
});

describe('donor screens', () => {
  it('requests documents from My giving and creates a purpose with validation', async () => {
    await renderApp();
    await signInAs('Corporate donor');
    go('My giving');
    const row = (await screen.findByText('Digital literacy lab for adolescent girls', undefined, T)).closest('tr')!;
    expect(within(row).getByText('Requested')).toBeTruthy();       // already asked in the sample data
    const first = screen.getByText('Smart classroom kit, ZP School Khed').closest('tr')!;
    fireEvent.click(within(first).getByRole('button', { name: 'Request documents' }));
    expect(await screen.findByText(/Request sent/, undefined, T)).toBeTruthy();

    go('My purposes');
    fireEvent.click(await screen.findByRole('button', { name: 'New purpose' }, T));
    const dialog = await screen.findByRole('dialog', { name: 'New purpose' }, T);
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save and submit for approval' }));
    expect(within(dialog).getByText('Give it a short name.')).toBeTruthy();
    fireEvent.change(within(dialog).getByLabelText('Name'), { target: { value: 'Girls’ science kits' } });
    fireEvent.change(within(dialog).getByLabelText('Description'), { target: { value: 'Science kits and a trained mentor for girls in classes 8 to 10 in Satara district schools.' } });
    await within(dialog).findByRole('option', { name: 'Education' }, T);
    fireEvent.change(within(dialog).getByLabelText('Sector'), { target: { value: 'Education' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save and submit for approval' }));
    expect(await screen.findByText(/Saved and sent for approval/, undefined, T)).toBeTruthy();
    expect(await screen.findByText('Girls’ science kits', undefined, T)).toBeTruthy();
  }, 20000);
});

describe('NGO screens', () => {
  it('shares documents, uploads a file and sees suggested donors', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Document requests');
    fireEvent.click(await screen.findByRole('button', { name: 'Share documents' }, T));
    expect(await screen.findByText(/Shared. The donor can now open/, undefined, T)).toBeTruthy();

    go('Documents');
    await screen.findByRole('option', { name: /80G certificate/ }, T);
    fireEvent.change(screen.getByLabelText('Document'), { target: { value: '4' } });
    fireEvent.change(screen.getByLabelText('File'), { target: { files: [new File(['%PDF-1.7'], '80g-2026.pdf', { type: 'application/pdf' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }));
    expect(await screen.findByText(/Uploaded. A HumSaadhak reviewer/, undefined, T)).toBeTruthy();
    expect(await screen.findByText('80g-2026.pdf', undefined, T)).toBeTruthy();

    go('Our projects');
    fireEvent.click(await screen.findByRole('button', { name: /Digital literacy lab/ }, T));
    expect(await screen.findByText('Digital classrooms for rural schools', undefined, T)).toBeTruthy();
  }, 20000);
});

describe('compulsory documents on the upload form', () => {
  const group = (label: RegExp) => [...document.querySelectorAll('#d-type optgroup')].find((g) => label.test(g.getAttribute('label') ?? ''))!;
  const options = (g: Element) => [...g.querySelectorAll('option')].map((o) => o.textContent ?? '');

  it('a school sees its compulsory documents first, marked, with a checklist that chooses them', async () => {
    await renderApp();
    await signInAs('Direct beneficiary (school)');
    go('Documents');
    await screen.findByRole('option', { name: /UDISE\+ school certificate/ }, T);
    // "N of M uploaded": M follows the rules, which an admin can change (an earlier test adds 12A for schools)
    const counts = () => {
      const m = /^★ Compulsory for schools \((\d+) of (\d+) uploaded\)$/.exec(group(/^★ Compulsory for schools/).getAttribute('label')!)!;
      return [Number(m[1]), Number(m[2])];
    };
    await waitFor(() => expect(counts()).toBeTruthy(), T);
    const [uploadedBefore, total] = counts();
    const compulsory = options(group(/^★ Compulsory for schools/));
    expect(compulsory).toHaveLength(total);
    for (const seeded of ['Cancelled cheque', 'PAN card', 'UDISE+ school certificate'])                 // V2's rules for schools
      expect(compulsory.find((o) => o.includes(seeded))).toMatch(/^★ .*: compulsory, not uploaded yet$/);
    expect(group(/^★ Compulsory for schools/).querySelectorAll('option.opt-compulsory')).toHaveLength(total);
    expect(options(group(/^Other documents$/)).some((o) => /UDISE|PAN card|Cancelled cheque/.test(o))).toBe(false);

    const checklist = screen.getByLabelText('Compulsory documents for schools');
    expect(within(checklist).getAllByRole('button')).toHaveLength(total);
    fireEvent.click(within(checklist).getByRole('button', { name: /UDISE\+ school certificate/ }));
    expect((screen.getByLabelText('Document') as HTMLSelectElement).value).toBe('9');          // the pill chose it

    fireEvent.change(screen.getByLabelText('File'), { target: { files: [new File(['%PDF-1.7'], 'udise.pdf', { type: 'application/pdf' })] } });
    fireEvent.click(screen.getByRole('button', { name: 'Upload' }));
    expect(await screen.findByText(/Uploaded. A HumSaadhak reviewer/, undefined, T)).toBeTruthy();
    await waitFor(() => expect(counts()).toEqual([uploadedBefore + 1, total]), T);
    expect(options(group(/^★ Compulsory for schools/)).find((o) => o.includes('UDISE'))).toMatch(/compulsory, uploaded, being checked$/);
  }, 20000);

  it('an NGO sees each compulsory document\'s progress', async () => {
    await renderApp();
    await signInAs('Indirect beneficiary (NGO)');
    go('Documents');
    await screen.findByRole('option', { name: /12A certificate/ }, T);
    await waitFor(() => expect(options(group(/^★ Compulsory for NGOs/)).find((o) => o.includes('12A'))).toMatch(/being checked$/), T);
    const compulsory = options(group(/^★ Compulsory for NGOs/));
    expect(compulsory).toHaveLength(6);
    expect(compulsory.find((o) => o.includes('Registration certificate'))).toMatch(/compulsory, accepted$/);
    expect(compulsory.find((o) => o.includes('PAN card'))).toMatch(/compulsory, not uploaded yet$/);
  }, 20000);
});

describe('all documents, for reviewers only', () => {
  const rows = (table: HTMLElement) => within(table).getAllByRole('row').slice(1).filter((r) => r.querySelectorAll('td').length > 1);
  const cell = (row: HTMLElement, i: number) => row.querySelectorAll('td')[i].textContent ?? '';

  it('a manager lists every organisation\'s documents and filters them by status and organisation', async () => {
    await renderApp();
    await signInAs('Manager');
    go('Documents');
    fireEvent.click(await screen.findByRole('tab', { name: 'All documents' }, T));
    const table = await screen.findByRole('table', { name: 'All documents' }, T);
    await waitFor(() => expect(new Set(rows(table).map((r) => cell(r, 1))).size).toBeGreaterThan(1), T);   // several organisations

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'REJECTED' } });
    await waitFor(() => expect(rows(table).every((r) => cell(r, 2).startsWith('Rejected'))).toBe(true), T);
    expect(rows(table).length).toBeGreaterThan(0);
    expect(rows(table).every((r) => cell(r, 2).length > 'Rejected'.length)).toBe(true);                     // with the reason

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'REVIEWED' } });
    await waitFor(() => expect(rows(table).every((r) => cell(r, 2) === 'Approved')).toBe(true), T);
    expect(rows(table).every((r) => !cell(r, 5).includes('Not yet'))).toBe(true);                         // reviewer and time shown

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: '' } });
    fireEvent.change(screen.getByLabelText('Organisation'), { target: { value: 'pragati' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(rows(table).every((r) => cell(r, 1) === 'Pragati Trust')).toBe(true), T);
    expect(rows(table).length).toBeGreaterThan(0);

    fireEvent.change(screen.getByLabelText('Organisation'), { target: { value: 'no such organisation' } });
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    expect(await within(table).findByText('No documents match these filters.', undefined, T)).toBeTruthy();
  }, 20000);

  it('schools and NGOs see only their own documents, with no list of other organisations\' documents', async () => {
    await renderApp();
    await signInAs('Direct beneficiary (school)');
    go('Documents');
    await screen.findByRole('heading', { name: 'Upload a document' }, T);
    expect(screen.queryByRole('tab', { name: 'All documents' })).toBeNull();
    expect(screen.queryByRole('tab', { name: 'Review queue' })).toBeNull();
  }, 20000);
});

describe('administration', () => {
  it('disables a user and shows import problems by row', async () => {
    await renderApp();
    await signInAs('Super admin');
    go('Users');
    const row = (await screen.findByText('vikram.rao@example.com', undefined, T)).closest('tr')!;
    fireEvent.click(within(row).getByRole('button', { name: 'Disable' }));
    expect(await screen.findByText('Vikram Rao can no longer sign in.', undefined, T)).toBeTruthy();
    const mine = screen.getByText('admin@demo.humsaadhak.org').closest('tr')!;
    expect(within(mine).queryByRole('button', { name: 'Disable' })).toBeNull();     // nobody disables themselves

    go('Bulk import');
    fireEvent.change(await screen.findByLabelText('Excel file', undefined, T), { target: { files: [new File(['x'], 'bad-rows.xlsx')] } });
    fireEvent.click(screen.getByRole('button', { name: 'Check and import' }));
    expect(await screen.findByText(/Nothing imported: 2 problems/, undefined, T)).toBeTruthy();
    expect(screen.getByText('Target amount')).toBeTruthy();
  }, 20000);
});

describe('public pages', () => {
  it('signs up in three steps without any password', async () => {
    await renderApp();
    await screen.findByRole('heading', { name: 'Sign in' }, T);
    fireEvent.click(screen.getByRole('link', { name: 'Create an account' }));
    fireEvent.click(await screen.findByRole('radio', { name: /NGO/ }, T));
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(await screen.findByLabelText('Organisation name', undefined, T), { target: { value: 'Pragati Trust' } });
    await screen.findByRole('option', { name: 'Maharashtra' }, T);
    fireEvent.change(screen.getByLabelText('State'), { target: { value: 'Maharashtra' } });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(await screen.findByLabelText('Your full name', undefined, T), { target: { value: 'Anita Deshpande' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'anita@pragati.org' } });
    const send = screen.getByRole('button', { name: 'Send for approval' }) as HTMLButtonElement;
    expect(send.disabled).toBe(true);                                     // consent first
    fireEvent.click(screen.getByRole('checkbox'));
    fireEvent.click(send);
    expect(await screen.findByRole('heading', { name: 'Thank you, Anita' }, T)).toBeTruthy();
  }, 20000);

  it('sets a password from the emailed link and explains weak ones', async () => {
    const { MemoryRouter } = await import('react-router-dom');
    const { default: SetPasswordPage } = await import('../features/auth/SetPasswordPage');
    render(await providers(<MemoryRouter initialEntries={['/set-password?token=abc']}><SetPasswordPage /></MemoryRouter>));
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'short' } });
    expect(screen.getByText('Use at least 12 characters.')).toBeTruthy();
    fireEvent.change(screen.getByLabelText('New password'), { target: { value: 'river-lamp-42-mango' } });
    fireEvent.change(screen.getByLabelText('Type it again'), { target: { value: 'river-lamp-42-mango' } });
    fireEvent.click(screen.getByRole('button', { name: 'Set password' }));
    expect(await screen.findByRole('heading', { name: 'Password set' }, T)).toBeTruthy();
  }, 20000);
});

describe('after an NGO shares its documents', () => {
  it('the donor opens them from My giving: approved, current documents only', async () => {
    // runs after "NGO screens", where Pragati Trust agrees to share its documents with Asha Foundation CSR
    await renderApp();
    await signInAs('Corporate donor');
    go('My giving');
    const row = (await screen.findByText('Digital literacy lab for adolescent girls', undefined, T)).closest('tr')!;
    fireEvent.click(within(row).getByRole('button', { name: 'View shared documents' }));
    const list = await screen.findByRole('list', { name: 'Documents shared by Pragati Trust' }, T);
    expect(within(list).getByText('Registration certificate')).toBeTruthy();              // approved by HumSaadhak
    expect(within(list).getByRole('button', { name: 'Download Registration certificate' })).toBeTruthy();
    expect(within(list).queryByText('80G certificate')).toBeNull();                        // rejected, or its new version unchecked
    expect(within(list).queryByText('12A certificate')).toBeNull();                        // still being checked
    fireEvent.click(within(row).getByRole('button', { name: 'Hide documents' }));
    expect(screen.queryByRole('list', { name: 'Documents shared by Pragati Trust' })).toBeNull();
  }, 20000);
});

describe('Match Studio history', () => {
  it('shows approved candidates in priority order and rejected ones with the reason, apart from those to review', async () => {
    await renderApp();
    await signInAs('Manager');
    go('Match Studio');
    const approved = await screen.findByRole('region', { name: 'Approved candidates' }, T);
    const rejected = screen.getByRole('region', { name: 'Rejected candidates' });
    const coding = (await within(approved).findByText('Coding club for girls, Satara', undefined, T)).closest('tr')!;
    expect(coding.querySelector('td')!.textContent).toBe('1');                                // priority
    const water = within(rejected).getByText('Drinking water station').closest('tr')!;
    expect(within(water).getByText('Outside this donor’s focus on digital learning.')).toBeTruthy();
    expect(within(water).getByText('manager@demo.humsaadhak.org')).toBeTruthy();
    // decided ones are not offered for review again
    expect(screen.queryByRole('group', { name: 'Decision for Coding club for girls, Satara' })).toBeNull();
    expect(screen.queryByRole('group', { name: 'Decision for Drinking water station' })).toBeNull();
  }, 20000);
});
