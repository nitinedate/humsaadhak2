// @vitest-environment jsdom
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';

const T = { timeout: 4000 };

beforeAll(() => {
  vi.stubEnv('VITE_DEMO', 'true');
  vi.stubEnv('VITE_ROUTER', 'memory');
  // Recharts measures its container; jsdom has no layout engine.
  globalThis.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} } as unknown as typeof ResizeObserver;
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', { configurable: true, get: () => 600 });
  Object.defineProperty(HTMLElement.prototype, 'clientHeight', { configurable: true, get: () => 300 });
});
afterEach(() => { cleanup(); sessionStorage.clear(); });

async function renderApp() {
  const { QueryClient, QueryClientProvider } = await import('@tanstack/react-query');
  const { AuthProvider } = await import('./auth');
  const { ThemeProvider } = await import('./theme');
  const { App } = await import('./App');
  const { demoApi } = await import('../shared/api/demo');
  await demoApi('/auth/logout', { method: 'POST' });          // each test starts signed out
  render(<QueryClientProvider client={new QueryClient()}><ThemeProvider><AuthProvider><App /></AuthProvider></ThemeProvider></QueryClientProvider>);
}

async function signInAs(label: string) {
  await screen.findByRole('heading', { name: 'Sign in' }, T);
  fireEvent.click(screen.getByRole('button', { name: label }));
}

describe('HumSaadhak web app on demo data', () => {
  it('manager: dashboard, approvals and Match Studio work end to end', async () => {
    await renderApp();
    await signInAs('Manager');
    expect(await screen.findByRole('heading', { name: 'Manager dashboard' }, T)).toBeTruthy();
    expect(screen.getByRole('button', { name: /Direct beneficiaries \(schools\), verified/ })).toBeTruthy();
    expect(screen.getByText('Money received by month')).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: /^Approvals( |$)/ }));
    const row = await screen.findByRole('button', { name: /Shiksha Sahyog NGO/ }, T);
    fireEvent.click(row);
    fireEvent.click(screen.getByRole('button', { name: 'Reject…' }));
    const confirm = screen.getByRole('button', { name: 'Confirm rejection' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);                         // a reason is required
    fireEvent.change(screen.getByLabelText(/Reason for rejection/), { target: { value: 'Registration certificate unreadable' } });
    fireEvent.click(confirm);
    expect(await screen.findByText(/Rejected · the maker has been notified/, undefined, T)).toBeTruthy();

    fireEvent.click(screen.getByRole('link', { name: 'Match Studio' }));
    await screen.findByRole('heading', { name: 'Match Studio' }, T);
    const confirmBatch = await screen.findByRole('button', { name: 'Confirm batch and notify donor' }, T) as HTMLButtonElement;
    expect(confirmBatch.disabled).toBe(true);
    const groups = screen.getAllByRole('group', { name: /Decision for/ });
    groups.forEach((g, i) => fireEvent.click(within(g).getByRole('button', { name: i === groups.length - 1 ? 'Reject' : 'Approve' })));
    expect(confirmBatch.disabled).toBe(true);                    // last rejection still needs a reason
    fireEvent.change(screen.getByLabelText('Reason (required)'), { target: { value: 'Outside donor districts' } });
    expect(confirmBatch.disabled).toBe(false);
    fireEvent.click(confirmBatch);
    expect(await screen.findByText(/Batch confirmed · 4 matches/, undefined, T)).toBeTruthy();
  }, 20000);

  it('donor: dashboard and recording a donation', async () => {
    await renderApp();
    await signInAs('Corporate donor');
    expect(await screen.findByRole('heading', { name: 'Your giving at a glance' }, T)).toBeTruthy();
    expect(screen.queryByRole('link', { name: /^Approvals( |$)/ })).toBeNull();     // donors never see approvals
    fireEvent.click(screen.getByRole('link', { name: 'Record donation' }));
    // the cause comes from the open-needs API; the amount suggestion is what it still needs
    await screen.findByRole('option', { name: /Smart classroom kit — ZP Primary School Khed, still needs ₹3,00,000/ }, T);
    fireEvent.change(screen.getByLabelText('Cause or project'), { target: { value: '877' } });
    fireEvent.change(screen.getByLabelText('UTR or cheque number'), { target: { value: 'SBIN526871234' } });
    fireEvent.click(await screen.findByRole('button', { name: /Record ₹3,00,000 donation/ }, T));
    expect(await screen.findByRole('heading', { name: 'Donation recorded' }, T)).toBeTruthy();
    expect(screen.getByText(/₹3,00,000/)).toBeTruthy();
  }, 20000);

  it.each([['Direct beneficiary (school)', 'Direct beneficiary dashboard', 'Causes by funding state'],
           ['Indirect beneficiary (NGO)', 'Indirect beneficiary dashboard', 'Projects by funding state'],
           ['Operations associate', 'Your work', 'Entries this week']])('%s dashboard renders', async (label, title, chart) => {
    await renderApp();
    await signInAs(label);
    expect(await screen.findByRole('heading', { name: title }, T)).toBeTruthy();
    expect(screen.getByText(chart)).toBeTruthy();
  }, 20000);
});
