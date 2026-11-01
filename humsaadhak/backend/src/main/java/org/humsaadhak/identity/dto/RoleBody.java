package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record RoleBody(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description, String kind, String copyFrom) {}
