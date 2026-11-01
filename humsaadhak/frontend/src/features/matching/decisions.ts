import type { CandidateDecision } from '../../shared/api/types';

export type Choice = 'A' | 'R';

/** Same rules as the server's CandidateDecisionValidator, checked before submitting. */
export function buildDecisions(order: number[], choices: Record<number, Choice>, remarks: Record<number, string>):
    { decisions: CandidateDecision[]; problem: string | null } {
  let priority = 0;
  const decisions = order.map((id) => {
    const approve = choices[id] === 'A';
    return { candidateId: id, approve, remark: approve ? null : (remarks[id] ?? '').trim(), priority: approve ? ++priority : null };
  });
  const undecided = order.filter((id) => !choices[id]).length;
  const problem = undecided > 0 ? `Decide ${undecided} more candidate${undecided === 1 ? '' : 's'}.`
    : decisions.some((d) => !d.approve && !d.remark) ? 'Add a reason to every rejection.'
    : priority === 0 ? 'Approve at least one candidate.'
    : null;
  return { decisions, problem };
}
