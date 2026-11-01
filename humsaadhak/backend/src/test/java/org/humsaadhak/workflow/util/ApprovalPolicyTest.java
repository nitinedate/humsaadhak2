package org.humsaadhak.workflow.util;

import static org.assertj.core.api.Assertions.*;
import static org.humsaadhak.workflow.api.ApprovalStatus.*;

import org.humsaadhak.shared.exception.DomainException;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {
    @Test void submitOnlyFromDraftOrRejected() {
        assertThat(ApprovalPolicy.submit(DRAFT)).isEqualTo(SUBMITTED);
        assertThat(ApprovalPolicy.submit(REJECTED)).isEqualTo(SUBMITTED);
        assertThatThrownBy(() -> ApprovalPolicy.submit(APPROVED)).isInstanceOf(DomainException.class);
    }

    @Test void checkerMustDifferFromMaker() {
        assertThatThrownBy(() -> ApprovalPolicy.decide(SUBMITTED, 7, 7, true, null))
                .hasMessageContaining("your own submission");
        assertThat(ApprovalPolicy.decide(SUBMITTED, 7, 8, true, null)).isEqualTo(APPROVED);
    }

    @Test void rejectNeedsReason() {
        assertThatThrownBy(() -> ApprovalPolicy.decide(SUBMITTED, 1, 2, false, " ")).hasMessageContaining("reason");
        assertThat(ApprovalPolicy.decide(SUBMITTED, 1, 2, false, "Amount mismatch")).isEqualTo(REJECTED);
    }

    @Test void onlySubmittedCanBeDecided() {
        assertThatThrownBy(() -> ApprovalPolicy.decide(DRAFT, 1, 2, true, null)).isInstanceOf(DomainException.class);
    }

    @Test void materialEditOfApprovedGoesBackToReview() {
        assertThat(ApprovalPolicy.afterMaterialEdit(APPROVED)).isEqualTo(SUBMITTED);
        assertThat(ApprovalPolicy.afterMaterialEdit(DRAFT)).isEqualTo(DRAFT);
    }
}
