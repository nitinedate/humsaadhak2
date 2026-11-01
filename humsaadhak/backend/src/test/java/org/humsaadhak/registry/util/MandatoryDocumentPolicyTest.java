package org.humsaadhak.registry.util;

import static org.assertj.core.api.Assertions.*;
import static org.humsaadhak.registry.util.MandatoryDocumentPolicy.DocState.*;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MandatoryDocumentPolicyTest {
    @Test void verifiedOnlyWhenEveryMandatoryTypeIsReviewed() {
        Set<Long> mandatory = Set.of(1L, 2L, 3L);
        assertThat(MandatoryDocumentPolicy.isVerified(mandatory, Map.of(1L, REVIEWED, 2L, REVIEWED, 3L, REVIEWED))).isTrue();
        assertThat(MandatoryDocumentPolicy.isVerified(mandatory, Map.of(1L, REVIEWED, 2L, REVIEWED))).isFalse();
        assertThat(MandatoryDocumentPolicy.isVerified(mandatory, Map.of(1L, REVIEWED, 2L, REJECTED, 3L, REVIEWED))).isFalse();
        assertThat(MandatoryDocumentPolicy.isVerified(Set.of(), Map.of())).isFalse();
    }
}
