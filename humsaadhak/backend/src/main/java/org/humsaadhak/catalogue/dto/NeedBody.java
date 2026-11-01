package org.humsaadhak.catalogue.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.humsaadhak.catalogue.api.NeedKind;
import org.humsaadhak.shared.model.Periodicity;
import org.springframework.web.bind.annotation.*;

public record NeedBody(Long orgId, @NotNull NeedKind kind, @NotBlank @Size(max = 300) String name,
                @NotBlank @Size(min = 40, max = 8000) String description, @NotBlank String sector,
                @NotNull @Positive BigDecimal targetAmount, @NotNull Periodicity periodicity) {}
