package org.humsaadhak.documents.api;

public final class ShareEvents {
    private ShareEvents() {}
    public record ShareRequested(long requestId, long needId, long donorOrgId, long ownerOrgId) {}
    public record ShareDecided(long requestId, long needId, long donorOrgId, boolean approved) {}
}
