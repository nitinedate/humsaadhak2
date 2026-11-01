package org.humsaadhak.giving.api;

import java.math.BigDecimal;

public record DonationRecorded(long donationId, String refNo, long needId, Long purposeId, long donorOrgId, BigDecimal paidAmount) {}
