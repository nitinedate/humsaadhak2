package org.humsaadhak.reporting.dto;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;

public record OpenNeed(long id, String refNo, String name, String kind, String sector, long orgId, String orgName, String orgGroup,
                BigDecimal targetAmount, BigDecimal raisedAmount, BigDecimal remaining) {}
