// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { CandidateRow, PurposeView } from '../../shared/api/types';

// The server, as it answered before the fix: an already-approved candidate in the list of those to review
const purpose: PurposeView = { id: 1, refNo: 'PU100001', donorOrgId: 7, name: 'Need to provide Books to poor students',
  description: 'I am interested to provide my fund to poor students to provide books.', sector: 'Education',
  commitmentAmount: 10000, periodicity: 'ONE_TIME', approved: true };
const row = (id: number, needName: string, score: number, decision: CandidateRow['decision']): CandidateRow => ({
  id, needId: 100 + id, needName, kind: 'CAUSE', sector: 'Education', remaining: 70000, score, engineRank: id,
  explanation: '{"sameSector":true,"sharedTerms":""}', decision, remark: null, priority: decision === 'APPROVED' ? 1 : null });
const posted: unknown[] = [];

vi.mock('../../shared/api/client', async (original) => ({
  ...(await original<typeof import('../../shared/api/client')>()),
  api: vi.fn(async (path: string, init?: { method?: string; json?: unknown }) => {
    if (path === '/purposes') return [purpose];
    if (path === '/purposes/1/candidates')
      return [row(3, 'Need to construct library', 63, 'PENDING'), row(2, 'Need to provide Books to poor children', 91, 'APPROVED')];
    if (path === '/purposes/1/candidates/decisions' && init?.method === 'POST') { posted.push(init.json); return { id: 1 }; }
    return [];
  }),
}));
vi.mock('../../app/auth', () => ({ useAuth: () => ({ can: () => true, me: { role: 'MANAGER' } }) }));
vi.mock('../../shared/ui/AuditTrail', () => ({ ActivityPanel: () => null, AuditTrailButton: () => null }));

import MatchStudioPage from './MatchStudioPage';

afterEach(cleanup);

describe('Match Studio review', () => {
  it('offers only candidates still waiting, so a confirmation never resends one decided earlier', async () => {
    render(<QueryClientProvider client={new QueryClient()}><MatchStudioPage /></QueryClientProvider>);
    const library = await screen.findByRole('group', { name: 'Decision for Need to construct library' }, { timeout: 4000 });
    expect(screen.queryByRole('group', { name: 'Decision for Need to provide Books to poor children' })).toBeNull();

    fireEvent.click(within(library).getByRole('button', { name: 'Approve' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm batch and notify donor' }));
    await waitFor(() => expect(posted).toHaveLength(1), { timeout: 4000 });
    const { decisions } = posted[0] as { decisions: { candidateId: number }[] };
    expect(decisions.map((d) => d.candidateId)).toEqual([3]);                 // not candidate 2, approved earlier
  });
});
