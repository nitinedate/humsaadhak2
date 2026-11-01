package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

public record SetPasswordBody(@NotBlank String token, @NotBlank String password) {}
