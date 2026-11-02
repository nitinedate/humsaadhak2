package org.humsaadhak.matching.util;

import java.util.*;
import org.humsaadhak.shared.exception.DomainException;

/**
 * Rules for confirming a purpose's candidates (pure, unit-tested). Mirrors the legacy review screen and adds what it missed:
 *  - every shown candidate must be decided (no silent leftovers)
 *  - every rejection needs a remark (it becomes a training label)
 *  - approved candidates carry priorities 1..n with no gaps or repeats (the order the donor sees)
 *  - at least one approval
 */
public final class CandidateDecisionValidator {
    public record Decision(long candidateId, boolean approve, String remark, Integer priority) {}

    private CandidateDecisionValidator() {}

    public static void validate(Set<Long> shownCandidateIds, List<Decision> decisions) {
        validate(shownCandidateIds, Set.of(), decisions);
    }

    /**
     * @param alreadyDecided this purpose's candidates approved or rejected earlier: a screen opened before that decision
     *                       still offers them, and is told so (not that they belong to another purpose)
     */
    public static void validate(Set<Long> shownCandidateIds, Set<Long> alreadyDecided, List<Decision> decisions) {
        Set<Long> decided = new HashSet<>();
        for (Decision d : decisions) {
            if (alreadyDecided.contains(d.candidateId()))
                throw new DomainException("already-decided", "Candidate " + d.candidateId()
                        + " was already approved or rejected. Reload the page to see what is still waiting for review.");
            if (!shownCandidateIds.contains(d.candidateId()))
                throw new DomainException("unknown-candidate", "Candidate " + d.candidateId() + " does not belong to this purpose.");
            if (!decided.add(d.candidateId()))
                throw new DomainException("duplicate-decision", "Candidate " + d.candidateId() + " was decided twice.");
            if (!d.approve() && (d.remark() == null || d.remark().isBlank()))
                throw new DomainException("remark-required", "Add a reason to every rejection.");
        }
        if (!decided.containsAll(shownCandidateIds))
            throw new DomainException("undecided", "Decide every candidate before confirming.");

        List<Integer> priorities = decisions.stream().filter(Decision::approve).map(Decision::priority).toList();
        if (priorities.isEmpty()) throw new DomainException("nothing-approved", "Approve at least one candidate, or reject the run.");
        if (priorities.contains(null)) throw new DomainException("priority-missing", "Give every approved candidate a priority.");
        List<Integer> sorted = new ArrayList<>(priorities);
        Collections.sort(sorted);
        for (int i = 0; i < sorted.size(); i++)
            if (sorted.get(i) != i + 1)
                throw new DomainException("priority-gaps", "Priorities must run 1 to " + sorted.size() + " with no gaps or repeats.");
    }
}
