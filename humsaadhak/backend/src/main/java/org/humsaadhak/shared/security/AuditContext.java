package org.humsaadhak.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.humsaadhak.shared.model.AuditTarget;

/**
 * What the audit trail knows about the current request: its id, where it came from, and anything a service adds while
 * handling it (the id of a record it just created, the subject of an approval, a short note). One per request thread.
 */
public final class AuditContext {
    private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

    private final String requestId;
    private final String ip;
    private final String userAgent;
    private AuditTarget target;
    private String targetId;
    private final List<String> notes = new ArrayList<>();
    private boolean recorded;

    private AuditContext(String requestId, String ip, String userAgent) {
        this.requestId = requestId; this.ip = ip; this.userAgent = userAgent;
    }

    public static AuditContext begin(String requestId, String ip, String userAgent) {
        AuditContext c = new AuditContext(requestId, ip, userAgent);
        CURRENT.set(c);
        return c;
    }

    public static Optional<AuditContext> current() { return Optional.ofNullable(CURRENT.get()); }

    public static void end() { CURRENT.remove(); }

    /** For services: the record this request acted on, when only the service knows it (a new record, an approval's subject). */
    public static void target(AuditTarget type, Object id) {
        current().ifPresent(c -> { c.target = type; c.targetId = id == null ? null : String.valueOf(id); });
    }

    /** For services: a short, non-secret note added to this request's audit record. */
    public static void note(String text) {
        if (text != null && !text.isBlank()) current().ifPresent(c -> c.notes.add(text.strip()));
    }

    /** The id shared by this request's audit record and the change history it wrote, or null outside a request. */
    public static String requestIdOrNull() { return current().map(AuditContext::requestId).orElse(null); }

    public String requestId() { return requestId; }
    public String ip() { return ip; }
    public String userAgent() { return userAgent; }
    public AuditTarget target() { return target; }
    public String targetId() { return targetId; }
    public List<String> notes() { return List.copyOf(notes); }
    public boolean recorded() { return recorded; }
    public void markRecorded() { this.recorded = true; }
}
