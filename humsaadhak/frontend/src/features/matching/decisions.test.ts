import { describe, expect, it } from 'vitest';
import { buildDecisions } from './decisions';

describe('buildDecisions', () => {
  it('numbers approved candidates 1..n in display order', () => {
    const { decisions, problem } = buildDecisions([3, 1, 2], { 3: 'A', 1: 'R', 2: 'A' }, { 1: 'Outside districts' });
    expect(problem).toBeNull();
    expect(decisions).toEqual([
      { candidateId: 3, approve: true, remark: null, priority: 1 },
      { candidateId: 1, approve: false, remark: 'Outside districts', priority: null },
      { candidateId: 2, approve: true, remark: null, priority: 2 },
    ]);
  });
  it('blocks until everything is decided and rejections have reasons', () => {
    expect(buildDecisions([1, 2], { 1: 'A' }, {}).problem).toBe('Decide 1 more candidate.');
    expect(buildDecisions([1, 2], { 1: 'A', 2: 'R' }, {}).problem).toBe('Add a reason to every rejection.');
    expect(buildDecisions([1], { 1: 'R' }, { 1: 'x' }).problem).toBe('Approve at least one candidate.');
  });
});
