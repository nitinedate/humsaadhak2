package org.humsaadhak.audit.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.humsaadhak.audit.dao.AuditDao;
import org.humsaadhak.audit.dto.AuditEntry;
import org.humsaadhak.audit.dto.AuditFilter;
import org.humsaadhak.audit.dto.HistoryEntry;
import org.humsaadhak.audit.model.AuditedType;
import org.humsaadhak.audit.util.AuditTrailMerge;
import org.humsaadhak.audit.util.AuditVisibility;
import org.humsaadhak.shared.dto.PageResponse;
import org.humsaadhak.shared.exception.DomainException;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Permission;
import org.humsaadhak.shared.util.Csv;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Audit trails for the pages where changes are made, and the full audit log. */
@Service
@Transactional(readOnly = true)
public class AuditTrailService {
    /** Categories a page may ask for (the part of an action code before the dot). */
    public static final Set<String> CATEGORIES = Set.of("auth", "preferences", "user", "role", "permission", "organisation", "purpose", "need",
            "approval", "document", "share", "match", "donation", "import", "sector", "document-type", "report", "audit");
    static final int CSV_LIMIT = 50_000;
    private final AuditDao dao;
    private final AuditRecorder recorder;
    private final HistoryService history;

    public AuditTrailService(AuditDao dao, AuditRecorder recorder, HistoryService history) {
        this.dao = dao; this.recorder = recorder; this.history = history;
    }

    /** Everything done to one record, newest first, each action with the fields it changed. */
    public PageResponse<AuditEntry> trail(AuditTarget type, String id, int page, int size, CurrentUser me) {
        if (type == AuditTarget.NONE || id == null || id.isBlank()) throw new DomainException("bad-target", "Choose a record.");
        if (!AuditVisibility.canSeeTrail(me.role().isStaff(), me.orgId(), recorder.owners(type, id)))
            throw new AccessDeniedException("audit trail of " + type + " " + id);
        boolean all = AuditVisibility.seesEverything(me.can(Permission.AUDIT_VIEW));
        List<AuditEntry> merged = AuditTrailMerge.merge(dao.forTarget(type.name(), id, all), changesOf(type, id), type.name(), id);
        if (!all) merged = merged.stream().map(AuditTrailMerge::redact).toList();
        return new PageResponse<>(AuditTrailMerge.page(merged, page, size), page, size, merged.size());
    }

    /** What happened in some areas: everything for staff, otherwise what concerns the person's organisation (or only their own actions). */
    public PageResponse<AuditEntry> activity(List<String> categories, boolean mine, int page, int size, CurrentUser me) {
        List<String> cats = categories.stream().filter(CATEGORIES::contains).distinct().toList();
        if (cats.isEmpty()) throw new DomainException("bad-category", "Choose what kind of activity to show.");
        boolean all = AuditVisibility.seesEverything(me.can(Permission.AUDIT_VIEW));
        Long actor = mine || (!me.role().isStaff() && me.orgId() == null) ? me.id() : null;
        Long org = me.role().isStaff() || actor != null ? null : me.orgId();
        List<AuditEntry> items = dao.activity(cats, org, actor, all, size, page * size);
        if (!all) items = items.stream().map(AuditTrailMerge::redact).toList();
        return new PageResponse<>(items, page, size, dao.countActivity(cats, org, actor, all));
    }

    /** The full audit log (audit.view). */
    public PageResponse<AuditEntry> search(AuditFilter f, int page, int size) {
        return new PageResponse<>(dao.search(f, size, page * size), page, size, dao.countSearch(f));
    }

    public String csv(AuditFilter f) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("When (UTC)", "Who", "Email", "Role", "Action", "Record type", "Record", "Outcome", "Error", "Details", "IP address",
                "Request id"));
        for (AuditEntry e : dao.search(f, CSV_LIMIT, 0))
            rows.add(java.util.Arrays.asList(e.at().toString(), e.actorName(), e.actorEmail(), e.actorRole(), e.label(), e.targetType(),
                    e.targetId(), e.outcome(), e.errorCode(), e.detail(), e.ip(), e.requestId()));
        return Csv.of(rows);
    }

    private List<HistoryEntry> changesOf(AuditTarget type, String id) {
        if (AuditedType.parse(type.name()).isEmpty()) return List.of();
        try { return history.history(type.name(), Long.parseLong(id)); }
        catch (NumberFormatException e) { return List.of(); }
    }
}
