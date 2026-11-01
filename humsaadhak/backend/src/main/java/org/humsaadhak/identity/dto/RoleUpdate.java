package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

public record RoleUpdate(@Size(max = 120) String name, @Size(max = 500) String description, @NotNull Set<String> permissions) {}
