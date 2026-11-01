package org.humsaadhak.catalogue.service;

import java.util.List;
import org.humsaadhak.catalogue.dto.NeedInput;
import org.humsaadhak.catalogue.dto.PurposeInput;

import org.humsaadhak.catalogue.entity.Need;
import org.humsaadhak.catalogue.repository.NeedRepository;
import org.humsaadhak.catalogue.entity.Purpose;
import org.humsaadhak.catalogue.repository.PurposeRepository;

import java.math.BigDecimal;
import java.util.Optional;
import org.humsaadhak.catalogue.api.Catalogue;
import org.humsaadhak.catalogue.api.CatalogueEvents.NeedIndexable;
import org.humsaadhak.catalogue.api.CatalogueEvents.PurposeIndexable;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Permission;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.exception.NotFoundException;
import org.humsaadhak.workflow.api.ApprovalDecided;
import org.humsaadhak.workflow.api.Approvals;
import org.humsaadhak.workflow.api.SubjectType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CatalogueService implements Catalogue {
    private final PurposeRepository purposes;
    private final NeedRepository needs;
    private final Approvals approvals;
    private final ApplicationEventPublisher events;

    public CatalogueService(PurposeRepository purposes, NeedRepository needs, Approvals approvals, ApplicationEventPublisher events) {
        this.purposes = purposes; this.needs = needs; this.approvals = approvals; this.events = events;
    }

    public long createPurpose(PurposeInput in, CurrentUser me) {
        long org = ownerOrg(in.donorOrgId(), me);
        Purpose p = purposes.save(new Purpose(org, in.name(), in.description(), in.sector(), in.commitmentAmount(), in.periodicity()));
        p.assignRefNo();
        approvals.open(SubjectType.PURPOSE, p.getId(), me.id(), p.getName());
        return p.getId();
    }

    public void updatePurpose(long id, PurposeInput in, CurrentUser me) {
        Purpose p = purposes.findById(id).orElseThrow(() -> new NotFoundException("Purpose", id));
        checkOwner(p.getDonorOrgId(), me);
        if (p.update(in.name(), in.description(), in.sector(), in.commitmentAmount(), in.periodicity())) {
            approvals.reopenAfterEdit(SubjectType.PURPOSE, id, me.id());
            if (p.isApproved()) { p.setApproved(false); events.publishEvent(new PurposeIndexable(id, false)); }
        }
    }

    public long createNeed(NeedInput in, CurrentUser me) {
        long org = ownerOrg(in.orgId(), me);
        Need n = needs.save(new Need(org, in.kind(), in.name(), in.description(), in.sector(), in.targetAmount(), in.periodicity()));
        n.assignRefNo();
        approvals.open(SubjectType.NEED, n.getId(), me.id(), n.getName());
        return n.getId();
    }

    public void updateNeed(long id, NeedInput in, CurrentUser me) {
        Need n = needs.findById(id).orElseThrow(() -> new NotFoundException("Need", id));
        checkOwner(n.getOrgId(), me);
        if (n.update(in.name(), in.description(), in.sector(), in.targetAmount(), in.periodicity())) {
            approvals.reopenAfterEdit(SubjectType.NEED, id, me.id());
            if (n.isApproved()) { n.setApproved(false); events.publishEvent(new NeedIndexable(id, false)); }
        }
    }

    public void submit(SubjectType type, long id, CurrentUser me) { approvals.submit(type, id, me.id()); }

    @ApplicationModuleListener
    public void on(ApprovalDecided e) {
        if (e.subjectType() == SubjectType.PURPOSE) {
            purposes.findById(e.subjectId()).ifPresent(p -> {
                p.setApproved(e.approved());
                events.publishEvent(new PurposeIndexable(p.getId(), e.approved()));
            });
        } else if (e.subjectType() == SubjectType.NEED) {
            needs.findById(e.subjectId()).ifPresent(n -> {
                n.setApproved(e.approved());
                events.publishEvent(new NeedIndexable(n.getId(), e.approved() && !n.isCompleted()));
            });
        }
    }

    /** Money received moves the funding progress; a fully funded need leaves the search index. */
    @Override
    public void recordPayment(long needId, Long purposeId, BigDecimal paid) {
        Need n = needs.findById(needId).orElseThrow(() -> new NotFoundException("Need", needId));
        boolean wasOpen = !n.isCompleted();
        n.addPayment(paid);
        if (wasOpen && n.isCompleted()) events.publishEvent(new NeedIndexable(n.getId(), false));
        if (purposeId != null) purposes.findById(purposeId).ifPresent(Purpose::markMatched);
    }

    @Override
    public long createNeed(NeedDraft d, long makerId) {
        Need n = needs.save(new Need(d.orgId(), d.kind(), d.name(), d.description(), d.sector(), d.targetAmount(), d.periodicity()));
        n.assignRefNo();
        approvals.open(SubjectType.NEED, n.getId(), makerId, n.getName());
        return n.getId();
    }

    @Override
    public long createPurpose(PurposeDraft d, long makerId) {
        Purpose p = purposes.save(new Purpose(d.donorOrgId(), d.name(), d.description(), d.sector(), d.commitmentAmount(), d.periodicity()));
        p.assignRefNo();
        approvals.open(SubjectType.PURPOSE, p.getId(), makerId, p.getName());
        return p.getId();
    }

    @Override @Transactional(readOnly = true)
    public Optional<PurposeView> purpose(long id) {
        return purposes.findById(id).map(p -> new PurposeView(p.getId(), p.getRefNo(), p.getDonorOrgId(), p.getName(),
                p.getDescription(), p.getSector(), p.getCommitmentAmount(), p.getPeriodicity(), p.isApproved()));
    }

    @Override @Transactional(readOnly = true)
    public Optional<NeedView> need(long id) {
        return needs.findById(id).map(n -> new NeedView(n.getId(), n.getRefNo(), n.getOrgId(), n.getKind(), n.getName(),
                n.getDescription(), n.getSector(), n.getTargetAmount(), n.getRaisedAmount(), n.getPeriodicity(),
                n.isApproved(), n.isCompleted()));
    }

    /** Staff may act for any organisation; external users only for their own. */
    private long ownerOrg(Long requested, CurrentUser me) {
        if (me.role().isStaff()) {
            if (requested == null) throw new DomainException("org-required", "Choose the organisation this record belongs to.");
            return requested;
        }
        return me.orgId();
    }

    private void checkOwner(long orgId, CurrentUser me) {
        if (!me.role().isStaff() && (me.orgId() == null || me.orgId() != orgId))
            throw new org.springframework.security.access.AccessDeniedException("not owner");
        if (!me.can(Permission.PURPOSE_EDIT) && !me.can(Permission.NEED_EDIT))
            throw new org.springframework.security.access.AccessDeniedException("no edit permission");
    }

    /** Staff see every purpose; a donor sees their own, newest first. */
    @Transactional(readOnly = true)
    public List<PurposeView> purposesFor(CurrentUser me) {
        var list = me.role().isStaff() ? purposes.findAll() : purposes.findByDonorOrgIdOrderByIdDesc(me.orgId());
        return list.stream().map(p -> purpose(p.getId()).orElseThrow()).toList();
    }

    /** Staff see every cause and project; a school or NGO sees its own, newest first. */
    @Transactional(readOnly = true)
    public List<NeedView> needsFor(CurrentUser me) {
        var list = me.role().isStaff() ? needs.findAll() : needs.findByOrgIdOrderByIdDesc(me.orgId());
        return list.stream().map(n -> need(n.getId()).orElseThrow()).toList();
    }

    /** Approved, still-open causes and projects (optionally of one sector): what donors can browse and give to directly. */
    @Transactional(readOnly = true)
    public List<NeedView> openNeeds(String sector) {
        var list = sector == null || sector.isBlank() ? needs.findByApprovedTrueAndCompletedFalseOrderByIdDesc()
                : needs.findByApprovedTrueAndCompletedFalseAndSectorOrderByIdDesc(sector);
        return list.stream().map(n -> need(n.getId()).orElseThrow()).toList();
    }

    @Transactional(readOnly = true)
    public NeedView needOrThrow(long id) { return need(id).orElseThrow(() -> new NotFoundException("Need", id)); }
}
