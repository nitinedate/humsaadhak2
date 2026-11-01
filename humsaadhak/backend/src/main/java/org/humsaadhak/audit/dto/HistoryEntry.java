package org.humsaadhak.audit.dto;

import java.util.*;

/** One revision of a record; requestId links it to the audited action that made it (null for older or system changes). */
public record HistoryEntry(int revision, long timestamp, String user, String kind, List<HistoryChange> changes, String requestId) {}
