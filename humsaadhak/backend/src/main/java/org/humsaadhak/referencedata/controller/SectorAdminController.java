package org.humsaadhak.referencedata.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.referencedata.dto.SectorCreateBody;
import org.humsaadhak.referencedata.dto.SectorRow;
import org.humsaadhak.referencedata.dto.SectorUpdateBody;
import org.humsaadhak.referencedata.service.SectorService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Super admins: sectors (add, rename while unused, switch off). */
@RestController
@RequestMapping("/api/v2/admin/sectors")
@PreAuthorize("hasAuthority('perm:reference.manage')")
public class SectorAdminController {
    private final SectorService sectors;

    public SectorAdminController(SectorService sectors) { this.sectors = sectors; }

    @GetMapping
    public List<SectorRow> list() { return sectors.list(); }

    @AuditAction(value = "sector.create", target = AuditTarget.SECTOR, id = "#result.code()", detail = "#b.name()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SectorRow create(@Valid @RequestBody SectorCreateBody b) { return sectors.create(b); }

    @AuditAction(value = "sector.update", target = AuditTarget.SECTOR, id = "#code")
    @PutMapping("/{code}")
    public SectorRow update(@PathVariable String code, @Valid @RequestBody SectorUpdateBody b) { return sectors.update(code, b); }
}
