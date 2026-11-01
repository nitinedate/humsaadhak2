package org.humsaadhak.reporting.controller;

import org.humsaadhak.reporting.dto.Counters;
import org.humsaadhak.reporting.service.CountersService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Numbers on the side panel: what is waiting for the signed-in person. */
@RestController
@RequestMapping("/api/v2/dashboards/me")
public class CountersController {
    private final CountersService counters;

    public CountersController(CountersService counters) { this.counters = counters; }

    @GetMapping("/counters")
    public Counters counters() { return counters.counters(CurrentUser.get()); }
}
