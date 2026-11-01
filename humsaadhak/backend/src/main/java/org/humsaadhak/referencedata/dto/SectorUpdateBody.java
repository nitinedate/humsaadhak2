package org.humsaadhak.referencedata.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

public record SectorUpdateBody(@NotBlank @Size(max = 120) String name, boolean active) {}
