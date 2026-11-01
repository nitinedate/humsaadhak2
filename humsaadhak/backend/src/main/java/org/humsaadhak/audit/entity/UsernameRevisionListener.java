package org.humsaadhak.audit.entity;

import org.hibernate.envers.RevisionListener;
import org.humsaadhak.shared.security.AuditContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Fills AuditRevision.username from the signed-in user (or "system" for scheduled jobs) and the audited request's id. */
public class UsernameRevisionListener implements RevisionListener {
    @Override
    public void newRevision(Object revisionEntity) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String name = a != null && a.isAuthenticated() && !"anonymousUser".equals(a.getName()) ? a.getName() : "system";
        AuditRevision revision = (AuditRevision) revisionEntity;
        revision.setUsername(name);
        revision.setRequestId(AuditContext.requestIdOrNull());
    }
}
