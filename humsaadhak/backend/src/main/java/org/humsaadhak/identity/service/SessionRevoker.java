package org.humsaadhak.identity.service;

import java.util.Collection;
import org.humsaadhak.identity.dao.SessionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Signs people out everywhere, so a change to their role or its permissions applies immediately. */
@Service
public class SessionRevoker {
    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);
    private final SessionDao sessions;

    public SessionRevoker(SessionDao sessions) { this.sessions = sessions; }

    public int revoke(Collection<String> emails) {
        if (emails.isEmpty()) return 0;
        int n = sessions.deleteSessionsOf(emails);
        if (n > 0) log.info("Signed out {} session(s) after an access change", n);
        return n;
    }
}
