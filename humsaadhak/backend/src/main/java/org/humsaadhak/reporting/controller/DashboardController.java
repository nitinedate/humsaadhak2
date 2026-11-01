package org.humsaadhak.reporting.controller;

import java.time.LocalDate;
import org.humsaadhak.reporting.dto.Dashboard;
import org.humsaadhak.reporting.service.DashboardService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One endpoint: the server decides which dashboard the caller may see. */
@RestController
@RequestMapping("/api/v2/dashboards")
public class DashboardController {
    private final DashboardService dashboards;

    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }

    @GetMapping("/me")
    public Dashboard mine() { return dashboards.forUser(CurrentUser.get(), LocalDate.now()); }
}
