package org.humsaadhak.giving.controller;

import org.humsaadhak.shared.model.AuditTarget;

import org.humsaadhak.shared.security.AuditAction;

import jakarta.validation.Valid;
import java.util.List;
import org.humsaadhak.giving.dto.DonationBody;
import org.humsaadhak.giving.dto.DonationCommand;
import org.humsaadhak.giving.dto.DonationResult;
import org.humsaadhak.giving.dto.DonationRow;
import org.humsaadhak.giving.service.DonationService;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Recording donations (with an Idempotency-Key so a retried request never records twice) and a donor's own list. */
@RestController
@RequestMapping("/api/v2/donations")
public class GivingController {
    private final DonationService donations;

    public GivingController(DonationService donations) { this.donations = donations; }

    @AuditAction(value = "donation.record", target = AuditTarget.DONATION, id = "#result.donationId()", detail = "'Amount ' + #b.paidAmount()")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('perm:donation.record')")
    public DonationResult record(@RequestHeader("Idempotency-Key") String key, @Valid @RequestBody DonationBody b) {
        return donations.record(new DonationCommand(b.donorOrgId(), b.needId(), b.matchId(), b.paidAmount(), b.periodicity(), b.paymentRef()),
                key, CurrentUser.get());
    }

    @GetMapping
    public List<DonationRow> mine() { return donations.donationsOf(CurrentUser.get()); }
}
