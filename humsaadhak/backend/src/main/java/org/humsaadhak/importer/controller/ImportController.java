package org.humsaadhak.importer.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import java.io.IOException;
import org.humsaadhak.importer.dto.ImportJobView;
import org.humsaadhak.importer.model.ImportType;
import org.humsaadhak.importer.service.ImportService;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Bulk Excel import of purposes, causes and projects (all-or-nothing), recent jobs and templates. */
@RestController
@RequestMapping("/api/v2/imports")
@PreAuthorize("hasAuthority('perm:import.run')")
public class ImportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final ImportService imports;

    public ImportController(ImportService imports) { this.imports = imports; }

    @AuditAction(value = "import.upload", target = AuditTarget.IMPORT_JOB, id = "#result.id()", detail = "#file.getOriginalFilename() + ' (' + #type + ')'")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportJobView upload(@RequestParam ImportType type, @RequestPart("file") MultipartFile file) throws IOException {
        return imports.upload(type, file.getOriginalFilename(), file.getInputStream(), CurrentUser.get());
    }

    @GetMapping
    public PageResponse<ImportJobView> recent(@RequestParam(defaultValue = "0") int page) { return imports.recent(page); }

    @GetMapping("/{id}")
    public ImportJobView get(@PathVariable long id) { return imports.job(id); }

    @AuditAction(value = "import.template", detail = "#type", transactional = false)
    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> template(@PathVariable ImportType type) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"humsaadhak-" + type.name().toLowerCase() + "-template.xlsx\"")
                .contentType(XLSX).body(imports.template(type));
    }
}
