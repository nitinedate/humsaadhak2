package org.humsaadhak.catalogue.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.catalogue.dto.Created;
import org.humsaadhak.catalogue.dto.NeedBody;
import org.humsaadhak.catalogue.dto.NeedInput;
import org.humsaadhak.catalogue.dto.PurposeBody;
import org.humsaadhak.catalogue.dto.PurposeInput;
import org.humsaadhak.catalogue.service.CatalogueService;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Purposes (what donors want to fund) and needs (causes of schools, projects of NGOs). */
@RestController
@RequestMapping("/api/v2")
public class CatalogueController {
    private final CatalogueService catalogue;

    public CatalogueController(CatalogueService catalogue) { this.catalogue = catalogue; }

    @AuditAction(value = "purpose.create", target = AuditTarget.PURPOSE, id = "#result.id()", detail = "#b.name()")
    @PostMapping("/purposes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:purpose.edit')")
    public Created createPurpose(@Valid @RequestBody PurposeBody b) { return new Created(catalogue.createPurpose(purposeInput(b), CurrentUser.get())); }

    @AuditAction(value = "purpose.update", target = AuditTarget.PURPOSE, id = "#id")
    @PutMapping("/purposes/{id}")
    @PreAuthorize("hasAuthority('perm:purpose.edit')")
    public void updatePurpose(@PathVariable long id, @Valid @RequestBody PurposeBody b) { catalogue.updatePurpose(id, purposeInput(b), CurrentUser.get()); }

    @AuditAction(value = "purpose.submit", target = AuditTarget.PURPOSE, id = "#id")
    @PostMapping("/purposes/{id}/submit")
    @PreAuthorize("hasAuthority('perm:purpose.edit')")
    public void submitPurpose(@PathVariable long id) { catalogue.submit(SubjectType.PURPOSE, id, CurrentUser.get()); }

    @GetMapping("/purposes")
    public List<Catalogue.PurposeView> myPurposes() { return catalogue.purposesFor(CurrentUser.get()); }

    @AuditAction(value = "need.create", target = AuditTarget.NEED, id = "#result.id()", detail = "#b.name()")
    @PostMapping("/needs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:need.edit')")
    public Created createNeed(@Valid @RequestBody NeedBody b) { return new Created(catalogue.createNeed(needInput(b), CurrentUser.get())); }

    @AuditAction(value = "need.update", target = AuditTarget.NEED, id = "#id")
    @PutMapping("/needs/{id}")
    @PreAuthorize("hasAuthority('perm:need.edit')")
    public void updateNeed(@PathVariable long id, @Valid @RequestBody NeedBody b) { catalogue.updateNeed(id, needInput(b), CurrentUser.get()); }

    @AuditAction(value = "need.submit", target = AuditTarget.NEED, id = "#id")
    @PostMapping("/needs/{id}/submit")
    @PreAuthorize("hasAuthority('perm:need.edit')")
    public void submitNeed(@PathVariable long id) { catalogue.submit(SubjectType.NEED, id, CurrentUser.get()); }

    @GetMapping("/needs/{id}")
    public Catalogue.NeedView need(@PathVariable long id) { return catalogue.needOrThrow(id); }

    /** Approved, still-open causes and projects: what donors can browse and give to directly. */
    @GetMapping("/needs/open")
    public List<Catalogue.NeedView> openNeeds(@RequestParam(required = false) String sector) { return catalogue.openNeeds(sector); }

    @GetMapping("/needs")
    public List<Catalogue.NeedView> myNeeds() { return catalogue.needsFor(CurrentUser.get()); }

    private static PurposeInput purposeInput(PurposeBody b) {
        return new PurposeInput(b.donorOrgId(), b.name(), b.description(), b.sector(), b.commitmentAmount(), b.periodicity());
    }

    private static NeedInput needInput(NeedBody b) {
        return new NeedInput(b.orgId(), b.kind(), b.name(), b.description(), b.sector(), b.targetAmount(), b.periodicity());
    }
}
