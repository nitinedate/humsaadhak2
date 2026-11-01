package org.humsaadhak.audit.service;

import java.util.List;
import java.util.Map;
import org.humsaadhak.audit.dao.HistoryDao;
import org.humsaadhak.audit.dto.HistoryEntry;
import org.humsaadhak.audit.model.AuditedType;
import org.humsaadhak.audit.util.HistoryDiff;
import org.humsaadhak.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Readable change history of a record, newest first. */
@Service
@Transactional(readOnly = true)
public class HistoryService {
    /** Never shown in history: secrets and noisy counters. */
    private static final List<String> HIDDEN = List.of("password_hash", "totp_secret", "failed_logins", "locked_until");

    private final HistoryDao history;

    public HistoryService(HistoryDao history) { this.history = history; }

    public List<HistoryEntry> history(String type, long id) {
        AuditedType t = AuditedType.parse(type).orElseThrow(() -> new NotFoundException("History type", type));
        List<Map<String, Object>> rows = history.revisions(t, id);
        rows.forEach(r -> HIDDEN.forEach(r::remove));
        return HistoryDiff.entries(rows);
    }
}
