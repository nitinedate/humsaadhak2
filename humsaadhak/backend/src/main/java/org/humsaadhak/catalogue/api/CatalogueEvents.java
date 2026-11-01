package org.humsaadhak.catalogue.api;

/** Published when a purpose or need becomes (or stops being) searchable. Ids only. */
public final class CatalogueEvents {
    private CatalogueEvents() {}
    public record PurposeIndexable(long purposeId, boolean indexable) {}
    public record NeedIndexable(long needId, boolean indexable) {}
}
