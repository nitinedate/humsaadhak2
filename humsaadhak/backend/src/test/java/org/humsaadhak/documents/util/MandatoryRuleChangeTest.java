package org.humsaadhak.documents.util;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;
import org.humsaadhak.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class MandatoryRuleChangeTest {
    @Test
    void onlyTypesWhoseRuleChangedAreRechecked() {
        assertThat(MandatoryRuleChange.affected(Set.of("NGO"), Set.of("NGO", "SCHOOL"))).isEqualTo(Set.of("SCHOOL"));
        assertThat(MandatoryRuleChange.affected(Set.of("NGO", "SCHOOL"), Set.of())).isEqualTo(Set.of("NGO", "SCHOOL"));
        assertThat(MandatoryRuleChange.affected(Set.of("NGO"), Set.of("NGO")).isEmpty()).isTrue();
    }

    @Test
    void unknownOrganisationTypesAreRefused() {
        assertThat(MandatoryRuleChange.validated(Set.of("DONOR_CORPORATE"))).isEqualTo(Set.of("DONOR_CORPORATE"));
        assertThatThrownBy(() -> MandatoryRuleChange.validated(Set.of("GUEST"))).isInstanceOf(DomainException.class);
    }
}
