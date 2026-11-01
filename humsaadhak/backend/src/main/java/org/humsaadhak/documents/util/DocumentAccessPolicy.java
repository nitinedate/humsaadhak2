package org.humsaadhak.documents.util;

/**
 * Who may open a document (fixes S2). Pure, unit-tested.
 *  - staff: always
 *  - the owning organisation: always
 *  - a donor: only shareable documents, only with an APPROVED share request from the owner
 *  - anyone else: never
 */
public final class DocumentAccessPolicy {
    private DocumentAccessPolicy() {}

    public static boolean canView(boolean viewerIsStaff, Long viewerOrgId, long ownerOrgId,
                                  boolean shareableType, boolean approvedShareExists) {
        if (viewerIsStaff) return true;
        if (viewerOrgId == null) return false;
        if (viewerOrgId == ownerOrgId) return true;
        return shareableType && approvedShareExists;
    }
}
