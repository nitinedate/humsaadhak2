package org.humsaadhak.giving.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.humsaadhak.shared.model.Periodicity;
import org.springframework.web.bind.annotation.*;

public record DonationBody(Long donorOrgId, @NotNull Long needId, Long matchId, @NotNull @Positive BigDecimal paidAmount,
                    @NotNull Periodicity periodicity, @NotBlank String paymentRef) {}
