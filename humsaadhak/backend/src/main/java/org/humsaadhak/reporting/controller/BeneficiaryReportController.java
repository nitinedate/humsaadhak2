package org.humsaadhak.reporting.controller;

import org.humsaadhak.shared.security.AuditAction;

import org.humsaadhak.reporting.dto.ReportOptions;
import org.humsaadhak.reporting.dto.ReportRequest;
import org.humsaadhak.reporting.dto.ReportResult;

import org.humsaadhak.reporting.service.BeneficiaryReportService;

import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * The original project's reports (POST /api/v1/report/donation_report, match_report, project_report, cause_report,
 * custom_project_report, custom_cause_report and report_page_filter_details), as:
 *   GET  /api/v2/beneficiary-reports/options?orgId=        filter choices (causes/projects, financial years, custom columns)
 *   POST /api/v2/beneficiary-reports/{type}                type = donations | matches | needs | custom
 * Excel and PDF files are built in the browser from these results, with the original layout.
 */
@RestController
@RequestMapping("/api/v2/beneficiary-reports")
public class BeneficiaryReportController {
    private final BeneficiaryReportService reports;
    BeneficiaryReportController(BeneficiaryReportService reports) { this.reports = reports; }

    @GetMapping("/options")
    ReportOptions options(@RequestParam(required = false) Long orgId) {
        try { return reports.options(CurrentUser.get(), orgId); }
        catch (BeneficiaryReportService.NotAllowed e) { throw new AccessDeniedException(e.getMessage()); }
    }

    @AuditAction(value = "report.run", detail = "#type", transactional = false)
    @PostMapping("/{type}")
    ReportResult run(@PathVariable String type, @RequestBody ReportRequest request) {
        try { return reports.run(CurrentUser.get(), type, request); }
        catch (BeneficiaryReportService.NotAllowed e) { throw new AccessDeniedException(e.getMessage()); }
    }
}
