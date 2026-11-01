package org.humsaadhak.audit.model;

import java.util.Locale;
import java.util.Optional;

/** Records whose change history can be shown, and their Envers audit tables (an allow-list: user input never reaches SQL). */
public enum AuditedType {
    ORGANISATION("organisation_aud"), PURPOSE("purpose_aud"), NEED("need_aud"), DOCUMENT("document_aud"),
    DONATION("donation_aud"), USER("app_user_aud"), MATCH_CANDIDATE("match_candidate_aud");

    private final String table;
    AuditedType(String table) { this.table = table; }

    public String table() { return table; }

    public static Optional<AuditedType> parse(String value) {
        try { return Optional.of(valueOf(value.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }
}
