package org.humsaadhak.reporting.controller;

import org.humsaadhak.shared.security.AuditAction;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.humsaadhak.reporting.dto.CsvFile;
import org.humsaadhak.reporting.service.CsvExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** CSV downloads for staff. */
@RestController
@RequestMapping("/api/v2/reports")
@PreAuthorize("hasAuthority('perm:dashboard.platform')")
public class ReportController {
    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);
    private final CsvExportService exports;

    public ReportController(CsvExportService exports) { this.exports = exports; }

    @AuditAction(value = "report.export-csv", detail = "'Donations'", transactional = false)
    @GetMapping("/donations.csv")
    public ResponseEntity<String> donations(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return download(exports.donations(from, to, LocalDate.now()));
    }

    @AuditAction(value = "report.export-csv", detail = "'Organisations'", transactional = false)
    @GetMapping("/organisations.csv")
    public ResponseEntity<String> organisations() { return download(exports.organisations(LocalDate.now())); }

    @AuditAction(value = "report.export-csv", detail = "'Causes and projects'", transactional = false)
    @GetMapping("/needs.csv")
    public ResponseEntity<String> needs() { return download(exports.needs(LocalDate.now())); }

    private static ResponseEntity<String> download(CsvFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(CSV).body(file.content());
    }
}
