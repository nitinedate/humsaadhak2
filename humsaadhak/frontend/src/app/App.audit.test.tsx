// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';

const T = { timeout: 4000 };
const saved: Blob[] = [];

beforeAll(() => {
  vi.stubEnv('VITE_DEMO', 'true');
  vi.stubEnv('VITE_ROUTER', 'memory');
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;
  URL.createObjectURL = vi.fn((b: Blob) => { saved.push(b); return 'blob:test'; }) as unknown as typeof URL.createObjectURL;
  URL.revokeObjectURL = vi.fn();
  HTMLAnchorElement.prototype.click = vi.fn();
});
afterEach(() => { cleanup(); sessionStorage.clear(); localStorage.clear(); });
const lastSaved = () => new Promise<string>((resolve) => {
  const r = new FileReader();
  r.onload = () => resolve(String(r.result).replace(/^\uFEFF/, ''));
  r.readAsText(saved[saved.length - 1]!);
});

async function renderAs(label: string) {
  const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
  const { AuthProvider } = await import('./auth');
  const { ThemeProvider } = await import('./theme');
  const { App } = await import('./App');
  const { demoApi } = await import('../shared/api/demo');
  await demoApi('/auth/logout', { method: 'POST' });
  render(<QueryClientProvider client={new QueryClient()}><ThemeProvider><AuthProvider><App /></AuthProvider></ThemeProvider></QueryClientProvider>);
  await screen.findByRole('heading', { name: 'Sign in' }, T);
  fireEvent.click(screen.getByRole('button', { name: label }));
  await screen.findByRole('navigation', { name: 'Main' }, T);
  return demoApi;
}
const go = (name: string) => fireEvent.click(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: new RegExp(`^${name}( |$)`) }));
const openPanel = async (title = 'Audit trail for this page') => fireEvent.click(await screen.findByText(title, {}, T));

describe('audit trail on the page where the change is made', () => {
  it('shows a donor who changed their purpose, what changed, and the earlier approval, without IP addresses', async () => {
    const demoApi = await renderAs('Corporate donor');
    await demoApi('/purposes/302', { method: 'PUT', json: { name: 'Digital classrooms for rural schools', description: 'Smart boards and tablets for rural Zilla Parishad schools in Pune district.',
      sector: 'Education', commitmentAmount: 2000000, periodicity: 'HALF_YEARLY' } });
    go('My purposes');
    fireEvent.click(await screen.findByRole('button', { name: 'Audit trail of Digital classrooms for rural schools' }, T));
    const drawer = await screen.findByRole('dialog', { name: /Audit trail: Digital classrooms/ }, T);
    const trail = await within(drawer).findByRole('list', { name: 'Audit trail' }, T);
    const items = within(trail).getAllByRole('listitem');
    expect(items[0]!.textContent).toContain('Edited the purpose');
    expect(items[0]!.textContent).toContain('by Meera Iyer (Corporate donor)');
    expect(items[0]!.textContent).toMatch(/Commitment amount: \d+ → 2000000/);
    expect(trail.textContent).toContain('Approved');
    expect(trail.textContent).toContain('by Priya Kulkarni (Manager)');
    expect(trail.textContent).not.toContain('10.20.4.17');           // addresses only with audit.view
  });

  it('shows each page’s recent actions, limited to the person’s own organisation', async () => {
    const demoApi = await renderAs('Direct beneficiary (school)');
    await demoApi('/needs', { method: 'POST', json: { kind: 'CAUSE', name: 'Drinking water filter', description: 'A water filter and storage tank for 220 children at the school.',
      sector: 'Health', targetAmount: 45000, periodicity: 'ONE_TIME' } });
    go('Our causes');
    await openPanel();
    const list = await screen.findByRole('list', { name: 'Audit trail' }, T);
    expect(list.textContent).toContain('Created the cause or project');
    expect(list.textContent).toContain('Drinking water filter');
    cleanup();

    await renderAs('Indirect beneficiary (NGO)');
    go('Our projects');
    await openPanel();
    expect(await screen.findByText('Nothing recorded yet.', {}, T)).toBeTruthy();
  });

  it('lists a person’s own sign-ins under My activity in Settings', async () => {
    await renderAs('Operations associate');
    go('Settings');
    await openPanel('My activity');
    const list = await screen.findByRole('list', { name: 'Audit trail' }, T);
    expect(list.textContent).toContain('Signed in');
    expect(list.textContent).toContain('Rahul Mehta');
  });
});

describe('audit log', () => {
  it('shows refused attempts with addresses, filters them and exports CSV', async () => {
    await renderAs('Manager');
    go('Audit log');
    await screen.findByRole('heading', { name: 'Audit log' }, T);
    const table = await screen.findByRole('table', {}, T);
    expect(table.textContent).toContain('Ran the matching engine');
    expect(table.textContent).toContain('10.20.4.17');

    fireEvent.change(screen.getByLabelText('Outcome'), { target: { value: 'DENIED' } });
    fireEvent.click(screen.getByRole('button', { name: 'Show' }));
    await vi.waitFor(() => {
      const rows = within(screen.getByRole('table')).getAllByRole('row').slice(1);
      expect(rows.length).toBeGreaterThan(0);
      for (const r of rows) expect(r.textContent).toContain('Refused');
    }, T);

    fireEvent.click(screen.getByRole('button', { name: 'Download CSV' }));
    await vi.waitFor(() => expect(saved.length).toBeGreaterThan(0), T);
    const csv = await lastSaved();
    expect(csv.split('\n')[0]).toContain('When,Who,Email,Role,Action,Record,Outcome');
    expect(csv).toContain('Ran the matching engine');
  });

  it('is only offered to people who may see it', async () => {
    await renderAs('Corporate donor');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).queryByRole('link', { name: /Audit log/ })).toBeNull();
  });
});
