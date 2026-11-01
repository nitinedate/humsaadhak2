package org.humsaadhak.registry.dto;

import org.humsaadhak.registry.entity.OrgContact;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

public record ContactBody(@NotNull OrgContact.ContactRole role, @NotBlank String name, String designation,
                   @Email String email, @Pattern(regexp = "^[+0-9 ]{0,20}$") String phone) {}
