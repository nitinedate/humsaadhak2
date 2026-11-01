package org.humsaadhak.catalogue.api;

import java.math.BigDecimal;
import java.util.Optional;
import org.humsaadhak.shared.model.Periodicity;

/** Read access for matching, giving and reporting. */
public interface Catalogue {
    record PurposeView(long id, String refNo, long donorOrgId, String name, String description, String sector,
                       BigDecimal commitmentAmount, Periodicity periodicity, boolean approved) {}
    record NeedView(long id, String refNo, long orgId, NeedKind kind, String name, String description, String sector,
                    BigDecimal targetAmount, BigDecimal raisedAmount, Periodicity periodicity, boolean approved, boolean completed) {
        public BigDecimal remaining() { return targetAmount.subtract(raisedAmount).max(BigDecimal.ZERO); }
    }
    Optional<PurposeView> purpose(long id);
    Optional<NeedView> need(long id);
    record NeedDraft(long orgId, NeedKind kind, String name, String description, String sector, BigDecimal targetAmount, Periodicity periodicity) {}
    record PurposeDraft(long donorOrgId, String name, String description, String sector, BigDecimal commitmentAmount, Periodicity periodicity) {}

    /** Creates a draft need and opens its approval (used by the importer). */
    long createNeed(NeedDraft draft, long makerId);
    /** Creates a draft purpose and opens its approval (used by the importer). */
    long createPurpose(PurposeDraft draft, long makerId);

    /** Called by giving inside the donation transaction so raised amounts never drift. */
    void recordPayment(long needId, Long purposeId, BigDecimal paid);
}
