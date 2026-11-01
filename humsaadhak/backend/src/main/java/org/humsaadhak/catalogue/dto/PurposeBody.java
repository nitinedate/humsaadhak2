package org.humsaadhak.catalogue.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.humsaadhak.shared.model.Periodicity;
import org.springframework.web.bind.annotation.*;

public record PurposeBody(Long donorOrgId, @NotBlank @Size(max = 300) String name, @NotBlank @Size(min = 40, max = 8000) String description,
                   @NotBlank String sector, @PositiveOrZero BigDecimal commitmentAmount, @NotNull Periodicity periodicity) {}
