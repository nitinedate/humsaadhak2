package org.humsaadhak.audit.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.humsaadhak.audit.dao.AuditDao;
import org.humsaadhak.audit.dto.Actor;
import org.humsaadhak.audit.dto.NewAuditEvent;
import org.humsaadhak.audit.model.Outcome;
import org.humsaadhak.audit.util.AuditActions;
import org.humsaadhak.shared.model.AuditTarget;
import org.humsaadhak.shared.security.AuditContext;
import org.humsaadhak.shared.security.AuditOwnership;
import org.humsaadhak.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Writes audit records. Joins the caller's transaction when there is one, so an action and its record commit together. */
@Service
public class AuditRecorder {
    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);
    private final AuditDao dao;
    private final List<AuditOwnership> owners;

    public AuditRecorder(AuditDao dao, List<AuditOwnership> owners) { this.dao = dao; this.owners = owners; }

    /** The signed-in person, or null for anonymous requests (sign-in, registration, password links). */
    public static Actor currentActor() {
        try {
            CurrentUser u = CurrentUser.get();
            return new Actor(u.id(), u.email(), u.fullName(), u.roleName(), u.orgId());
        } catch (IllegalStateException notSignedIn) {
            return null;
        }
    }

    public void record(AuditContext ctx, String action, AuditTarget type, String targetId, Actor actor, Outcome outcome, String errorCode,
                       String detail) {
        AuditTarget t = type == null ? AuditTarget.NONE : type;
        String id = targetId;
        if ((id == null || id.isBlank()) && t == AuditTarget.USER && actor != null) id = String.valueOf(actor.id());
        Set<Long> orgs = id == null || t == AuditTarget.NONE ? Set.of() : owners(t, id);
        dao.insert(new NewAuditEvent(ctx.requestId(), actor == null ? null : actor.id(), actor == null ? null : actor.email(),
                actor == null ? null : actor.name(), actor == null ? null : actor.role(), actor == null ? null : actor.orgId(),
                action, AuditActions.category(action), t == AuditTarget.NONE ? null : t.name(), id, orgs, outcome.name(),
                AuditActions.clip(errorCode, 80), AuditActions.clip(detail, 1000), AuditActions.clip(ctx.ip(), 64),
                AuditActions.clip(ctx.userAgent(), 400)));
    }

    /** Organisations that may see the record's trail; a failing lookup never blocks the action. */
    public Set<Long> owners(AuditTarget type, String id) {
        Set<Long> out = new HashSet<>();
        for (AuditOwnership o : owners) {
            if (!o.supports().contains(type)) continue;
            try { out.addAll(o.organisations(type, id)); }
            catch (RuntimeException e) { log.warn("Cannot resolve owners of {} {}: {}", type, id, e.toString()); }
        }
        return out;
    }
}
