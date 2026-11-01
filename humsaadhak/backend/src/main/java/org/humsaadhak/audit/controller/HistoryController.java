package org.humsaadhak.audit.controller;

import java.util.List;
import org.humsaadhak.audit.dto.HistoryEntry;
import org.humsaadhak.audit.service.HistoryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** GET /api/v2/history/{type}/{id}: readable change history of one record, newest first (staff only). */
@RestController
@RequestMapping("/api/v2/history")
public class HistoryController {
    private final HistoryService history;

    public HistoryController(HistoryService history) { this.history = history; }

    @GetMapping("/{type}/{id}")
    @PreAuthorize("hasAnyAuthority('perm:record.approve','perm:dashboard.platform','perm:admin.users')")
    public List<HistoryEntry> history(@PathVariable String type, @PathVariable long id) { return history.history(type, id); }
}
