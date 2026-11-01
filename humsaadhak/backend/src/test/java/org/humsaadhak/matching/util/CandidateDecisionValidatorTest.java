package org.humsaadhak.matching.util;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.humsaadhak.matching.util.CandidateDecisionValidator.Decision;
import org.junit.jupiter.api.Test;

class CandidateDecisionValidatorTest {
    Set<Long> shown = Set.of(1L, 2L, 3L);

    @Test void acceptsCompleteOrderedDecisions() {
        assertThatNoException().isThrownBy(() -> CandidateDecisionValidator.validate(shown, List.of(
                new Decision(1, true, null, 2), new Decision(2, true, null, 1), new Decision(3, false, "Outside donor's districts", null))));
    }

    @Test void everyCandidateMustBeDecided() {
        assertThatThrownBy(() -> CandidateDecisionValidator.validate(shown, List.of(new Decision(1, true, null, 1))))
                .hasMessageContaining("Decide every candidate");
    }

    @Test void rejectionNeedsRemark() {
        assertThatThrownBy(() -> CandidateDecisionValidator.validate(shown, List.of(
                new Decision(1, true, null, 1), new Decision(2, true, null, 2), new Decision(3, false, "", null))))
                .hasMessageContaining("reason");
    }

    @Test void prioritiesMustBeOneToNWithoutGaps() {
        assertThatThrownBy(() -> CandidateDecisionValidator.validate(shown, List.of(
                new Decision(1, true, null, 1), new Decision(2, true, null, 1), new Decision(3, false, "x", null))))
                .hasMessageContaining("no gaps or repeats");
        assertThatThrownBy(() -> CandidateDecisionValidator.validate(shown, List.of(
                new Decision(1, true, null, 1), new Decision(2, true, null, 3), new Decision(3, false, "x", null))))
                .hasMessageContaining("no gaps or repeats");
    }
}
