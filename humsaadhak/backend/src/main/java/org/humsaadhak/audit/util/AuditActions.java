package org.humsaadhak.audit.util;

import java.util.Map;
import org.humsaadhak.audit.model.Outcome;

/** Readable names for action codes, their categories, and how an HTTP status maps to an outcome (pure, unit-tested). */
public final class AuditActions {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("auth.sign-in", "Signed in"), Map.entry("auth.sign-out", "Signed out"),
            Map.entry("auth.register", "Registered an account"), Map.entry("auth.reset-request", "Asked for a password reset link"),
            Map.entry("auth.set-password", "Set a password"), Map.entry("preferences.update", "Changed personal settings"),
            Map.entry("user.create", "Added the staff member"), Map.entry("user.role-change", "Changed the role"), Map.entry("user.disable", "Disabled the account"),
            Map.entry("user.enable", "Enabled the account"), Map.entry("user.invite", "Sent a new invitation"),
            Map.entry("role.create", "Created the role"), Map.entry("role.update", "Changed the role"), Map.entry("role.delete", "Deleted the role"),
            Map.entry("permission.create", "Created the permission"), Map.entry("permission.update", "Changed the permission"),
            Map.entry("permission.delete", "Deleted the permission"),
            Map.entry("organisation.profile-update", "Updated the profile"), Map.entry("organisation.contacts-update", "Updated the contacts"),
            Map.entry("organisation.bank-add", "Added a bank account"),
            Map.entry("purpose.create", "Created the purpose"), Map.entry("purpose.update", "Edited the purpose"),
            Map.entry("purpose.submit", "Submitted the purpose for approval"),
            Map.entry("need.create", "Created the cause or project"), Map.entry("need.update", "Edited the cause or project"),
            Map.entry("need.submit", "Submitted for approval"),
            Map.entry("approval.approve", "Approved"), Map.entry("approval.reject", "Rejected"),
            Map.entry("document.upload", "Uploaded a document"), Map.entry("document.download", "Opened a download link"),
            Map.entry("document.review", "Accepted the document"), Map.entry("document.reject", "Rejected the document"),
            Map.entry("share.request", "Asked to see documents"), Map.entry("share.approve", "Agreed to share documents"),
            Map.entry("share.reject", "Declined to share documents"),
            Map.entry("match.run", "Ran the matching engine"), Map.entry("match.confirm", "Confirmed matches"),
            Map.entry("donation.record", "Recorded a donation"),
            Map.entry("import.upload", "Imported a workbook"), Map.entry("import.template", "Downloaded an import template"),
            Map.entry("sector.create", "Created the sector"), Map.entry("sector.update", "Changed the sector"),
            Map.entry("document-type.create", "Created the document type"), Map.entry("document-type.update", "Changed the document type"),
            Map.entry("report.run", "Ran a report"), Map.entry("report.export-csv", "Exported data to CSV"),
            Map.entry("audit.export", "Exported the audit log"),
            Map.entry("record.create", "Created the record"), Map.entry("record.change", "Changed the record"));

    private AuditActions() {}

    public static boolean known(String code) { return LABELS.containsKey(code); }

    /** "purpose.update" -> "Edited the purpose"; an unknown code is shown as it is. */
    public static String label(String code) { return LABELS.getOrDefault(code, code); }

    /** "purpose.update" -> "purpose": the group a page shows. */
    public static String category(String code) {
        int dot = code.indexOf('.');
        return dot < 0 ? code : code.substring(0, dot);
    }

    /** 401/403 are refusals; any other error status is a failure. */
    public static Outcome outcomeForStatus(int status) {
        if (status == 401 || status == 403) return Outcome.DENIED;
        return status >= 400 ? Outcome.FAILED : Outcome.SUCCESS;
    }

    /** Trims text to fit a column, marking the cut. */
    public static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
