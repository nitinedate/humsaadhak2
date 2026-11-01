package org.humsaadhak.giving.dto;

import java.math.BigDecimal;
import org.humsaadhak.shared.model.Periodicity;

public record DonationCommand(Long donorOrgId, long needId, Long matchId, BigDecimal paidAmount, Periodicity periodicity, String paymentRef) {}
