package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record PermissionBody(@Size(max = 80) String code, @NotBlank @Size(max = 120) String name, @Size(max = 500) String description,
                      @Size(max = 60) String category) {}
