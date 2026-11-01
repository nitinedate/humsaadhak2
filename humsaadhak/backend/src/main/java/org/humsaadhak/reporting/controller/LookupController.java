package org.humsaadhak.reporting.controller;

import java.util.List;
import org.humsaadhak.reporting.dto.OpenNeed;
import org.humsaadhak.reporting.dto.OrganisationOption;
import org.humsaadhak.reporting.service.LookupService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

/** Small read models for pickers in forms, with the names the screens show. */
@RestController
@RequestMapping("/api/v2/lookups")
public class LookupController {
    private final LookupService lookups;

    public LookupController(LookupService lookups) { this.lookups = lookups; }

    /** Approved causes and projects that still need money; anyone who can record donations may look them up. */
    @GetMapping("/open-needs")
    public List<OpenNeed> openNeeds(@RequestParam(required = false) String q) { return lookups.openNeeds(CurrentUser.get(), q); }

    /** group = DONORS (individual + corporate) or BENEFICIARIES (schools + NGOs); staff only. */
    @GetMapping("/organisations")
    public List<OrganisationOption> organisations(@RequestParam String group, @RequestParam(required = false) String q) {
        return lookups.organisations(CurrentUser.get(), group, q);
    }
}
