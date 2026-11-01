package org.humsaadhak.registry.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

public record BankBody(@NotBlank String accountName, @NotBlank @Pattern(regexp = "^[0-9]{6,18}$") String accountNumber,
                @NotBlank @Pattern(regexp = "^[A-Za-z]{4}0[A-Za-z0-9]{6}$", message = "IFSC looks like SBIN0001234") String ifsc,
                String bankName, String branch) {}
