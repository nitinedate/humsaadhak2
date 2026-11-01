package org.humsaadhak.giving.api;

import java.math.BigDecimal;
import java.time.LocalDate;

public record InstalmentDue(long instalmentId, long donorOrgId, long needId, BigDecimal amount, LocalDate dueDate) {}
