package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotNull;
import org.humsaadhak.identity.api.Theme;
import org.springframework.web.bind.annotation.*;

public record Preferences(@NotNull Theme theme) {}
