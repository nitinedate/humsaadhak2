package org.humsaadhak.catalogue.api;

/** Causes (schools) and projects (NGOs) are one concept in 2.0. */
public enum NeedKind {
    CAUSE("CA"), PROJECT("PR");
    private final String refPrefix;
    NeedKind(String refPrefix) { this.refPrefix = refPrefix; }
    public String refPrefix() { return refPrefix; }
}
