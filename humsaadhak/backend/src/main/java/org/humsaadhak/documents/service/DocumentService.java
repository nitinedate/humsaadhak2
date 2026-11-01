package org.humsaadhak.documents.service;

import org.humsaadhak.documents.entity.Document;
import org.humsaadhak.documents.util.DocumentAccessPolicy;
import org.humsaadhak.documents.repository.DocumentRepository;
import org.humsaadhak.documents.entity.DocumentType;
import org.humsaadhak.documents.repository.DocumentTypeRepository;
import org.humsaadhak.documents.entity.DocumentTypeRule;
import org.humsaadhak.documents.repository.DocumentTypeRuleRepository;
import org.humsaadhak.documents.entity.ShareRequest;
import org.humsaadhak.documents.repository.ShareRequestRepository;
import org.humsaadhak.documents.client.StorageGateway;
import org.humsaadhak.documents.util.UploadPolicy;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.documents.api.DocumentReviewed;
import org.humsaadhak.documents.api.DocumentStates;
import org.humsaadhak.documents.api.ShareEvents.ShareDecided;
import org.humsaadhak.documents.api.ShareEvents.ShareRequested;
import org.humsaadhak.matching.api.Matches;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DocumentService implements DocumentStates {
    public static final Duration LINK_TTL = Duration.ofMinutes(5);

    private final DocumentRepository documents;
    private final DocumentTypeRepository types;
    private final DocumentTypeRuleRepository rules;
    private final ShareRequestRepository shares;
    private final StorageGateway storage;
    private final Catalogue catalogue;
    private final Matches matches;
    private final ApplicationEventPublisher events;

    public DocumentService(DocumentRepository documents, DocumentTypeRepository types, DocumentTypeRuleRepository rules,
                    ShareRequestRepository shares, StorageGateway storage, Catalogue catalogue, Matches matches,
                    ApplicationEventPublisher events) {
        this.documents = documents; this.types = types; this.rules = rules; this.shares = shares;
        this.storage = storage; this.catalogue = catalogue; this.matches = matches; this.events = events;
    }

    public long upload(CurrentUser me, Long ownerOrgId, long typeId, Long needId, Long donationId,
                String fileName, long size, InputStream in) throws IOException {
        long owner = me.role().isStaff() && ownerOrgId != null ? ownerOrgId : Objects.requireNonNull(me.orgId(), "orgId");
        types.findById(typeId).orElseThrow(() -> new NotFoundException("Document type", typeId));
        PushbackInputStream stream = new PushbackInputStream(in, 16);
        byte[] head = stream.readNBytes(16);
        stream.unread(head);
        String contentType = UploadPolicy.check(fileName, size, head);

        Optional<Document> previous = documents.findFirstByOwnerOrgIdAndDocumentTypeIdAndSupersededFalse(owner, typeId);
        int version = previous.map(d -> d.getDocVersion() + 1).orElse(1);
        previous.ifPresent(Document::supersede);

        LocalDate today = LocalDate.now();
        String key = "org-%d/%d/%02d/%s".formatted(owner, today.getYear(), today.getMonthValue(), UUID.randomUUID());
        storage.put(key, stream, size, contentType);
        return documents.save(new Document(owner, typeId, needId, donationId, key, fileName, contentType, size, version)).getId();
    }

    public void review(long id, CurrentUser me) {
        require(me, Permission.DOCUMENT_REVIEW);
        Document d = get(id);
        d.review();
        events.publishEvent(new DocumentReviewed(d.getId(), d.getOwnerOrgId(), d.getDocumentTypeId(), d.getState().name()));
    }

    public void reject(long id, String reason, CurrentUser me) {
        require(me, Permission.DOCUMENT_REVIEW);
        if (reason == null || reason.isBlank()) throw new DomainException("reason-required", "Say why the document is rejected.");
        Document d = get(id);
        d.reject(reason);
        events.publishEvent(new DocumentReviewed(d.getId(), d.getOwnerOrgId(), d.getDocumentTypeId(), d.getState().name()));
    }

    /** Checks the access policy, then returns a link that works for five minutes. */
    @Transactional(readOnly = true)
    public String downloadUrl(long id, CurrentUser me) {
        Document d = get(id);
        DocumentType type = types.findById(d.getDocumentTypeId()).orElseThrow();
        boolean approvedShare = me.orgId() != null && shares.existsByOwnerOrgIdAndDonorOrgIdAndStatus(
                d.getOwnerOrgId(), me.orgId(), ShareRequest.Status.APPROVED);
        if (!DocumentAccessPolicy.canView(me.role().isStaff(), me.orgId(), d.getOwnerOrgId(), type.isShareable(), approvedShare))
            throw new AccessDeniedException("document " + id);
        return storage.signedDownloadUrl(d.getStorageKey(), d.getOriginalName(), LINK_TTL);
    }

    public long requestShare(long needId, CurrentUser me) {
        require(me, Permission.SHARE_REQUEST);
        var need = catalogue.need(needId).orElseThrow(() -> new NotFoundException("Need", needId));
        if (!matches.donorHasConfirmedMatch(me.orgId(), needId))
            throw new DomainException("not-matched", "You can request documents only for causes and projects matched to you.");
        ShareRequest r = shares.findByNeedIdAndDonorOrgId(needId, me.orgId())
                .orElseGet(() -> shares.save(new ShareRequest(needId, need.orgId(), me.orgId())));
        events.publishEvent(new ShareRequested(r.getId(), needId, me.orgId(), need.orgId()));
        return r.getId();
    }

    public void decideShare(long requestId, boolean approve, String reason, CurrentUser me) {
        require(me, Permission.SHARE_DECIDE);
        ShareRequest r = shares.findById(requestId).orElseThrow(() -> new NotFoundException("Share request", requestId));
        if (me.orgId() == null || me.orgId() != r.getOwnerOrgId()) throw new AccessDeniedException("not owner");
        r.decide(approve, reason);
        events.publishEvent(new ShareDecided(r.getId(), r.getNeedId(), r.getDonorOrgId(), approve));
    }

    @Override @Transactional(readOnly = true)
    public Set<Long> mandatoryTypeIds(String organisationType) {
        return rules.findMandatory(organisationType).stream().map(DocumentTypeRule::getDocumentTypeId).collect(Collectors.toSet());
    }

    @Override @Transactional(readOnly = true)
    public Map<Long, String> latestStateByType(long orgId) {
        Map<Long, String> m = new HashMap<>();
        for (Document d : documents.findByOwnerOrgIdAndSupersededFalseOrderByIdDesc(orgId))
            m.putIfAbsent(d.getDocumentTypeId(), d.getState().name());
        return m;
    }

    private Document get(long id) { return documents.findById(id).orElseThrow(() -> new NotFoundException("Document", id)); }

    private static void require(CurrentUser me, Permission p) {
        if (!me.can(p)) throw new AccessDeniedException(p.code());
    }
}
