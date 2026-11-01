package org.humsaadhak.documents.service;

import java.util.Set;
import org.humsaadhak.documents.repository.DocumentRepository;
import org.humsaadhak.documents.repository.ShareRequestRepository;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditOwnership;
import org.springframework.stereotype.Component;

/** A document's trail belongs to its owner; a share request's to both the school or NGO and the donor. */
@Component
public class DocumentAuditOwnership implements AuditOwnership {
    private final DocumentRepository documents;
    private final ShareRequestRepository shares;

    public DocumentAuditOwnership(DocumentRepository documents, ShareRequestRepository shares) { this.documents = documents; this.shares = shares; }

    @Override public Set<AuditTarget> supports() { return Set.of(AuditTarget.DOCUMENT, AuditTarget.SHARE_REQUEST); }

    @Override
    public Set<Long> organisations(AuditTarget type, String id) {
        long key = Long.parseLong(id);
        if (type == AuditTarget.DOCUMENT) return documents.findById(key).map(d -> Set.of(d.getOwnerOrgId())).orElse(Set.of());
        return shares.findById(key).map(s -> Set.of(s.getOwnerOrgId(), s.getDonorOrgId())).orElse(Set.of());
    }
}
