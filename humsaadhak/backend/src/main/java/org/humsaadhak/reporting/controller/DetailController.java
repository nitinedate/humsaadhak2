package org.humsaadhak.reporting.controller;

import java.util.HashMap;
import java.util.Map;
import org.humsaadhak.reporting.dto.DetailTable;
import org.humsaadhak.reporting.service.DetailService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v2/dashboards/details?set=DONATIONS&donorType=DONOR_CORPORATE&needKind=CAUSE&from=2026-04-01
 * The records behind a chart segment (each slice and bar sends its own Drill). Scope is applied server-side.
 */
@RestController
@RequestMapping("/api/v2/dashboards")
public class DetailController {
    private final DetailService details;

    public DetailController(DetailService details) { this.details = details; }

    @GetMapping("/details")
    public DetailTable details(@RequestParam String set, @RequestParam Map<String, String> all) {
        Map<String, String> filters = new HashMap<>(all);
        filters.remove("set");
        return details.load(set, filters, CurrentUser.get());
    }
}
