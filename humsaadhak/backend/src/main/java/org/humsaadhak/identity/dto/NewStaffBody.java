package org.humsaadhak.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new HumSaadhak staff member (Users screen, "Add staff member"). */
public record NewStaffBody(@NotBlank @Email @Size(max = 320) String email, @NotBlank @Size(max = 200) String fullName,
                           @Size(max = 20) String mobile, @NotBlank String roleCode) {}
