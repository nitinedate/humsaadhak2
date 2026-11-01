package org.humsaadhak.documents.api;

import java.util.Set;

/** Published when an administrator changes which documents an organisation type must provide; verification is re-checked. */
public record MandatoryRulesChanged(Set<String> organisationTypes) {}
