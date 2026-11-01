package org.humsaadhak.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One donation in the staff CSV export. */
public record DonationExportRow(String refNo, LocalDate paidOn, String donor, String donorType, String need, String beneficiary,
                                String beneficiaryType, BigDecimal paid, BigDecimal pledged, String paymentRef, boolean matched) {}
