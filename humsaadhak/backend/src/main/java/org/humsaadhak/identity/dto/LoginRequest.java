package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

public record LoginRequest(@Email String email, @NotBlank String password) {}
