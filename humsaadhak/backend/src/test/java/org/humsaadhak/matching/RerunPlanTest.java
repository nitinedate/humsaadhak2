package org.humsaadhak.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.humsaadhak.matching.util.RerunPlan;
import org.humsaadhak.matching.util.RerunPlan.Action;
import org.humsaadhak.matching.util.RerunPlan.Hit;
import org.humsaadhak.matching.util.RerunPlan.Step;
import org.junit.jupiter.api.Test;

class RerunPlanTest {
    @Test
    void aRerunRefreshesWaitingCandidatesAndLeavesDecidedOnesAlone() {
        Map<Long, Boolean> existing = new LinkedHashMap<>();
        existing.put(1L, true);      // waiting: shown with the old engine's 100
        existing.put(2L, false);     // approved already
        existing.put(3L, true);      // waiting, but no longer suggested
        List<Step> steps = RerunPlan.plan(existing, List.of(new Hit(1, 72), new Hit(2, 91), new Hit(4, 64), new Hit(5, 30)), 40);
        assertThat(steps).isEqualTo(List.of(
                new Step(1, Action.RESCORE),     // takes the new 72
                new Step(2, Action.KEEP),        // the reviewer's decision stands
                new Step(4, Action.CREATE),      // new and good enough to keep
                new Step(3, Action.HIDE)));      // 5 (30) is below the store threshold: nothing
    }

    @Test
    void aFirstRunCreatesWhatReachesTheThresholdOnceEach() {
        List<Step> steps = RerunPlan.plan(Map.of(), List.of(new Hit(7, 40), new Hit(7, 40), new Hit(8, 39)), 40);
        assertThat(steps).isEqualTo(List.of(new Step(7, Action.CREATE)));
    }
}
