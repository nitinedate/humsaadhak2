package org.humsaadhak.giving.service;

import org.humsaadhak.giving.dto.DonationRow;
import java.util.List;
import org.humsaadhak.giving.dto.DonationCommand;
import org.humsaadhak.giving.dto.DonationResult;

import org.humsaadhak.giving.entity.Donation;
import org.humsaadhak.giving.util.DonationCalculator;
import org.humsaadhak.giving.repository.DonationRepository;
import org.humsaadhak.giving.entity.Instalment;
import org.humsaadhak.giving.repository.InstalmentRepository;
import org.humsaadhak.giving.entity.RecurringPlan;
import org.humsaadhak.giving.repository.RecurringPlanRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.giving.api.DonationRecorded;
import org.humsaadhak.matching.api.Matches;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.shared.util.RefNo;
import org.humsaadhak.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonationService {
    private final DonationRepository donations;
    private final RecurringPlanRepository plans;
    private final InstalmentRepository instalments;
    private final Catalogue catalogue;
    private final Matches matches;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public DonationService(DonationRepository donations, RecurringPlanRepository plans, InstalmentRepository instalments,
                    Catalogue catalogue, Matches matches, ApplicationEventPublisher events, Clock clock) {
        this.donations = donations; this.plans = plans; this.instalments = instalments;
        this.catalogue = catalogue; this.matches = matches; this.events = events; this.clock = clock;
    }

    /** Records a donation exactly once per idempotency key (a double click cannot pay twice). */
    @Transactional
    public DonationResult record(DonationCommand c, String idempotencyKey, CurrentUser me) {
        if (idempotencyKey == null || idempotencyKey.isBlank())
            throw new DomainException("idempotency-key-required", "Missing Idempotency-Key header.");
        var existing = donations.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Donation d = existing.get();
            return new DonationResult(d.getId(), d.getRefNo(), d.getPaidAmount(), null, null);
        }
        if (me.role().isStaff() && c.donorOrgId() == null)
            throw new org.humsaadhak.shared.exception.DomainException("donor-required", "Choose the donor who made this payment.");
        long donorOrg = me.role().isStaff() ? c.donorOrgId() : me.orgId();
        if (!me.role().isStaff() && !me.role().isDonor()) throw new AccessDeniedException("donors only");
        if (c.paymentRef() == null || c.paymentRef().isBlank())
            throw new DomainException("payment-ref-required", "Enter the UTR or cheque number.");

        var need = catalogue.need(c.needId()).orElseThrow(() -> new NotFoundException("Need", c.needId()));
        if (!need.approved() || need.completed())
            throw new DomainException("need-closed", "This cause or project is not open for donations.");

        Long purposeId = null;
        BigDecimal pledged = null;
        BigDecimal givenThisCycle = BigDecimal.ZERO;
        LocalDate today = LocalDate.now(clock);
        if (c.matchId() != null) {
            var m = matches.match(c.matchId()).orElseThrow(() -> new NotFoundException("Match", c.matchId()));
            if (m.donorOrgId() != donorOrg || m.needId() != c.needId()) throw new AccessDeniedException("match does not belong to donor");
            purposeId = m.purposeId();
            var purpose = catalogue.purpose(purposeId).orElseThrow();
            pledged = purpose.commitmentAmount();
            givenThisCycle = donations.sumForPurposeSince(purposeId, DonationCalculator.cycleStart(today, purpose.periodicity()));
        }

        var outcome = DonationCalculator.compute(c.paidAmount(), need.remaining(), pledged, givenThisCycle, c.periodicity(), today);

        Donation d = new Donation(donorOrg, c.needId(), purposeId, c.matchId(), pledged, outcome.paid(), c.paymentRef().trim(),
                today, idempotencyKey);
        donations.save(d);
        d.setRefNo(RefNo.of(c.matchId() == null ? RefNo.DIRECT_DONATION : RefNo.DONATION, d.getId()));

        catalogue.recordPayment(c.needId(), purposeId, outcome.paid());
        LocalDate nextDue = scheduleNext(donorOrg, c, purposeId, outcome, d.getId());
        events.publishEvent(new DonationRecorded(d.getId(), d.getRefNo(), c.needId(), purposeId, donorOrg, outcome.paid()));
        return new DonationResult(d.getId(), d.getRefNo(), outcome.paid(), outcome.commitmentLeft(), nextDue);
    }

    private LocalDate scheduleNext(long donorOrg, DonationCommand c, Long purposeId, DonationCalculator.Outcome o, long donationId) {
        if (o.nextDue().isEmpty()) return null;
        RecurringPlan plan = plans.findByDonorOrgIdAndNeedId(donorOrg, c.needId())
                .orElseGet(() -> plans.save(new RecurringPlan(donorOrg, c.needId(), purposeId, c.periodicity(), o.paid())));
        plan.setAmount(o.paid());
        plan.setPeriodicity(c.periodicity());
        instalments.findFirstByPlanIdAndStatusOrderByDueDateAsc(plan.getId(), Instalment.Status.DUE).ifPresent(i -> {
            i.setStatus(Instalment.Status.PAID);
            i.setDonationId(donationId);
        });
        instalments.save(new Instalment(plan.getId(), o.nextDue().get(), o.paid()));
        return o.nextDue().get();
    }

    /** The signed-in donor's own donations, newest first. */
    @Transactional(readOnly = true)
    public List<DonationRow> donationsOf(CurrentUser me) {
        return donations.findByDonorOrgIdOrderByIdDesc(me.orgId()).stream()
                .map(d -> new DonationRow(d.getId(), d.getRefNo(), d.getNeedId(), d.getMatchId(), d.getPaidAmount(), d.getPledgedAmount(), d.getPaidOn(), d.isConfirmed()))
                .toList();
    }
}
