package org.humsaadhak.documents.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.documents.dto.DocumentTypeCreateBody;
import org.humsaadhak.documents.dto.DocumentTypeRow;
import org.humsaadhak.documents.dto.DocumentTypeUpdateBody;
import org.humsaadhak.documents.service.DocumentTypeAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Super admins: document types and which organisation types must provide each. */
@RestController
@RequestMapping("/api/v2/admin/document-types")
@PreAuthorize("hasAuthority('perm:reference.manage')")
public class DocumentTypeAdminController {
    private final DocumentTypeAdminService documentTypes;

    public DocumentTypeAdminController(DocumentTypeAdminService documentTypes) { this.documentTypes = documentTypes; }

    @GetMapping
    public List<DocumentTypeRow> list() { return documentTypes.list(); }

    @AuditAction(value = "document-type.create", target = AuditTarget.DOCUMENT_TYPE, id = "#result.id()", detail = "#b.name()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentTypeRow create(@Valid @RequestBody DocumentTypeCreateBody b) { return documentTypes.create(b); }

    @AuditAction(value = "document-type.update", target = AuditTarget.DOCUMENT_TYPE, id = "#id")
    @PutMapping("/{id}")
    public DocumentTypeRow update(@PathVariable long id, @Valid @RequestBody DocumentTypeUpdateBody b) { return documentTypes.update(id, b); }
}
