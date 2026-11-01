package org.humsaadhak.registry.api;

/** One table for all organisations replaces benefactor / db_beneficiary / indirect_beneficiary. */
public enum OrganisationType {
    DONOR_INDIVIDUAL("BN"), DONOR_CORPORATE("BN"), SCHOOL("DB"), NGO("IB");

    private final String refPrefix;
    OrganisationType(String refPrefix) { this.refPrefix = refPrefix; }
    public String refPrefix() { return refPrefix; }


    public boolean isDonor() { return this == DONOR_INDIVIDUAL || this == DONOR_CORPORATE; }
}
