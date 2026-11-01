package org.humsaadhak.matching.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one engine run does with each candidate of a purpose (pure, unit-tested; MatchingService applies it):
 * a new suggestion at or above the store threshold is created; a candidate still waiting for review takes the new
 * verdict (otherwise scores from an older engine would stay on screen); a candidate already approved or rejected keeps
 * what the reviewer saw; and a waiting candidate the engine no longer suggests is hidden (score 0), not deleted.
 */
public final class RerunPlan {
    public enum Action { CREATE, RESCORE, KEEP, HIDE }
    public record Hit(long needId, int score) {}
    public record Step(long needId, Action action) {}

    private RerunPlan() {}

    /** @param existing need id -> true when that candidate is still waiting for review (false: already decided) */
    public static List<Step> plan(Map<Long, Boolean> existing, List<Hit> hits, int storeThreshold) {
        List<Step> steps = new ArrayList<>();
        Set<Long> returned = new LinkedHashSet<>();
        for (Hit h : hits) {
            if (!returned.add(h.needId())) continue;
            Boolean pending = existing.get(h.needId());
            if (pending == null) {
                if (h.score() >= storeThreshold) steps.add(new Step(h.needId(), Action.CREATE));
            } else {
                steps.add(new Step(h.needId(), pending ? Action.RESCORE : Action.KEEP));
            }
        }
        existing.forEach((needId, pending) -> {
            if (pending && !returned.contains(needId)) steps.add(new Step(needId, Action.HIDE));
        });
        return steps;
    }
}
