package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

public record RoleChange(@NotNull String roleCode) {}
