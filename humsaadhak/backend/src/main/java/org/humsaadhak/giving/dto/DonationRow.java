package org.humsaadhak.giving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

public record DonationRow(long id, String refNo, long needId, Long matchId, BigDecimal paidAmount, BigDecimal pledgedAmount,
                   LocalDate paidOn, boolean confirmed) {}
