package org.humsaadhak.documents.controller;

import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.documents.dto.DocumentListRow;
import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import java.io.IOException;
import java.util.List;
import org.humsaadhak.documents.dto.Created;
import org.humsaadhak.documents.dto.DocRow;
import org.humsaadhak.documents.dto.Link;
import org.humsaadhak.documents.dto.Reason;
import org.humsaadhak.documents.dto.ReviewRow;
import org.humsaadhak.documents.dto.ShareRow;
import org.humsaadhak.documents.service.DocumentQueryService;
import org.humsaadhak.documents.service.DocumentService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Uploading and reviewing documents, downloads, and donors' requests to see a school's or NGO's documents. */
@RestController
@RequestMapping("/api/v2")
public class DocumentController {
    private final DocumentService documents;
    private final DocumentQueryService queries;

    public DocumentController(DocumentService documents, DocumentQueryService queries) { this.documents = documents; this.queries = queries; }

    @AuditAction(value = "document.upload", target = AuditTarget.DOCUMENT, id = "#result.id()", detail = "#file.getOriginalFilename()")
    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:document.upload')")
    public Created upload(@RequestParam long typeId, @RequestParam(required = false) Long ownerOrgId, @RequestParam(required = false) Long needId,
                          @RequestParam(required = false) Long donationId, @RequestPart("file") MultipartFile file) throws IOException {
        return new Created(documents.upload(CurrentUser.get(), ownerOrgId, typeId, needId, donationId, file.getOriginalFilename(), file.getSize(),
                file.getInputStream()));
    }

    @GetMapping("/documents")
    public List<DocRow> mine(@RequestParam(required = false) Long ownerOrgId) { return queries.documentsOf(CurrentUser.get(), ownerOrgId); }

    /** Staff review queue: newest version of every document still waiting, oldest first. */
    /** Staff with the document review permission: every organisation's documents, filtered (see DocumentQueryService). */
    @GetMapping("/documents/all")
    @PreAuthorize("hasAuthority('perm:document.review')")
    public PageResponse<DocumentListRow> all(@RequestParam(required = false) String state, @RequestParam(required = false) Long typeId,
                                             @RequestParam(required = false) String organisation,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return queries.allDocuments(state, typeId, organisation, page, size);
    }

    @GetMapping("/documents/pending")
    @PreAuthorize("hasAuthority('perm:document.review')")
    public List<ReviewRow> pending() { return queries.reviewQueue(); }

    @AuditAction(value = "document.download", target = AuditTarget.DOCUMENT, id = "#id", transactional = false)
    @GetMapping("/documents/{id}/download-url")
    public Link download(@PathVariable long id) { return new Link(documents.downloadUrl(id, CurrentUser.get())); }

    @AuditAction(value = "document.review", target = AuditTarget.DOCUMENT, id = "#id")
    @PostMapping("/documents/{id}/review")
    public void review(@PathVariable long id) { documents.review(id, CurrentUser.get()); }

    @AuditAction(value = "document.reject", target = AuditTarget.DOCUMENT, id = "#id", detail = "#body.reason()")
    @PostMapping("/documents/{id}/reject")
    public void reject(@PathVariable long id, @RequestBody Reason body) { documents.reject(id, body.reason(), CurrentUser.get()); }

    @AuditAction(value = "share.request", target = AuditTarget.SHARE_REQUEST, id = "#result.id()", detail = "'Cause or project ' + #needId")
    @PostMapping("/share-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Created requestShare(@RequestParam long needId) { return new Created(documents.requestShare(needId, CurrentUser.get())); }

    @GetMapping("/share-requests")
    public List<ShareRow> myShareRequests() { return queries.shareRequestsOf(CurrentUser.get()); }

    @AuditAction(value = "share.approve", target = AuditTarget.SHARE_REQUEST, id = "#id")
    @PostMapping("/share-requests/{id}/approve")
    public void approveShare(@PathVariable long id) { documents.decideShare(id, true, null, CurrentUser.get()); }

    @AuditAction(value = "share.reject", target = AuditTarget.SHARE_REQUEST, id = "#id", detail = "#body.reason()")
    @PostMapping("/share-requests/{id}/reject")
    public void rejectShare(@PathVariable long id, @RequestBody Reason body) { documents.decideShare(id, false, body.reason(), CurrentUser.get()); }
}
