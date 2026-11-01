package org.humsaadhak.reporting.service;

import org.humsaadhak.reporting.dao.CountersDao;
import org.humsaadhak.reporting.dto.Counters;
import org.humsaadhak.shared.security.CurrentUser;
import org.humsaadhak.shared.security.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What is waiting for the signed-in person; zero where they have no such work. */
@Service
@Transactional(readOnly = true)
public class CountersService {
    private final CountersDao counters;

    public CountersService(CountersDao counters) { this.counters = counters; }

    public Counters counters(CurrentUser me) {
        long approvals = me.can(Permission.RECORD_APPROVE) ? counters.approvalsWaitingFor(me.id()) : 0;
        long documents = me.can(Permission.DOCUMENT_REVIEW) ? counters.documentsWaitingForReview() : 0;
        long shares = me.can(Permission.SHARE_DECIDE) && me.orgId() != null ? counters.shareRequestsWaitingFor(me.orgId()) : 0;
        return new Counters(approvals, documents, shares);
    }
}
