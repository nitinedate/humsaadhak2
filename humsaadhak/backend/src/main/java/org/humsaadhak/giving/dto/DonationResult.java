package org.humsaadhak.giving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DonationResult(long donationId, String refNo, BigDecimal paid, BigDecimal commitmentLeft, LocalDate nextDue) {}
