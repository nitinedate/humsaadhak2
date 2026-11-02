package org.humsaadhak.documents.service;

import org.springframework.security.access.AccessDeniedException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.documents.entity.ShareRequest;
import org.humsaadhak.documents.entity.DocumentType;
import org.humsaadhak.documents.dto.SharedDocumentRow;
import org.humsaadhak.shared.exception.DomainException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.documents.dto.DocumentListRow;
import java.util.Set;
import java.util.ArrayList;
import jakarta.persistence.criteria.Predicate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.documents.api.OrganisationDirectory;
import org.humsaadhak.documents.dto.DocRow;
import org.humsaadhak.documents.dto.ReviewRow;
import org.humsaadhak.documents.dto.ShareRow;
import org.humsaadhak.documents.entity.Document;
import org.humsaadhak.documents.repository.DocumentRepository;
import org.humsaadhak.documents.repository.DocumentTypeRepository;
import org.humsaadhak.documents.repository.ShareRequestRepository;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Document lists for the screens, with organisation, type and cause/project names filled in. */
@Service
@Transactional(readOnly = true)
public class DocumentQueryService {
    private final DocumentRepository documents;
    private final ShareRequestRepository shares;
    private final DocumentTypeRepository types;
    private final OrganisationDirectory directory;
    private final Catalogue catalogue;

    public DocumentQueryService(DocumentRepository documents, ShareRequestRepository shares, DocumentTypeRepository types,
                                OrganisationDirectory directory, Catalogue catalogue) {
        this.documents = documents; this.shares = shares; this.types = types; this.directory = directory; this.catalogue = catalogue;
    }

    /** Current versions of an organisation's documents: the person's own organisation, or any for staff. */
    public List<DocRow> documentsOf(CurrentUser me, Long ownerOrgId) {
        Long chosen = me.role().isStaff() && ownerOrgId != null ? ownerOrgId : me.orgId();
        if (chosen == null) return List.of();          // staff without an organisation, nothing chosen: no list (was an error)
        long org = chosen;
        return documents.findByOwnerOrgIdAndSupersededFalseOrderByIdDesc(org).stream()
                .map(d -> new DocRow(d.getId(), d.getDocumentTypeId(), d.getOriginalName(), d.getDocVersion(), d.getState().name(), d.getRejectionReason()))
                .toList();
    }

    /**
     * Staff: the current version of every organisation's documents, newest first, filtered by state (waiting, approved,
     * rejected), document type and part of the organisation's name.
     */
    public PageResponse<DocumentListRow> allDocuments(String stateName, Long typeId, String organisation, int page, int size) {
        Document.State state = null;
        if (stateName != null && !stateName.isBlank()) {
            try { state = Document.State.valueOf(stateName.strip().toUpperCase(java.util.Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new DomainException("invalid-state", "Choose PENDING, REVIEWED or REJECTED."); }
        }
        Document.State chosenState = state;
        int pageSize = Math.max(1, Math.min(size, 100));
        PageRequest request = PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Set<Long> orgIds = null;
        if (organisation != null && !organisation.isBlank()) {
            orgIds = directory.idsNamed(organisation);
            if (orgIds.isEmpty()) return new PageResponse<>(List.of(), request.getPageNumber(), pageSize, 0);
        }
        Set<Long> owners = orgIds;
        Specification<Document> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.isFalse(root.get("superseded")));
            if (chosenState != null) where.add(cb.equal(root.get("state"), chosenState));
            if (typeId != null) where.add(cb.equal(root.get("documentTypeId"), typeId));
            if (owners != null) where.add(root.get("ownerOrgId").in(owners));
            return cb.and(where.toArray(Predicate[]::new));
        };
        Page<Document> found = documents.findAll(filter, request);
        Map<Long, String> orgNames = directory.namesOf(found.getContent().stream().map(Document::getOwnerOrgId).toList());
        Map<Long, String> typeNames = new HashMap<>();
        types.findAll().forEach(t -> typeNames.put(t.getId(), t.getName()));
        return PageResponse.of(found, d -> {
            boolean decided = d.getState() != Document.State.PENDING;
            return new DocumentListRow(d.getId(), d.getOwnerOrgId(), orgNames.getOrDefault(d.getOwnerOrgId(), "Organisation " + d.getOwnerOrgId()),
                    d.getDocumentTypeId(), typeNames.getOrDefault(d.getDocumentTypeId(), "Document"), d.getOriginalName(), d.getDocVersion(),
                    d.getState().name(), d.getRejectionReason(), d.getCreatedBy(), d.getCreatedAt(),
                    decided ? d.getUpdatedBy() : null, decided ? d.getUpdatedAt() : null);
        });
    }

