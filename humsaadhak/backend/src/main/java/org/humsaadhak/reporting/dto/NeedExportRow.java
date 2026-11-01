package org.humsaadhak.reporting.dto;

import java.math.BigDecimal;

/** One cause or project in the staff CSV export. */
public record NeedExportRow(String refNo, String name, String kind, String beneficiary, String beneficiaryType, String sector,
                            BigDecimal target, BigDecimal raised, BigDecimal remaining, boolean approved, boolean completed) {}
