package org.humsaadhak.audit.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.humsaadhak.audit.dto.AuditEntry;
import org.humsaadhak.audit.dto.AuditFilter;
import org.humsaadhak.audit.service.AuditTrailService;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditAction;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v2/audit/trail: one record's audit trail (anyone who can see the record);
 * GET /api/v2/audit/activity: recent actions in a page's area; GET /api/v2/audit/events(.csv): the full log (audit.view).
 */
@RestController
@RequestMapping("/api/v2/audit")
public class AuditController {
    private final AuditTrailService audit;

    public AuditController(AuditTrailService audit) { this.audit = audit; }

    static int size(int size) { return Math.max(1, Math.min(size, 100)); }

    @GetMapping("/trail")
    public PageResponse<AuditEntry> trail(@RequestParam AuditTarget type, @RequestParam String id, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return audit.trail(type, id, Math.max(0, page), size(size), CurrentUser.get());
    }

    @GetMapping("/activity")
    public PageResponse<AuditEntry> activity(@RequestParam List<String> category, @RequestParam(defaultValue = "false") boolean mine,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return audit.activity(category, mine, Math.max(0, page), size(size), CurrentUser.get());
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('perm:audit.view')")
    public PageResponse<AuditEntry> events(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) String actor, @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String outcome, @RequestParam(required = false) String targetType,
                                           @RequestParam(required = false) String targetId, @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return audit.search(new AuditFilter(from, to, actor, category, outcome, targetType, targetId), Math.max(0, page), size(size));
    }

    @GetMapping("/events.csv")
    @PreAuthorize("hasAuthority('perm:audit.view')")
    @AuditAction(value = "audit.export", detail = "(#from == null ? '' : 'from ' + #from + ' ') + (#to == null ? '' : 'to ' + #to)", transactional = false)
    public ResponseEntity<byte[]> csv(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(required = false) String actor, @RequestParam(required = false) String category,
                                      @RequestParam(required = false) String outcome, @RequestParam(required = false) String targetType,
                                      @RequestParam(required = false) String targetId) {
        byte[] body = audit.csv(new AuditFilter(from, to, actor, category, outcome, targetType, targetId)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"").body(body);
    }
}
