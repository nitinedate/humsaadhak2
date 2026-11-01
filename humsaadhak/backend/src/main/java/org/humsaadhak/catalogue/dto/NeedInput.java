package org.humsaadhak.catalogue.dto;

import java.math.BigDecimal;
import org.humsaadhak.catalogue.api.NeedKind;
import org.humsaadhak.shared.model.Periodicity;

public record NeedInput(Long orgId, NeedKind kind, String name, String description, String sector, BigDecimal targetAmount, Periodicity periodicity) {}
