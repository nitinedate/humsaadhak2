package org.humsaadhak.catalogue.dto;

import java.math.BigDecimal;
import org.humsaadhak.shared.model.Periodicity;

public record PurposeInput(Long donorOrgId, String name, String description, String sector, BigDecimal commitmentAmount, Periodicity periodicity) {}
