package org.humsaadhak.registry.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

public record ProfileBody(@NotBlank @Size(max = 300) String name, @Size(max = 120) String city, @Size(max = 120) String state) {}
