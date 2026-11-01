package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.humsaadhak.shared.security.Role;
import org.springframework.web.bind.annotation.*;

public record RegistrationRequest(@Email String email, @NotBlank String fullName, String mobile, Role role,
                           String organisationName, String city, String state, boolean consent) {}