    /** Staff review queue: newest version of every document still waiting, oldest first. */
    public List<ReviewRow> reviewQueue() {
        var pending = documents.findByStateAndSupersededFalseOrderByIdAsc(Document.State.PENDING);
        Map<Long, String> orgNames = directory.namesOf(pending.stream().map(Document::getOwnerOrgId).toList());
        Map<Long, String> typeNames = new HashMap<>();
        types.findAll().forEach(t -> typeNames.put(t.getId(), t.getName()));
        return pending.stream().map(d -> new ReviewRow(d.getId(), d.getOwnerOrgId(), orgNames.getOrDefault(d.getOwnerOrgId(), "Organisation " + d.getOwnerOrgId()),
                d.getDocumentTypeId(), typeNames.getOrDefault(d.getDocumentTypeId(), "Document"), d.getOriginalName(), d.getDocVersion(), d.getCreatedBy())).toList();
    }

    /**
     * The documents a school or NGO shares with a donor through an approved request: the current version of each
     * shareable document, once HumSaadhak has approved it (exactly what DocumentAccessPolicy lets the donor download).
     * For the donor who asked, the owner, or staff; empty while the request is not approved.
     */
    public List<SharedDocumentRow> sharedDocuments(long shareRequestId, CurrentUser me) {
        ShareRequest s = shares.findById(shareRequestId).orElseThrow(() -> new NotFoundException("Document request", shareRequestId));
        boolean allowed = me.role().isStaff() || (me.orgId() != null && (me.orgId() == s.getDonorOrgId() || me.orgId() == s.getOwnerOrgId()));
        if (!allowed) throw new AccessDeniedException("document request " + shareRequestId);
        if (s.getStatus() != ShareRequest.Status.APPROVED) return List.of();
        Map<Long, DocumentType> shareable = new HashMap<>();
        types.findAll().forEach(t -> { if (t.isShareable()) shareable.put(t.getId(), t); });
        return documents.findByOwnerOrgIdAndSupersededFalseOrderByIdDesc(s.getOwnerOrgId()).stream()
                .filter(d -> d.getState() == Document.State.REVIEWED && shareable.containsKey(d.getDocumentTypeId()))
                .map(d -> new SharedDocumentRow(d.getId(), shareable.get(d.getDocumentTypeId()).getName(), d.getOriginalName(),
                        d.getDocVersion(), d.getUpdatedAt()))
                .sorted(java.util.Comparator.comparing(SharedDocumentRow::typeName))
                .toList();
    }

    /** Requests a donor made, or requests a school or NGO received. */
    public List<ShareRow> shareRequestsOf(CurrentUser me) {
        var list = me.role().isDonor() ? shares.findByDonorOrgIdOrderByIdDesc(me.orgId()) : shares.findByOwnerOrgIdOrderByIdDesc(me.orgId());
        Map<Long, String> orgNames = directory.namesOf(list.stream().flatMap(s -> Stream.of(s.getDonorOrgId(), s.getOwnerOrgId())).toList());
        return list.stream().map(s -> new ShareRow(s.getId(), s.getNeedId(),
                catalogue.need(s.getNeedId()).map(Catalogue.NeedView::name).orElse("Need " + s.getNeedId()),
                s.getDonorOrgId(), orgNames.getOrDefault(s.getDonorOrgId(), "Donor " + s.getDonorOrgId()),
                s.getOwnerOrgId(), orgNames.getOrDefault(s.getOwnerOrgId(), "Organisation " + s.getOwnerOrgId()), s.getStatus().name())).toList();
    }
}
