package org.humsaadhak.shared.util;

/**
 * Business reference numbers shown to people (BN100045, DN100046 …).
 * Same rule as the legacy system so existing numbers stay valid: prefix + (100000 + database id).
 */
public final class RefNo {
    public static final long OFFSET = 100_000L;

    public static final String BENEFACTOR = "BN";
    public static final String SCHOOL = "DB";
    public static final String NGO = "IB";
    public static final String PURPOSE = "PU";
    public static final String CAUSE = "CA";
    public static final String PROJECT = "PR";
    public static final String MATCH = "MA";
    public static final String MATCH_BATCH = "PMT";
    public static final String DONATION = "DN";
    public static final String DIRECT_DONATION = "DDN";

    private RefNo() {}

    public static String of(String prefix, long databaseId) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("prefix is required");
        if (databaseId <= 0) throw new IllegalArgumentException("databaseId must be positive");
        return prefix + (OFFSET + databaseId);
    }
}
